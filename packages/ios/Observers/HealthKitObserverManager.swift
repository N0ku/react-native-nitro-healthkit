import Foundation
import HealthKit

/// Manages `HKObserverQuery` subscriptions and surfaces change events to JS — the iOS
/// counterpart of Android's `ChangesObserver`.
///
/// HealthKit *pushes* change notifications (unlike Health Connect, which Android polls).
/// On each notification we run a short `HKAnchoredObjectQuery` to advance a per-subscription
/// anchor and forward that anchor — serialized as a base64 "changes token" — to the JS
/// callback, mirroring the opaque token Android forwards. The JS side decides when to
/// re-fetch the actual data.
///
/// `enableBackgroundDelivery` is requested so observers also fire when the app is backgrounded
/// (this requires the HealthKit background-delivery capability in the host app — see README).
final class HealthKitObserverManager: @unchecked Sendable {
  private let healthStore: HKHealthStore
  private let lock = NSLock()
  private var queries: [String: HKObserverQuery] = [:]
  private var sampleTypes: [String: HKSampleType] = [:]
  private var anchors: [String: HKQueryAnchor] = [:]

  init(healthStore: HKHealthStore) {
    self.healthStore = healthStore
  }

  func observeQuantity(type: String, callback: @escaping (String) -> Void) throws -> String {
    guard let identifier = HKQuantityTypeIdentifier.from(type),
          let sampleType = HKObjectType.quantityType(forIdentifier: identifier) else {
      throw NSError(domain: "HealthKit", code: 2, userInfo: [NSLocalizedDescriptionKey: "Invalid quantity type: \(type)"])
    }
    return startObserving(sampleType: sampleType, callback: callback)
  }

  func observeCategory(type: String, callback: @escaping (String) -> Void) throws -> String {
    guard let identifier = HKCategoryTypeIdentifier.from(type),
          let sampleType = HKObjectType.categoryType(forIdentifier: identifier) else {
      throw NSError(domain: "HealthKit", code: 2, userInfo: [NSLocalizedDescriptionKey: "Invalid category type: \(type)"])
    }
    return startObserving(sampleType: sampleType, callback: callback)
  }

  func removeObserver(id: String) {
    lock.lock()
    let query = queries.removeValue(forKey: id)
    let type = sampleTypes.removeValue(forKey: id)
    anchors.removeValue(forKey: id)
    let stillUsed = type.map { t in sampleTypes.values.contains { $0.isEqual(t) } } ?? false
    lock.unlock()

    if let query = query {
      healthStore.stop(query)
    }
    // Only tear down background delivery for the type once no subscription needs it.
    if let type = type, !stillUsed {
      healthStore.disableBackgroundDelivery(for: type) { _, error in
        if let error = error {
          NSLog("⚠️ [HealthKit] disableBackgroundDelivery failed: %@", error.localizedDescription)
        }
      }
    }
  }

  func removeAll() {
    lock.lock()
    let active = queries
    queries.removeAll()
    sampleTypes.removeAll()
    anchors.removeAll()
    lock.unlock()

    active.values.forEach { healthStore.stop($0) }
    healthStore.disableAllBackgroundDelivery { _, error in
      if let error = error {
        NSLog("⚠️ [HealthKit] disableAllBackgroundDelivery failed: %@", error.localizedDescription)
      }
    }
  }

  // MARK: - Internals

  private func startObserving(sampleType: HKSampleType, callback: @escaping (String) -> Void) -> String {
    let id = UUID().uuidString

    let query = HKObserverQuery(sampleType: sampleType, predicate: nil) { [weak self] _, completionHandler, error in
      guard let self = self else {
        completionHandler()
        return
      }
      if let error = error {
        NSLog("⚠️ [HealthKit] observer error for %@: %@", sampleType.identifier, error.localizedDescription)
        completionHandler()
        return
      }
      self.emitToken(id: id, sampleType: sampleType, callback: callback, completion: completionHandler)
    }

    lock.lock()
    queries[id] = query
    sampleTypes[id] = sampleType
    lock.unlock()

    healthStore.execute(query)
    healthStore.enableBackgroundDelivery(for: sampleType, frequency: .immediate) { success, error in
      if let error = error {
        NSLog("⚠️ [HealthKit] enableBackgroundDelivery for %@ failed: %@", sampleType.identifier, error.localizedDescription)
      } else {
        NSLog("✅ [HealthKit] backgroundDelivery enabled for %@: %@", sampleType.identifier, success ? "yes" : "no")
      }
    }
    return id
  }

  /// Runs an anchored query to advance the subscription's anchor, then forwards the
  /// serialized anchor as the change token. Always calls `completion` so HealthKit
  /// knows the notification was handled.
  private func emitToken(
    id: String,
    sampleType: HKSampleType,
    callback: @escaping (String) -> Void,
    completion: @escaping () -> Void
  ) {
    lock.lock()
    let previousAnchor = anchors[id]
    lock.unlock()

    let anchoredQuery = HKAnchoredObjectQuery(
      type: sampleType,
      predicate: nil,
      anchor: previousAnchor,
      limit: HKObjectQueryNoLimit
    ) { [weak self] _, _, _, newAnchor, _ in
      defer { completion() }
      guard let self = self else { return }
      if let newAnchor = newAnchor {
        self.lock.lock()
        self.anchors[id] = newAnchor
        self.lock.unlock()
        callback(Self.encode(anchor: newAnchor))
      } else {
        callback("")
      }
    }
    healthStore.execute(anchoredQuery)
  }

  private static func encode(anchor: HKQueryAnchor) -> String {
    guard let data = try? NSKeyedArchiver.archivedData(withRootObject: anchor, requiringSecureCoding: true) else {
      return ""
    }
    return data.base64EncodedString()
  }
}
