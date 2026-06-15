import Foundation
import HealthKit
#if canImport(BackgroundTasks)
import BackgroundTasks
#endif

/// Periodic background sync of HealthKit deltas to a configured backend — the iOS
/// counterpart of Android's `HealthSyncWorker` (which uses WorkManager).
///
/// iOS requires the `BGTaskScheduler` launch handler to be registered **before**
/// `application(_:didFinishLaunchingWithOptions:)` returns, so the host app must call
/// `HealthKitBackgroundSync.registerLaunchHandler()` at launch and declare the task
/// identifier in `Info.plist` under `BGTaskSchedulerPermittedIdentifiers`
/// (+ enable the "Background processing"/"Background fetch" capabilities). See the README.
///
/// Credentials (JWT, backend URL) are loaded from the Keychain at execution time and never
/// embedded in the scheduled request, mirroring Android's `SecureCredentialsStore`.
@objc public final class HealthKitBackgroundSync: NSObject, @unchecked Sendable {
  @objc public static let shared = HealthKitBackgroundSync()

  /// The `BGTaskScheduler` identifier. Must also be listed in the host app's
  /// `Info.plist` → `BGTaskSchedulerPermittedIdentifiers`.
  @objc public static let taskIdentifier = "com.nitrohealthkit.sync"

  private let store = KeychainCredentialsStore(service: "react-native-nitro-healthkit")
  private let healthStore = HKHealthStore()
  private let anchorDefaults = UserDefaults.standard

  /// Register the background-task launch handler. Call this from the host app's
  /// `AppDelegate` (or an Expo config plugin) before `didFinishLaunching` returns.
  /// Returns `false` when registration is unavailable (e.g. unsupported OS).
  @discardableResult
  @objc public static func registerLaunchHandler() -> Bool {
    #if canImport(BackgroundTasks)
    if #available(iOS 13.0, *) {
      return BGTaskScheduler.shared.register(forTaskWithIdentifier: taskIdentifier, using: nil) { task in
        guard let refreshTask = task as? BGAppRefreshTask else {
          task.setTaskCompleted(success: false)
          return
        }
        shared.handle(task: refreshTask)
      }
    }
    #endif
    return false
  }

  func register(apiBaseUrl: String, jwtToken: String, intervalMinutes: Double, types: [String], syncPath: String) {
    store.save(
      apiBaseUrl: apiBaseUrl,
      jwtToken: jwtToken,
      syncPath: syncPath,
      types: types,
      intervalMinutes: Int(intervalMinutes)
    )
    schedule(intervalMinutes: intervalMinutes)
  }

  func unregister() {
    #if canImport(BackgroundTasks)
    if #available(iOS 13.0, *) {
      BGTaskScheduler.shared.cancel(taskRequestWithIdentifier: Self.taskIdentifier)
    }
    #endif
    store.clear()
  }

  func isRegistered() -> Bool {
    guard let jwt = store.jwtToken() else { return false }
    return !jwt.isEmpty
  }

  // MARK: - Scheduling

  private func schedule(intervalMinutes: Double) {
    #if canImport(BackgroundTasks)
    if #available(iOS 13.0, *) {
      let request = BGAppRefreshTaskRequest(identifier: Self.taskIdentifier)
      // iOS treats this as a lower bound and applies its own heuristics. We clamp to a
      // sane floor that matches Android's WorkManager minimum.
      let minutes = max(15.0, intervalMinutes)
      request.earliestBeginDate = Date(timeIntervalSinceNow: minutes * 60)
      do {
        try BGTaskScheduler.shared.submit(request)
        NSLog("✅ [HealthKit] background sync scheduled (~%.0f min)", minutes)
      } catch {
        NSLog("⚠️ [HealthKit] BGTaskScheduler submit failed: %@ — did you list \"%@\" in BGTaskSchedulerPermittedIdentifiers and call registerLaunchHandler()?", error.localizedDescription, Self.taskIdentifier)
      }
    } else {
      NSLog("⚠️ [HealthKit] background sync requires iOS 13+")
    }
    #endif
  }

  #if canImport(BackgroundTasks)
  @available(iOS 13.0, *)
  private func handle(task: BGAppRefreshTask) {
    // Always queue the next run first so a single failure doesn't end the chain.
    schedule(intervalMinutes: Double(store.intervalMinutes()))

    let work = Task { await self.performSync() }
    task.expirationHandler = { work.cancel() }
    Task {
      let success = await work.value
      task.setTaskCompleted(success: success)
    }
  }
  #endif

  // MARK: - Sync work (exposed for manual triggering / testing)

  /// Reads the delta for each configured type and POSTs it to the backend.
  /// Returns `true` when the run completed (including "nothing to sync"); `false` on a
  /// transient failure so the OS may retry.
  @discardableResult
  func performSync() async -> Bool {
    guard let jwt = store.jwtToken(), let baseUrl = store.apiBaseUrl(),
          !jwt.isEmpty, !baseUrl.isEmpty else {
      NSLog("⚠️ [HealthKit] background sync: missing credentials — skipping")
      return true
    }

    let types = store.types().isEmpty ? Self.defaultTypes : store.types()
    var entries: [[String: Any]] = []
    for type in types {
      guard let samples = await fetchDelta(type: type), !samples.isEmpty else { continue }
      entries.append(["type": type, "samples": samples])
    }

    if entries.isEmpty {
      return true
    }
    return await post(baseUrl: baseUrl, jwt: jwt, entries: entries)
  }

  private func fetchDelta(type: String) async -> [[String: Any]]? {
    let sampleType: HKSampleType
    let unit: HKUnit?
    if let identifier = HKQuantityTypeIdentifier.from(type),
       let qType = HKObjectType.quantityType(forIdentifier: identifier) {
      sampleType = qType
      unit = identifier.defaultUnit()
    } else if let identifier = HKCategoryTypeIdentifier.from(type),
              let cType = HKObjectType.categoryType(forIdentifier: identifier) {
      sampleType = cType
      unit = nil
    } else {
      return nil
    }

    let previousAnchor = loadAnchor(for: type)
    return await withCheckedContinuation { (continuation: CheckedContinuation<[[String: Any]]?, Never>) in
      let query = HKAnchoredObjectQuery(
        type: sampleType,
        predicate: nil,
        anchor: previousAnchor,
        limit: HKObjectQueryNoLimit
      ) { [weak self] _, samples, _, newAnchor, error in
        guard let self = self else {
          continuation.resume(returning: nil)
          return
        }
        if let error = error {
          NSLog("⚠️ [HealthKit] background fetchDelta %@ failed: %@", type, error.localizedDescription)
          continuation.resume(returning: nil)
          return
        }
        if let newAnchor = newAnchor {
          self.saveAnchor(newAnchor, for: type)
        }
        let formatter = ISO8601DateFormatter()
        let mapped: [[String: Any]] = (samples ?? []).compactMap { sample in
          if let quantitySample = sample as? HKQuantitySample, let unit = unit {
            var entry: [String: Any] = [
              "value": quantitySample.quantity.doubleValue(for: unit),
              "unit": unit.unitString,
              "startDate": formatter.string(from: quantitySample.startDate),
              "endDate": formatter.string(from: quantitySample.endDate),
            ]
            if let metadata = quantitySample.metadata?.compactMapValues({ "\($0)" }), !metadata.isEmpty {
              entry["metadata"] = metadata
            }
            return entry
          } else if let categorySample = sample as? HKCategorySample {
            var entry: [String: Any] = [
              "value": Double(categorySample.value),
              "startDate": formatter.string(from: categorySample.startDate),
              "endDate": formatter.string(from: categorySample.endDate),
            ]
            if let metadata = categorySample.metadata?.compactMapValues({ "\($0)" }), !metadata.isEmpty {
              entry["metadata"] = metadata
            }
            return entry
          }
          return nil
        }
        continuation.resume(returning: mapped)
      }
      healthStore.execute(query)
    }
  }

  private func post(baseUrl: String, jwt: String, entries: [[String: Any]]) async -> Bool {
    let body: [String: Any] = [
      "source": "ios-healthkit",
      "syncedAt": ISO8601DateFormatter().string(from: Date()),
      "entries": entries,
    ]
    guard let payload = try? JSONSerialization.data(withJSONObject: body) else { return false }

    var urlString = baseUrl
    while urlString.hasSuffix("/") { urlString.removeLast() }
    urlString += store.syncPath()
    guard let url = URL(string: urlString) else {
      NSLog("⚠️ [HealthKit] background sync: invalid URL %@", urlString)
      return true
    }

    var request = URLRequest(url: url)
    request.httpMethod = "POST"
    request.setValue("Bearer \(jwt)", forHTTPHeaderField: "Authorization")
    request.setValue("application/json", forHTTPHeaderField: "Content-Type")
    request.httpBody = payload

    do {
      let (_, response) = try await URLSession.shared.data(for: request)
      guard let http = response as? HTTPURLResponse else { return false }
      switch http.statusCode {
      case 200...299:
        return true
      case 401, 403:
        // Token rejected: clear credentials so we stop retrying until the app re-registers.
        NSLog("⚠️ [HealthKit] background sync: auth rejected (%d) — clearing credentials", http.statusCode)
        store.clear()
        return true
      case 500...599:
        NSLog("⚠️ [HealthKit] background sync: backend returned %d — will retry", http.statusCode)
        return false
      default:
        NSLog("⚠️ [HealthKit] background sync: backend returned %d — giving up this run", http.statusCode)
        return true
      }
    } catch {
      NSLog("⚠️ [HealthKit] background sync POST failed: %@", error.localizedDescription)
      return false
    }
  }

  // MARK: - Anchor persistence

  private func loadAnchor(for type: String) -> HKQueryAnchor? {
    guard let data = anchorDefaults.data(forKey: Self.anchorKey(type)) else { return nil }
    return try? NSKeyedUnarchiver.unarchivedObject(ofClass: HKQueryAnchor.self, from: data)
  }

  private func saveAnchor(_ anchor: HKQueryAnchor, for type: String) {
    guard let data = try? NSKeyedArchiver.archivedData(withRootObject: anchor, requiringSecureCoding: true) else { return }
    anchorDefaults.set(data, forKey: Self.anchorKey(type))
  }

  private static func anchorKey(_ type: String) -> String { "nitrohealthkit.anchor.\(type)" }

  // Mirrors Android's HealthSyncWorker.DEFAULT_TYPES.
  private static let defaultTypes = [
    "HKQuantityTypeIdentifierStepCount",
    "HKQuantityTypeIdentifierHeartRate",
    "HKQuantityTypeIdentifierActiveEnergyBurned",
    "HKQuantityTypeIdentifierDistanceWalkingRunning",
    "HKQuantityTypeIdentifierFlightsClimbed",
    "HKCategoryTypeIdentifierSleepAnalysis",
  ]
}
