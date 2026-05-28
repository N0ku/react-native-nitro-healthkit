import NitroModules
import HealthKit
import os.log

public class HealthKitModule: HybridHealthKitSpec, @unchecked Sendable {
  private let healthStore = HKHealthStore()
  private let cacheManager = CacheManager.shared
  
  // Default cache configuration
  private let defaultCacheTTL: TimeInterval = 60 // 1 minute
  
  // Data types to authorize (extended)
  private let readTypes: Set<HKObjectType> = {
    var types: Set<HKObjectType> = []
    
    // Workout type
    types.insert(HKWorkoutType.workoutType())
    
    // Quantity types
    let quantityIdentifiers: [HKQuantityTypeIdentifier] = [
      .stepCount, .heartRate, .activeEnergyBurned, .basalEnergyBurned,
      .distanceWalkingRunning, .distanceCycling, .distanceSwimming,
      .flightsClimbed, .restingHeartRate, .heartRateVariabilitySDNN,
      .oxygenSaturation, .bodyTemperature, .bloodPressureSystolic,
      .bloodPressureDiastolic, .respiratoryRate, .bodyMass, .height,
      .bodyMassIndex, .bodyFatPercentage, .leanBodyMass, .vo2Max,
      .walkingSpeed, .walkingStepLength
    ]
    
    for identifier in quantityIdentifiers {
      if let type = HKObjectType.quantityType(forIdentifier: identifier) {
        types.insert(type)
      }
    }
    
    // Category types
    let categoryIdentifiers: [HKCategoryTypeIdentifier] = [
      .sleepAnalysis, .appleStandHour, .mindfulSession,
      .menstrualFlow, .sexualActivity, .lowHeartRateEvent,
      .highHeartRateEvent, .irregularHeartRhythmEvent
    ]
    
    for identifier in categoryIdentifiers {
      if let type = HKObjectType.categoryType(forIdentifier: identifier) {
        types.insert(type)
      }
    }
    
    return types
  }()
  
  public override init() {
    super.init()
  }
  
   public func requestAuthorization() throws -> Promise<Bool> {
    return Promise.async { [weak self] in
      guard let self = self else { return false }

      NSLog("🔍 [HealthKit] requestAuthorization - Start")
      os_log("🔍 [HealthKit] requestAuthorization - Start", log: .default, type: .info)
      
      
      guard HKHealthStore.isHealthDataAvailable() else {
        throw NSError(domain: "HealthKit", code: 1, userInfo: [NSLocalizedDescriptionKey: "HealthKit not available"])
      }
      
      return try await withCheckedThrowingContinuation { continuation in
        self.healthStore.requestAuthorization(toShare: [], read: self.readTypes) { success, error in
          if let error = error {
            continuation.resume(throwing: error)
          } else {
            continuation.resume(returning: success)
          }
        }
      }
    }
  }
  
  public func getSteps(startDate: Date, endDate: Date) throws -> Promise<Double> {
    return Promise.async { [weak self] in
      guard let self = self else { return 0 }
      
      // Log dates
      let formatter = DateFormatter()
      formatter.dateFormat = "yyyy-MM-dd HH:mm:ss"
      NSLog("🔍 [HealthKit] getSteps - Start: %@, End: %@", formatter.string(from: startDate), formatter.string(from: endDate))
      os_log("🔍 [HealthKit] getSteps - Start: %{public}@, End: %{public}@", log: .default, type: .info, formatter.string(from: startDate), formatter.string(from: endDate))
      
      let stepType: HKQuantityType = HKQuantityType.quantityType(forIdentifier: .stepCount)!
      // Use .strictEndDate to include all samples in the period
      let predicate: NSPredicate = HKQuery.predicateForSamples(withStart: startDate, end: endDate, options: [])
      
      return try await withCheckedThrowingContinuation { continuation in
        let query = HKStatisticsQuery(quantityType: stepType, quantitySamplePredicate: predicate, options: .cumulativeSum) { _, result, error in
          if let error = error {
            continuation.resume(throwing: error)
            return
          }
          
          let steps = result?.sumQuantity()?.doubleValue(for: HKUnit.count()) ?? 0
          NSLog("✅ [HealthKit] getSteps - Result: %.0f pas", steps)
          os_log("✅ [HealthKit] getSteps - Result: %.0f pas", log: .default, type: .info, steps)
          continuation.resume(returning: steps)
        }
        
        self.healthStore.execute(query)
      }
    }
  }
  
  public func getHeartRate(startDate: Date, endDate: Date) throws -> Promise<Double> {
    return Promise.async { [weak self] in
      guard let self = self else { return 0 }
      
      // Log dates
      let formatter: DateFormatter = DateFormatter()
      formatter.dateFormat = "yyyy-MM-dd HH:mm:ss"
      NSLog("🔍 [HealthKit] getHeartRate - Start: %@, End: %@", formatter.string(from: startDate), formatter.string(from: endDate))
      os_log("🔍 [HealthKit] getHeartRate - Start: %{public}@, End: %{public}@", log: .default, type: .info, formatter.string(from: startDate), formatter.string(from: endDate))
      
      let heartRateType: HKQuantityType = HKQuantityType.quantityType(forIdentifier: .heartRate)!
      // Use [] to include all samples in the period
      let predicate: NSPredicate = HKQuery.predicateForSamples(withStart: startDate, end: endDate, options: [])
      
      return try await withCheckedThrowingContinuation { continuation in
        let query = HKStatisticsQuery(quantityType: heartRateType, quantitySamplePredicate: predicate, options: .discreteAverage) { _, result, error in
          if let error = error {
            NSLog("❌ [HealthKit] getHeartRate - Error: %@", error.localizedDescription)
            continuation.resume(throwing: error)
            return
          }
          
          NSLog("🔍 [HealthKit] getHeartRate - Query completed, result: %@", result?.description ?? "nil")
          
          let bpm = result?.averageQuantity()?.doubleValue(for: HKUnit.count().unitDivided(by: .minute())) ?? 0
          NSLog("✅ [HealthKit] getHeartRate - Result: %.1f bpm", bpm)
          os_log("✅ [HealthKit] getHeartRate - Result: %.1f bpm", log: .default, type: .info, bpm)
          continuation.resume(returning: bpm)
        }
        
        self.healthStore.execute(query)
      }
    }
  }
  
  public func getHealthData(startDate: Date, endDate: Date) throws -> Promise<HealthData> {
    return Promise.async { [weak self] in
      guard let self = self else {
        return HealthData(steps: nil, heartRate: nil, activeEnergy: nil, distance: nil, sleepAnalysis: nil)
      }
      
      // Log dates
      let formatter = DateFormatter()
      formatter.dateFormat = "yyyy-MM-dd HH:mm:ss"
      NSLog("🔍 [HealthKit] getHealthData - Start: %@, End: %@", formatter.string(from: startDate), formatter.string(from: endDate))
      os_log("🔍 [HealthKit] getHealthData - Start: %{public}@, End: %{public}@", log: .default, type: .info, formatter.string(from: startDate), formatter.string(from: endDate))
      
      // Fetch data in parallel with individual error handling
      var steps: Double? = nil
      var heartRate: Double? = nil
      
      // Fetch steps (with error handling)
      do {
        let stepsValue = try await self.getSteps(startDate: startDate, endDate: endDate).await()
        steps = stepsValue > 0 ? stepsValue : nil
      } catch {
        NSLog("⚠️ [HealthKit] getHealthData - Error getting steps: %@", error.localizedDescription)
      }
      
      // Fetch heart rate (with error handling)
      do {
        let heartRateValue = try await self.getHeartRate(startDate: startDate, endDate: endDate).await()
        heartRate = heartRateValue > 0 ? heartRateValue : nil
      } catch {
        NSLog("⚠️ [HealthKit] getHealthData - Error getting heart rate: %@", error.localizedDescription)
      }
      
      NSLog("✅ [HealthKit] getHealthData - Steps: %@, HeartRate: %@", 
            steps != nil ? String(format: "%.0f", steps!) : "nil",
            heartRate != nil ? String(format: "%.1f", heartRate!) : "nil")
      
      return HealthData(
        steps: steps,
        heartRate: heartRate,
        activeEnergy: nil,
        distance: nil,
        sleepAnalysis: nil
      )
    }
  }
  
  // MARK: - Generic Quantity Data Retrieval
  
  /// Fetch generic quantity data with cache
  public func getQuantityData(
    type: String,
    startDate: Date,
    endDate: Date,
    aggregationType: String?,
    useCache: Bool?,
    cacheTTL: Double?
  ) throws -> Promise<[QuantityDataPoint]> {
    return Promise.async { [weak self] in
      guard let self = self else { 
        NSLog("❌ [HealthKit] getQuantityData - self is nil")
        return [] 
      }
      
      let useCacheValue = useCache ?? true
      let cacheTTLValue = cacheTTL ?? self.defaultCacheTTL
      
      NSLog("🔍 [HealthKit] getQuantityData - Type: %@, Start: %@, End: %@, Cache: %@", type, startDate as CVarArg, endDate as CVarArg, useCacheValue ? "enabled" : "disabled")
      
      // Check cache
      let cacheKey = CacheManager.generateKey(type: type, startDate: startDate, endDate: endDate, options: aggregationType ?? "")
      if useCacheValue, let cached: [QuantityDataPoint] = self.cacheManager.get(cacheKey) {
        NSLog("✅ [HealthKit] Cache hit for %@", type)
        return cached
      }
      
      // Convert string type to HKQuantityTypeIdentifier
      NSLog("🔍 [HealthKit] Converting type identifier: %@", type)
      guard let identifier = HKQuantityTypeIdentifier.from(type) else {
        NSLog("❌ [HealthKit] Failed to convert type identifier: %@", type)
        throw NSError(domain: "HealthKit", code: 2, userInfo: [NSLocalizedDescriptionKey: "Invalid quantity type identifier: \(type)"])
      }
      
      guard let quantityType = HKObjectType.quantityType(forIdentifier: identifier) else {
        NSLog("❌ [HealthKit] Failed to create quantity type for: %@", type)
        throw NSError(domain: "HealthKit", code: 2, userInfo: [NSLocalizedDescriptionKey: "Invalid quantity type: \(type)"])
      }
      
      NSLog("✅ [HealthKit] Created quantity type successfully")
      
      let predicate = HKQuery.predicateForSamples(withStart: startDate, end: endDate, options: [])
      let unit = identifier.defaultUnit()
      let sortDescriptor = NSSortDescriptor(key: HKSampleSortIdentifierStartDate, ascending: false)
      
      NSLog("🔍 [HealthKit] Executing query...")
      
      return try await withCheckedThrowingContinuation { continuation in
        let query = HKSampleQuery(
          sampleType: quantityType,
          predicate: predicate,
          limit: HKObjectQueryNoLimit,
          sortDescriptors: [sortDescriptor]
        ) { _, samples, error in
          if let error = error {
            NSLog("❌ [HealthKit] Query error: %@", error.localizedDescription)
            continuation.resume(throwing: error)
            return
          }
          
          guard let samples = samples as? [HKQuantitySample] else {
            NSLog("⚠️ [HealthKit] No samples found or wrong type")
            continuation.resume(returning: [])
            return
          }
          
          NSLog("📊 [HealthKit] Found %d samples", samples.count)
          
          let dataPoints = samples.map { sample -> QuantityDataPoint in
            let value = sample.quantity.doubleValue(for: unit)
            // Convert metadata from [String: Any] to [String: String]
            let metadataDict = sample.metadata?.compactMapValues { "\($0)" }
            return QuantityDataPoint(
              value: value,
              unit: unit.unitString,
              startDate: sample.startDate,
              endDate: sample.endDate,
              metadata: metadataDict
            )
          }
          
          NSLog("✅ [HealthKit] Retrieved %d data points for %@", dataPoints.count, type)
          
          // Save to cache
          if useCacheValue {
            self.cacheManager.set(cacheKey, value: dataPoints, ttl: cacheTTLValue)
          }
          
          continuation.resume(returning: dataPoints)
        }
        
        self.healthStore.execute(query)
      }
    }
  }
  
  /// Fetch aggregated statistics for a quantity type
  public func getAggregatedQuantity(
    type: String,
    startDate: Date,
    endDate: Date,
    aggregationType: String,
    useCache: Bool?,
    cacheTTL: Double?
  ) throws -> Promise<Double> {
    return Promise.async { [weak self] in
      guard let self = self else { return 0 }
      
      let useCacheValue = useCache ?? true
      let cacheTTLValue = cacheTTL ?? self.defaultCacheTTL
      
      // Check cache
      let cacheKey = CacheManager.generateKey(type: type, startDate: startDate, endDate: endDate, options: "agg_\(aggregationType)")
      if useCacheValue, let cached: Double = self.cacheManager.get(cacheKey) {
        NSLog("✅ [HealthKit] Cache hit for aggregated %@", type)
        return cached
      }
      
      guard let identifier = HKQuantityTypeIdentifier.from(type),
            let quantityType = HKObjectType.quantityType(forIdentifier: identifier) else {
        throw NSError(domain: "HealthKit", code: 2, userInfo: [NSLocalizedDescriptionKey: "Invalid quantity type: \(type)"])
      }
      
      let predicate = HKQuery.predicateForSamples(withStart: startDate, end: endDate, options: [])
      let unit = identifier.defaultUnit()
      
      // Determine statistics options
      var options: HKStatisticsOptions
      switch aggregationType.lowercased() {
      case "sum":
        options = .cumulativeSum
      case "average", "avg":
        options = .discreteAverage
      case "min":
        options = .discreteMin
      case "max":
        options = .discreteMax
      default:
        options = identifier.statisticsOptions()
      }
      
      return try await withCheckedThrowingContinuation { continuation in
        let query = HKStatisticsQuery(
          quantityType: quantityType,
          quantitySamplePredicate: predicate,
          options: options
        ) { _, result, error in
          if let error = error {
            continuation.resume(throwing: error)
            return
          }
          
          var value: Double = 0
          
          switch aggregationType.lowercased() {
          case "sum":
            value = result?.sumQuantity()?.doubleValue(for: unit) ?? 0
          case "average", "avg":
            value = result?.averageQuantity()?.doubleValue(for: unit) ?? 0
          case "min":
            value = result?.minimumQuantity()?.doubleValue(for: unit) ?? 0
          case "max":
            value = result?.maximumQuantity()?.doubleValue(for: unit) ?? 0
          default:
            // By default, use sum or average depending on type
            if options == .cumulativeSum {
              value = result?.sumQuantity()?.doubleValue(for: unit) ?? 0
            } else {
              value = result?.averageQuantity()?.doubleValue(for: unit) ?? 0
            }
          }
          
          NSLog("✅ [HealthKit] Aggregated %@ for %@: %.2f", aggregationType, type, value)
          
          // Save to cache
          if useCacheValue {
            self.cacheManager.set(cacheKey, value: value, ttl: cacheTTLValue)
          }
          
          continuation.resume(returning: value)
        }
        
        self.healthStore.execute(query)
      }
    }
  }
  
  // MARK: - Category Data Retrieval
  
  /// Fetch category data
  public func getCategoryData(
    type: String,
    startDate: Date,
    endDate: Date,
    useCache: Bool?,
    cacheTTL: Double?
  ) throws -> Promise<[CategoryDataPoint]> {
    return Promise.async { [weak self] in
      guard let self = self else { return [] }
      
      let useCacheValue = useCache ?? true
      let cacheTTLValue = cacheTTL ?? self.defaultCacheTTL
      
      NSLog("🔍 [HealthKit] getCategoryData - Type: %@", type)
      
      // Check cache
      let cacheKey = CacheManager.generateKey(type: type, startDate: startDate, endDate: endDate)
      if useCacheValue, let cached: [CategoryDataPoint] = self.cacheManager.get(cacheKey) {
        NSLog("✅ [HealthKit] Cache hit for category %@", type)
        return cached
      }
      
      guard let identifier = HKCategoryTypeIdentifier.from(type),
            let categoryType = HKObjectType.categoryType(forIdentifier: identifier) else {
        throw NSError(domain: "HealthKit", code: 2, userInfo: [NSLocalizedDescriptionKey: "Invalid category type: \(type)"])
      }
      
      let predicate = HKQuery.predicateForSamples(withStart: startDate, end: endDate, options: [])
      let sortDescriptor = NSSortDescriptor(key: HKSampleSortIdentifierStartDate, ascending: false)
      
      return try await withCheckedThrowingContinuation { continuation in
        let query = HKSampleQuery(
          sampleType: categoryType,
          predicate: predicate,
          limit: HKObjectQueryNoLimit,
          sortDescriptors: [sortDescriptor]
        ) { _, samples, error in
          if let error = error {
            continuation.resume(throwing: error)
            return
          }
          
          guard let samples = samples as? [HKCategorySample] else {
            continuation.resume(returning: [])
            return
          }
          
          let dataPoints = samples.map { sample -> CategoryDataPoint in
            // Convert metadata from [String: Any] to [String: String]
            let metadataDict = sample.metadata?.compactMapValues { "\($0)" }
            return CategoryDataPoint(
              value: Double(sample.value),
              startDate: sample.startDate,
              endDate: sample.endDate,
              metadata: metadataDict
            )
          }
          
          NSLog("✅ [HealthKit] Retrieved %d category data points for %@", dataPoints.count, type)
          
          // Save to cache
          if useCacheValue {
            self.cacheManager.set(cacheKey, value: dataPoints, ttl: cacheTTLValue)
          }
          
          continuation.resume(returning: dataPoints)
        }
        
        self.healthStore.execute(query)
      }
    }
  }
  
  // MARK: - Time Range Helpers
  
  /// Fetch data for a defined period
  public func getHealthDataForTimeRange(
    timeRange: String,
    customStartDate: Date?,
    customEndDate: Date?,
    useCache: Bool?,
    cacheTTL: Double?
  ) throws -> Promise<HealthData> {
    return Promise.async { [weak self] in
      guard let self = self else { throw NSError(domain: "HealthKit", code: 1, userInfo: nil) }
      
      // Convert string to TimeRange
      guard let range = TimeRangeHelper.TimeRange(rawValue: timeRange) else {
        throw NSError(domain: "HealthKit", code: 3, userInfo: [NSLocalizedDescriptionKey: "Invalid time range: \(timeRange)"])
      }
      
      let dates = TimeRangeHelper.getDates(for: range, customStart: customStartDate, customEnd: customEndDate)
      NSLog("🔍 [HealthKit] Time range: %@ (%@ to %@)", timeRange, dates.start.description, dates.end.description)
      
      // Fetch data
      return try await self.getHealthData(startDate: dates.start, endDate: dates.end).await()
    }
  }
  
  // getRealtimeData removed in 2.x — Map<JString, double> was unsupported by
  // fbjni on Android. The TS hook `useHealthKitRealtime` now fans out via
  // getAggregatedQuantity instead, keeping the same observable behaviour.

  // MARK: - Workout Data Retrieval
  
  /// Fetch workout data
  public func getWorkouts(
    startDate: Date,
    endDate: Date,
    useCache: Bool?,
    cacheTTL: Double?
  ) throws -> Promise<[WorkoutDataPoint]> {
    return Promise.async { [weak self] in
      guard let self = self else { return [] }
      
      let useCacheValue = useCache ?? true
      let cacheTTLValue = cacheTTL ?? self.defaultCacheTTL
      
      NSLog("🔍 [HealthKit] getWorkouts - Start")
      
      // Check cache
      let cacheKey = CacheManager.generateKey(type: "workouts", startDate: startDate, endDate: endDate)
      if useCacheValue, let cached: [WorkoutDataPoint] = self.cacheManager.get(cacheKey) {
        NSLog("✅ [HealthKit] Cache hit for workouts")
        return cached
      }
      
      let workoutType = HKWorkoutType.workoutType()
      let predicate = HKQuery.predicateForSamples(withStart: startDate, end: endDate, options: [])
      let sortDescriptor = NSSortDescriptor(key: HKSampleSortIdentifierStartDate, ascending: false)
      
      return try await withCheckedThrowingContinuation { continuation in
        let query = HKSampleQuery(
          sampleType: workoutType,
          predicate: predicate,
          limit: HKObjectQueryNoLimit,
          sortDescriptors: [sortDescriptor]
        ) { _, samples, error in
          if let error = error {
            continuation.resume(throwing: error)
            return
          }
          
          guard let workouts = samples as? [HKWorkout] else {
            continuation.resume(returning: [])
            return
          }
          
          let dataPoints = workouts.map { workout -> WorkoutDataPoint in
            let activityType = workout.workoutActivityType.rawValue
            let activityName = self.workoutActivityTypeName(workout.workoutActivityType)

            // detect indoor/outdoor via metadata (fallback: assume outdoor)
            let isIndoor = (workout.metadata?[HKMetadataKeyIndoorWorkout] as? Bool) ?? false
            let location = isIndoor ? "indoor" : "outdoor"

            // safe numeric fallbacks to 0
            let duration = workout.duration
            let energyBurned = workout.totalEnergyBurned?.doubleValue(for: .kilocalorie()) ?? 0
            let distance = workout.totalDistance?.doubleValue(for: .meter()) ?? 0
            
            // Convert metadata from [String: Any] to [String: String]
            let metadataDict = workout.metadata?.compactMapValues { "\($0)" } ?? [:]
            
            return WorkoutDataPoint(
              workoutActivityType: Double(activityType),
              workoutActivityName: activityName,
              duration: duration,
              totalEnergyBurned: energyBurned,
              totalDistance: distance,
              startDate: workout.startDate,
              endDate: workout.endDate,
              metadata: metadataDict.merging(["location": location], uniquingKeysWith: { $1 })
            )
          }
          
          NSLog("✅ [HealthKit] Retrieved %d workouts", dataPoints.count)
          
          // Save to cache
          if useCacheValue {
            self.cacheManager.set(cacheKey, value: dataPoints, ttl: cacheTTLValue)
          }
          
          continuation.resume(returning: dataPoints)
        }
        
        self.healthStore.execute(query)
      }
    }
  }
  
  /// Helper to get workout activity type name
  private func workoutActivityTypeName(_ activityType: HKWorkoutActivityType) -> String {
    switch activityType {
    case .running: return "Running"
    case .walking: return "Walking"
    case .cycling: return "Cycling"
    case .swimming: return "Swimming"
    case .yoga: return "Yoga"
    case .functionalStrengthTraining: return "Functional Strength Training"
    case .traditionalStrengthTraining: return "Traditional Strength Training"
    case .elliptical: return "Elliptical"
    case .rowing: return "Rowing"
    case .stairs: return "Stairs"
    case .stepTraining: return "Step Training"
    case .hiking: return "Hiking"
    case .soccer: return "Soccer"
    case .basketball: return "Basketball"
    case .tennis: return "Tennis"
    case .golf: return "Golf"
    case .baseball: return "Baseball"
    case .americanFootball: return "American Football"
    case .dance: return "Dance"
    case .boxing: return "Boxing"
    case .climbing: return "Climbing"
    case .crossTraining: return "Cross Training"
    case .mixedCardio: return "Mixed Cardio"
    case .highIntensityIntervalTraining: return "HIIT"
    case .jumpRope: return "Jump Rope"
    case .pilates: return "Pilates"
    case .flexibility: return "Flexibility"
    case .coreTraining: return "Core Training"
    case .mindAndBody: return "Mind and Body"
    case .preparationAndRecovery: return "Preparation and Recovery"
    case .barre: return "Barre"
    case .cooldown: return "Cooldown"
    case .kickboxing: return "Kickboxing"
    case .martialArts: return "Martial Arts"
    case .skatingSports: return "Skating Sports"
    case .snowSports: return "Snow Sports"
    case .waterFitness: return "Water Fitness"
    case .waterPolo: return "Water Polo"
    case .waterSports: return "Water Sports"
    case .handball: return "Handball"
    case .hockey: return "Hockey"
    case .lacrosse: return "Lacrosse"
    case .rugby: return "Rugby"
    case .volleyball: return "Volleyball"
    case .other: return "Other"
    default: return "Unknown"
    }
  }
  
  // MARK: - Cache Management
  
  /// Clear cache
  public func clearCache() throws -> Promise<Void> {
    return Promise.async { [weak self] in
      guard let self = self else { return }
      self.cacheManager.clear()
      NSLog("✅ [HealthKit] Cache cleared")
    }
  }
  
  // MARK: - Utility Methods
  
  /// Check if HealthKit is available
  public func isHealthKitAvailable() throws -> Promise<Bool> {
    return Promise.async {
      let available = HKHealthStore.isHealthDataAvailable()
      NSLog("✅ [HealthKit] HealthKit available: %@", available ? "yes" : "no")
      return available
    }
  }
  
  /// Check authorization status for a specific type
  public func checkAuthorizationStatus(type: String) throws -> Promise<String> {
    return Promise.async { [weak self] in
      guard let self = self else { return "notDetermined" }
      
      var objectType: HKObjectType?
      
      // Essayer d'abord comme quantity type
      if let identifier = HKQuantityTypeIdentifier.from(type) {
        objectType = HKObjectType.quantityType(forIdentifier: identifier)
      }
      // Sinon essayer comme category type
      else if let identifier = HKCategoryTypeIdentifier.from(type) {
        objectType = HKObjectType.categoryType(forIdentifier: identifier)
      }
      
      guard let type = objectType else {
        throw NSError(domain: "HealthKit", code: 2, userInfo: [NSLocalizedDescriptionKey: "Invalid type: \(type)"])
      }
      
      let status = self.healthStore.authorizationStatus(for: type)
      
      switch status {
      case .notDetermined:
        return "notDetermined"
      case .sharingDenied:
        return "sharingDenied"
      case .sharingAuthorized:
        return "sharingAuthorized"
      @unknown default:
        return "notDetermined"
      }
    }
  }

  public func seedTestHealthData() throws -> Promise<Void> {
    return Promise.async {
      #if DEBUG
      let seeder = HealthDataSeeder(healthStore: self.healthStore)
      try await seeder.seedData()
      #else
      print("⚠️ seedTestHealthData called in non-DEBUG build. Ignoring.")
      #endif
    }
  }

  // MARK: - Write APIs (iOS implementation pending parity work)

  public func writeQuantityData(
    type: String,
    value: Double,
    unit: String,
    startDate: Date,
    endDate: Date,
    metadata: [String: String]?
  ) throws -> Promise<Bool> {
    return Promise.async { [weak self] in
      guard let self = self else { return false }
      guard let identifier = HKQuantityTypeIdentifier.from(type),
            let quantityType = HKObjectType.quantityType(forIdentifier: identifier) else {
        NSLog("❌ [HealthKit] writeQuantityData - unknown type %@", type)
        return false
      }
      let hkUnit = HKUnit(from: unit.isEmpty ? identifier.defaultUnit().unitString : unit)
      let quantity = HKQuantity(unit: hkUnit, doubleValue: value)
      let sample = HKQuantitySample(type: quantityType, quantity: quantity, start: startDate, end: endDate, metadata: metadata)
      return try await withCheckedThrowingContinuation { continuation in
        self.healthStore.save(sample) { success, error in
          if let error = error {
            continuation.resume(throwing: error)
          } else {
            continuation.resume(returning: success)
          }
        }
      }
    }
  }

  public func writeCategoryData(
    type: String,
    value: Double,
    startDate: Date,
    endDate: Date,
    metadata: [String: String]?
  ) throws -> Promise<Bool> {
    return Promise.async { [weak self] in
      guard let self = self else { return false }
      guard let identifier = HKCategoryTypeIdentifier.from(type),
            let categoryType = HKObjectType.categoryType(forIdentifier: identifier) else {
        NSLog("❌ [HealthKit] writeCategoryData - unknown type %@", type)
        return false
      }
      let sample = HKCategorySample(type: categoryType, value: Int(value), start: startDate, end: endDate, metadata: metadata)
      return try await withCheckedThrowingContinuation { continuation in
        self.healthStore.save(sample) { success, error in
          if let error = error {
            continuation.resume(throwing: error)
          } else {
            continuation.resume(returning: success)
          }
        }
      }
    }
  }

  // MARK: - Observers / Background sync (no-op stubs on iOS — full parity tracked separately)

  public func observeQuantityChanges(type: String, callback: @escaping (String) -> Void) throws -> Promise<String> {
    return Promise.async {
      NSLog("⚠️ [HealthKit] observeQuantityChanges is not yet implemented on iOS — returning placeholder subscription id")
      return ""
    }
  }

  public func observeCategoryChanges(type: String, callback: @escaping (String) -> Void) throws -> Promise<String> {
    return Promise.async {
      NSLog("⚠️ [HealthKit] observeCategoryChanges is not yet implemented on iOS — returning placeholder subscription id")
      return ""
    }
  }

  public func removeObserver(subscriptionId: String) throws -> Promise<Void> {
    return Promise.async {
      NSLog("⚠️ [HealthKit] removeObserver is not yet implemented on iOS")
    }
  }

  public func removeAllObservers() throws -> Promise<Void> {
    return Promise.async {
      NSLog("⚠️ [HealthKit] removeAllObservers is not yet implemented on iOS")
    }
  }

  public func registerBackgroundSync(config: BackgroundSyncConfig) throws -> Promise<Void> {
    return Promise.async {
      NSLog("⚠️ [HealthKit] registerBackgroundSync is not yet implemented on iOS — call from JS will no-op")
    }
  }

  public func unregisterBackgroundSync() throws -> Promise<Void> {
    return Promise.async {
      NSLog("⚠️ [HealthKit] unregisterBackgroundSync is not yet implemented on iOS")
    }
  }

  public func isBackgroundSyncRegistered() throws -> Promise<Bool> {
    return Promise.async {
      return false
    }
  }

}