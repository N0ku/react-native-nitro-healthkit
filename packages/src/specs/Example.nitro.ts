import type { HybridObject } from 'react-native-nitro-modules'

// HealthKit Data Types
export enum HealthKitQuantityType {
  // Activity & Fitness
  STEPS = 'HKQuantityTypeIdentifierStepCount',
  DISTANCE_WALKING_RUNNING = 'HKQuantityTypeIdentifierDistanceWalkingRunning',
  DISTANCE_CYCLING = 'HKQuantityTypeIdentifierDistanceCycling',
  DISTANCE_SWIMMING = 'HKQuantityTypeIdentifierDistanceSwimming',

  // Energy & Nutrition
  ACTIVE_ENERGY_BURNED = 'HKQuantityTypeIdentifierActiveEnergyBurned',
  BASAL_ENERGY_BURNED = 'HKQuantityTypeIdentifierBasalEnergyBurned',
  DIETARY_ENERGY_CONSUMED = 'HKQuantityTypeIdentifierDietaryEnergyConsumed',

  // Vital Signs
  HEART_RATE = 'HKQuantityTypeIdentifierHeartRate',
  RESTING_HEART_RATE = 'HKQuantityTypeIdentifierRestingHeartRate',
  HEART_RATE_VARIABILITY_SDNN = 'HKQuantityTypeIdentifierHeartRateVariabilitySDNN',
  BLOOD_PRESSURE_SYSTOLIC = 'HKQuantityTypeIdentifierBloodPressureSystolic',
  BLOOD_PRESSURE_DIASTOLIC = 'HKQuantityTypeIdentifierBloodPressureDiastolic',
  BLOOD_OXYGEN_SATURATION = 'HKQuantityTypeIdentifierOxygenSaturation',
  BLOOD_GLUCOSE = 'HKQuantityTypeIdentifierBloodGlucose',
  BODY_TEMPERATURE = 'HKQuantityTypeIdentifierBodyTemperature',

  // Body Measurements
  BODY_MASS = 'HKQuantityTypeIdentifierBodyMass',
  BODY_FAT_PERCENTAGE = 'HKQuantityTypeIdentifierBodyFatPercentage',
  LEAN_BODY_MASS = 'HKQuantityTypeIdentifierLeanBodyMass',
  HEIGHT = 'HKQuantityTypeIdentifierHeight',
  WAIST_CIRCUMFERENCE = 'HKQuantityTypeIdentifierWaistCircumference',

  // Respiratory
  RESPIRATORY_RATE = 'HKQuantityTypeIdentifierRespiratoryRate',
  FORCED_EXPIRATORY_VOLUME_1 = 'HKQuantityTypeIdentifierForcedExpiratoryVolume1',
  FORCED_VITAL_CAPACITY = 'HKQuantityTypeIdentifierForcedVitalCapacity',
  PEAK_EXPIRATORY_FLOW_RATE = 'HKQuantityTypeIdentifierPeakExpiratoryFlowRate',

  // Other
  FLIGHTS_CLIMBED = 'HKQuantityTypeIdentifierFlightsClimbed',
  UV_EXPOSURE = 'HKQuantityTypeIdentifierUVExposure',
  WATER_TEMPERATURE = 'HKQuantityTypeIdentifierWaterTemperature',
  BASAL_BODY_TEMPERATURE = 'HKQuantityTypeIdentifierBasalBodyTemperature',
  ELECTRODERMAL_ACTIVITY = 'HKQuantityTypeIdentifierElectrodermalActivity',
  BLOOD_ALCOHOL_CONTENT = 'HKQuantityTypeIdentifierBloodAlcoholContent',
  NUMBER_OF_TIMES_FALLEN = 'HKQuantityTypeIdentifierNumberOfTimesFallen',
  PUSH_COUNT = 'HKQuantityTypeIdentifierPushCount',
  DISTANCE_WHEELCHAIR = 'HKQuantityTypeIdentifierDistanceWheelchair',
  SWIMMING_STROKE_COUNT = 'HKQuantityTypeIdentifierSwimmingStrokeCount',
  WALKING_SPEED = 'HKQuantityTypeIdentifierWalkingSpeed',
  WALKING_STEP_LENGTH = 'HKQuantityTypeIdentifierWalkingStepLength',
  WALKING_ASYMMETRY_PERCENTAGE = 'HKQuantityTypeIdentifierWalkingAsymmetryPercentage',
  WALKING_DOUBLE_SUPPORT_PERCENTAGE = 'HKQuantityTypeIdentifierWalkingDoubleSupportPercentage',
  SIX_MINUTE_WALK_TEST_DISTANCE = 'HKQuantityTypeIdentifierSixMinuteWalkTestDistance',
  STAIR_ASCENT_SPEED = 'HKQuantityTypeIdentifierStairAscentSpeed',
  STAIR_DESCENT_SPEED = 'HKQuantityTypeIdentifierStairDescentSpeed',
  VO2_MAX = 'HKQuantityTypeIdentifierVO2Max',
  ATRIAL_FIBRILLATION_BURDEN = 'HKQuantityTypeIdentifierAtrialFibrillationBurden',
  UNDERWATER_DEPTH = 'HKQuantityTypeIdentifierUnderwaterDepth',
  WATER_SALINITY = 'HKQuantityTypeIdentifierWaterSalinity',
  ENVIRONMENTAL_AUDIO_EXPOSURE = 'HKQuantityTypeIdentifierEnvironmentalAudioExposure',
  HEADPHONE_AUDIO_EXPOSURE = 'HKQuantityTypeIdentifierHeadphoneAudioExposure',
  TIME_IN_DAYLIGHT = 'HKQuantityTypeIdentifierTimeInDaylight',
  PHYSICAL_EFFORT = 'HKQuantityTypeIdentifierPhysicalEffort',
  CYCLING_CADENCE = 'HKQuantityTypeIdentifierCyclingCadence',
  CYCLING_FUNCTIONAL_THRESHOLD_POWER = 'HKQuantityTypeIdentifierCyclingFunctionalThresholdPower',
  CYCLING_POWER = 'HKQuantityTypeIdentifierCyclingPower',
  RUNNING_GROUND_CONTACT_TIME = 'HKQuantityTypeIdentifierRunningGroundContactTime',
  RUNNING_POWER = 'HKQuantityTypeIdentifierRunningPower',
  RUNNING_SPEED = 'HKQuantityTypeIdentifierRunningSpeed',
  RUNNING_STRIDE_LENGTH = 'HKQuantityTypeIdentifierRunningStrideLength',
  RUNNING_VERTICAL_OSCILLATION = 'HKQuantityTypeIdentifierRunningVerticalOscillation',
  APPLE_EXERCISE_TIME = 'HKQuantityTypeIdentifierAppleExerciseTime',
  APPLE_STAND_TIME = 'HKQuantityTypeIdentifierAppleStandTime',
  APPLE_WALKING_STEADINESS = 'HKQuantityTypeIdentifierAppleWalkingSteadiness',
  APPLE_SLEEPING_WRIST_TEMPERATURE = 'HKQuantityTypeIdentifierAppleSleepingWristTemperature',
  CROSS_TRAINING = 'HKQuantityTypeIdentifierCrossTraining',
  CYCLING = 'HKQuantityTypeIdentifierCycling',
  ELLIPTICAL = 'HKQuantityTypeIdentifierElliptical',
  FUNCTIONAL_STRENGTH_TRAINING = 'HKQuantityTypeIdentifierFunctionalStrengthTraining',
  HIGH_INTENSITY_INTERVAL_TRAINING = 'HKQuantityTypeIdentifierHighIntensityIntervalTraining',
  JUMP_ROPE = 'HKQuantityTypeIdentifierJumpRope',
  MIXED_CARDIO = 'HKQuantityTypeIdentifierMixedCardio',
  OTHER = 'HKQuantityTypeIdentifierOther',
  PILATES = 'HKQuantityTypeIdentifierPilates',
  ROWING = 'HKQuantityTypeIdentifierRowing',
  RUNNING = 'HKQuantityTypeIdentifierRunning',
  SKIING = 'HKQuantityTypeIdentifierSkiing',
  SNOWBOARDING = 'HKQuantityTypeIdentifierSnowboarding',
  SOCCER = 'HKQuantityTypeIdentifierSoccer',
  STAIRS = 'HKQuantityTypeIdentifierStairs',
  STEP_TRAINING = 'HKQuantityTypeIdentifierStepTraining',
  SURFING = 'HKQuantityTypeIdentifierSurfing',
  SWIMMING = 'HKQuantityTypeIdentifierSwimming',
  TABLE_TENNIS = 'HKQuantityTypeIdentifierTableTennis',
  TENNIS = 'HKQuantityTypeIdentifierTennis',
  TRADITIONAL_STRENGTH_TRAINING = 'HKQuantityTypeIdentifierTraditionalStrengthTraining',
  VOLLEYBALL = 'HKQuantityTypeIdentifierVolleyball',
  WALKING = 'HKQuantityTypeIdentifierWalking',
  WATER_POLO = 'HKQuantityTypeIdentifierWaterPolo',
  WRESTLING = 'HKQuantityTypeIdentifierWrestling',
  YOGA = 'HKQuantityTypeIdentifierYoga',
  BARRE = 'HKQuantityTypeIdentifierBarre',
  BASKETBALL = 'HKQuantityTypeIdentifierBasketball',
  BOWLING = 'HKQuantityTypeIdentifierBowling',
  BOXING = 'HKQuantityTypeIdentifierBoxing',
  CLIMBING = 'HKQuantityTypeIdentifierClimbing',
  CORE_TRAINING = 'HKQuantityTypeIdentifierCoreTraining',
  CRICKET = 'HKQuantityTypeIdentifierCricket',
  DANCE = 'HKQuantityTypeIdentifierDance',
  DISC_SPORTS = 'HKQuantityTypeIdentifierDiscSports',
  DOWNHILL_SKIING = 'HKQuantityTypeIdentifierDownhillSkiing',
  EQUESTRIAN_SPORTS = 'HKQuantityTypeIdentifierEquestrianSports',
  FENCING = 'HKQuantityTypeIdentifierFencing',
  FISHING = 'HKQuantityTypeIdentifierFishing',
  FLEXIBILITY = 'HKQuantityTypeIdentifierFlexibility',
  GOLF = 'HKQuantityTypeIdentifierGolf',
  GYMNASTICS = 'HKQuantityTypeIdentifierGymnastics',
  HANDBALL = 'HKQuantityTypeIdentifierHandball',
  HIKING = 'HKQuantityTypeIdentifierHiking',
  HOCKEY = 'HKQuantityTypeIdentifierHockey',
  HUNTING = 'HKQuantityTypeIdentifierHunting',
  LACROSSE = 'HKQuantityTypeIdentifierLacrosse',
  MARTIAL_ARTS = 'HKQuantityTypeIdentifierMartialArts',
  MINDFULNESS = 'HKQuantityTypeIdentifierMindfulness',
  PREPARATION_AND_RECOVERY = 'HKQuantityTypeIdentifierPreparationAndRecovery',
  RACQUETBALL = 'HKQuantityTypeIdentifierRacquetball',
  RUGBY = 'HKQuantityTypeIdentifierRugby',
  SAILING = 'HKQuantityTypeIdentifierSailing',
  SKATING_SPORTS = 'HKQuantityTypeIdentifierSkatingSports',
  SNOW_SPORTS = 'HKQuantityTypeIdentifierSnowSports',
  SOFTBALL = 'HKQuantityTypeIdentifierSoftball',
  SQUASH = 'HKQuantityTypeIdentifierSquash',
  STAIR_CLIMBING = 'HKQuantityTypeIdentifierStairClimbing',
  TRACK_AND_FIELD = 'HKQuantityTypeIdentifierTrackAndField',
  WATER_SPORTS = 'HKQuantityTypeIdentifierWaterSports',
  WEIGHT_LIFTING = 'HKQuantityTypeIdentifierWeightLifting'
}

export enum HealthKitCategoryType {
  SLEEP_ANALYSIS = 'HKCategoryTypeIdentifierSleepAnalysis',
  APPLE_STAND_HOUR = 'HKCategoryTypeIdentifierAppleStandHour',
  CIGARETTE_SMOKING = 'HKCategoryTypeIdentifierCigaretteSmoking',
  MENSTRUAL_FLOW = 'HKCategoryTypeIdentifierMenstrualFlow',
  INTERMENSTRUAL_BLEEDING = 'HKCategoryTypeIdentifierIntermenstrualBleeding',
  INFREQUENT_MENSTRUAL_BLEEDING = 'HKCategoryTypeIdentifierInfrequentMenstrualBleeding',
  PERSISTENT_INTERMENSTRUAL_BLEEDING = 'HKCategoryTypeIdentifierPersistentIntermenstrualBleeding',
  PROLONGED_MENSTRUAL_BLEEDING = 'HKCategoryTypeIdentifierProlongedMenstrualBleeding',
  IRREGULAR_MENSTRUAL_CYCLES = 'HKCategoryTypeIdentifierIrregularMenstrualCycles',
  PREGNANCY = 'HKCategoryTypeIdentifierPregnancy',
  PREGNANCY_TEST_RESULT = 'HKCategoryTypeIdentifierPregnancyTestResult',
  PROSTATE_SPECIFIC_ANTIGEN = 'HKCategoryTypeIdentifierProstateSpecificAntigen',
  VAGINAL_DRYNESS = 'HKCategoryTypeIdentifierVaginalDryness',
  OVULATION_TEST_RESULT = 'HKCategoryTypeIdentifierOvulationTestResult',
  SEXUAL_ACTIVITY = 'HKCategoryTypeIdentifierSexualActivity',
  PREGNANCY_INTENT = 'HKCategoryTypeIdentifierPregnancyIntent',
  LACTATION = 'HKCategoryTypeIdentifierLactation',
  CONTRACEPTIVE = 'HKCategoryTypeIdentifierContraceptive',
  GALLBLADDER_ATTACK = 'HKCategoryTypeIdentifierGallbladderAttack',
  BLADDER_INCONTINENCE = 'HKCategoryTypeIdentifierBladderIncontinence',
  DYSMENORRHEA = 'HKCategoryTypeIdentifierDysmenorrhea',
  PELVIC_PAIN = 'HKCategoryTypeIdentifierPelvicPain',
  ACNE = 'HKCategoryTypeIdentifierAcne',
  APPLE_WALKING_STEADINESS_EVENT = 'HKCategoryTypeIdentifierAppleWalkingSteadinessEvent',
  ENVIRONMENTAL_AUDIO_EXPOSURE_EVENT = 'HKCategoryTypeIdentifierEnvironmentalAudioExposureEvent',
  HEADPHONE_AUDIO_EXPOSURE_EVENT = 'HKCategoryTypeIdentifierHeadphoneAudioExposureEvent',
  LOW_HEART_RATE_EVENT = 'HKCategoryTypeIdentifierLowHeartRateEvent',
  HIGH_HEART_RATE_EVENT = 'HKCategoryTypeIdentifierHighHeartRateEvent',
  IRREGULAR_HEART_RHYTHM_EVENT = 'HKCategoryTypeIdentifierIrregularHeartRhythmEvent',
  TOOTHBRUSHING_EVENT = 'HKCategoryTypeIdentifierToothbrushingEvent',
  MINDFUL_SESSION = 'HKCategoryTypeIdentifierMindfulSession',
  HANDWASHING_EVENT = 'HKCategoryTypeIdentifierHandwashingEvent',
  LOW_CARDIO_FITNESS_EVENT = 'HKCategoryTypeIdentifierLowCardioFitnessEvent',
  ABDOMINAL_CRAMPS = 'HKCategoryTypeIdentifierAbdominalCramps',
  BLOATING = 'HKCategoryTypeIdentifierBloating',
  CONSTIPATION = 'HKCategoryTypeIdentifierConstipation',
  DIARRHEA = 'HKCategoryTypeIdentifierDiarrhea',
  HEARTBURN = 'HKCategoryTypeIdentifierHeartburn',
  NAUSEA = 'HKCategoryTypeIdentifierNausea',
  VOMITING = 'HKCategoryTypeIdentifierVomiting',
  APPLE_FERTILITY_PREDICTION = 'HKCategoryTypeIdentifierAppleFertilityPrediction',
  BREASTFEEDING = 'HKCategoryTypeIdentifierBreastfeeding',
  COUGHING = 'HKCategoryTypeIdentifierCoughing',
  WHEEZING = 'HKCategoryTypeIdentifierWheezing',
  RAPID_POUNDING_OR_FLUTTERING_HEARTBEAT = 'HKCategoryTypeIdentifierRapidPoundingOrFlutteringHeartbeat',
  SKIPPED_HEARTBEAT = 'HKCategoryTypeIdentifierSkippedHeartbeat',
  FATIGUE = 'HKCategoryTypeIdentifierFatigue',
  SHORTNESS_OF_BREATH = 'HKCategoryTypeIdentifierShortnessOfBreath',
  SLEEP_CHANGES = 'HKCategoryTypeIdentifierSleepChanges',
  MOOD_CHANGES = 'HKCategoryTypeIdentifierMoodChanges',
  APPETITE_CHANGES = 'HKCategoryTypeIdentifierAppetiteChanges',
  HEADACHE = 'HKCategoryTypeIdentifierHeadache',
  CHEST_TIGHTNESS_OR_PAIN = 'HKCategoryTypeIdentifierChestTightnessOrPain',
  LOWER_BACK_PAIN = 'HKCategoryTypeIdentifierLowerBackPain',
  HOT_FLASHES = 'HKCategoryTypeIdentifierHotFlashes',
  CHILLS = 'HKCategoryTypeIdentifierChills',
  DIZZINESS = 'HKCategoryTypeIdentifierDizziness',
  FUZZY_VISION = 'HKCategoryTypeIdentifierFuzzyVision',
  MEMORY_LAPSE = 'HKCategoryTypeIdentifierMemoryLapse',
  DECREASED_APPETITE = 'HKCategoryTypeIdentifierDecreasedAppetite',
  INCREASED_APPETITE = 'HKCategoryTypeIdentifierIncreasedAppetite',
  FOOD_POISONING = 'HKCategoryTypeIdentifierFoodPoisoning',
  GENERALIZED_BODY_ACHE = 'HKCategoryTypeIdentifierGeneralizedBodyAche',
  LOSS_OF_SMELL = 'HKCategoryTypeIdentifierLossOfSmell',
  LOSS_OF_TASTE = 'HKCategoryTypeIdentifierLossOfTaste',
  RUNNY_NOSE = 'HKCategoryTypeIdentifierRunnyNose',
  SORE_THROAT = 'HKCategoryTypeIdentifierSoreThroat',
  SINUS_CONGESTION = 'HKCategoryTypeIdentifierSinusCongestion',
  COUGH_TYPE = 'HKCategoryTypeIdentifierCoughType',
  WELLBEING = 'HKCategoryTypeIdentifierWellbeing',
  SYMPTOMS = 'HKCategoryTypeIdentifierSymptoms',
  TREATMENTS = 'HKCategoryTypeIdentifierTreatments'
}

export enum TimeRange {
  TODAY = 'today',
  YESTERDAY = 'yesterday',
  THIS_WEEK = 'this_week',
  LAST_WEEK = 'last_week',
  THIS_MONTH = 'this_month',
  LAST_MONTH = 'last_month',
  THIS_YEAR = 'this_year',
  LAST_YEAR = 'last_year',
  LAST_7_DAYS = 'last_7_days',
  LAST_30_DAYS = 'last_30_days',
  LAST_90_DAYS = 'last_90_days',
  CUSTOM = 'custom'
}

export enum AggregationType {
  SUM = 'sum',
  AVERAGE = 'average',
  MIN = 'min',
  MAX = 'max',
  COUNT = 'count'
}

// Enhanced Health Data Interface
//
// NOTE: an earlier draft exposed `quantities?: Record<string, number>` and
// `categories?: Record<string, number>` as generic catch-alls. Nitrogen turns
// those into `JMap<JString, double>` on the C++ side, which fbjni 0.7 cannot
// instantiate (it requires boxed reference types as JMap values). The fields
// were never consumed by the host app, so we drop them and keep the explicit
// scalar columns only.
export interface HealthData {
  steps?: number
  heartRate?: number
  activeEnergy?: number
  distance?: number
  sleepAnalysis?: string
}

// Quantity Data Point
export interface QuantityDataPoint {
  value: number
  unit: string
  startDate: Date
  endDate: Date
  metadata?: Record<string, string>
}

// Category Data Point
export interface CategoryDataPoint {
  value: number
  startDate: Date
  endDate: Date
  metadata?: Record<string, string>
}

// Workout Data Point
export interface WorkoutDataPoint {
  workoutActivityType: number
  workoutActivityName: string
  duration: number
  totalEnergyBurned?: number
  totalDistance?: number
  startDate: Date
  endDate: Date
  metadata?: Record<string, string>
}

// Cache Configuration
export interface CacheConfig {
  enabled: boolean
  ttl: number // Time to live in milliseconds
  maxSize: number // Maximum cache size
}

// Query Options
export interface QueryOptions {
  timeRange: TimeRange
  aggregationType: AggregationType
  cache?: CacheConfig
  limit?: number
  sortOrder?: 'ascending' | 'descending'
}

// Background sync configuration (used by registerBackgroundSync)
export interface BackgroundSyncConfig {
  apiBaseUrl: string
  jwtToken: string
  intervalMinutes: number
  types: string[]
  syncPath: string
}

// Change notification payload (used by observer callbacks)
export interface HealthChangeEvent {
  type: string
  changesToken: string
  changedAt: Date
}

export interface HealthKit extends HybridObject<{ ios: 'swift', android: 'kotlin' }> {
  /**
   * Request HealthKit authorizations.
   */
  requestAuthorization(): Promise<boolean>

  /**
   * Fetch raw quantity samples (individual data points).
   * @param type Quantity type (e.g. HealthKitQuantityType.STEPS)
   * @param startDate Start date
   * @param endDate End date
   * @param aggregationType Aggregation type (optional)
   * @param useCache Enable cache (default: true)
   * @param cacheTTL Cache time-to-live in seconds (default: 60)
   */
  getQuantityData(
    type: string,
    startDate: Date,
    endDate: Date,
    aggregationType?: string | null,
    useCache?: boolean,
    cacheTTL?: number
  ): Promise<QuantityDataPoint[]>

  /**
   * Fetch an aggregated value for a quantity type.
   * @param type Quantity type
   * @param startDate Start date
   * @param endDate End date
   * @param aggregationType Aggregation type: 'sum', 'average', 'min', 'max'
   * @param useCache Enable cache
   * @param cacheTTL Cache time-to-live in seconds
   */
  getAggregatedQuantity(
    type: string,
    startDate: Date,
    endDate: Date,
    aggregationType: string,
    useCache?: boolean,
    cacheTTL?: number
  ): Promise<number>

  /**
   * Fetch category data.
   * @param type Category type (e.g. HealthKitCategoryType.SLEEP_ANALYSIS)
   * @param startDate Start date
   * @param endDate End date
   * @param useCache Enable cache
   * @param cacheTTL Cache time-to-live in seconds
   */
  getCategoryData(
    type: string,
    startDate: Date,
    endDate: Date,
    useCache?: boolean,
    cacheTTL?: number
  ): Promise<CategoryDataPoint[]>

  /**
   * Fetch health data for a defined period.
   * @param timeRange Predefined period (TimeRange.TODAY, TimeRange.THIS_WEEK, etc.)
   * @param customStartDate Custom start date (when timeRange = CUSTOM)
   * @param customEndDate Custom end date (when timeRange = CUSTOM)
   * @param useCache Enable cache
   * @param cacheTTL Cache time-to-live in seconds
   */
  getHealthDataForTimeRange(
    timeRange: string,
    customStartDate?: Date | null,
    customEndDate?: Date | null,
    useCache?: boolean,
    cacheTTL?: number
  ): Promise<HealthData>

  // getRealtimeData was removed in 2.x — its `Promise<Record<string, number>>`
  // return type was unsupported by fbjni on Android (Map<JString, double> is not
  // instantiable). The host app never consumed it; callers should use
  // getAggregatedQuantity per metric instead.

  /**
   * Clear the cache.
   */
  clearCache(): Promise<void>

  /**
   * Check whether HealthKit is available.
   */
  isHealthKitAvailable(): Promise<boolean>

  /**
   * Check the authorization status for a specific type.
   * @param type Data type (quantity or category)
   * @returns 'notDetermined' | 'sharingDenied' | 'sharingAuthorized'
   */
  checkAuthorizationStatus(type: string): Promise<string>

  /**
   * Fetch workout data.
   * @param startDate Start date
   * @param endDate End date
   * @param useCache Enable cache
   * @param cacheTTL Cache time-to-live in seconds
   */
  getWorkouts(
    startDate: Date,
    endDate: Date,
    useCache?: boolean,
    cacheTTL?: number
  ): Promise<WorkoutDataPoint[]>

  // Legacy methods for backward compatibility
  getSteps(startDate: Date, endDate: Date): Promise<number>
  getHeartRate(startDate: Date, endDate: Date): Promise<number>
  getHealthData(startDate: Date, endDate: Date): Promise<HealthData>

  /**
   * Seed test data into HealthKit (debug only).
   */
  seedTestHealthData(): Promise<void>

  /**
   * Write a quantity sample to the native health store
   * (HealthKit on iOS, Health Connect on Android).
   * @returns true if the write succeeded, false otherwise (e.g. unsupported type, permission denied)
   */
  writeQuantityData(
    type: string,
    value: number,
    unit: string,
    startDate: Date,
    endDate: Date,
    metadata?: Record<string, string> | null
  ): Promise<boolean>

  /**
   * Write a category sample to the native health store.
   * @returns true if the write succeeded, false otherwise
   */
  writeCategoryData(
    type: string,
    value: number,
    startDate: Date,
    endDate: Date,
    metadata?: Record<string, string> | null
  ): Promise<boolean>

  /**
   * Subscribe to changes for a quantity type.
   * The callback receives the current changesToken on each detection.
   * @returns a subscription id to pass to removeObserver
   */
  observeQuantityChanges(
    type: string,
    callback: (token: string) => void
  ): Promise<string>

  /**
   * Subscribe to changes for a category type.
   */
  observeCategoryChanges(
    type: string,
    callback: (token: string) => void
  ): Promise<string>

  /**
   * Cancel an observation subscription.
   */
  removeObserver(subscriptionId: string): Promise<void>

  /**
   * Cancel all active observation subscriptions.
   */
  removeAllObservers(): Promise<void>

  /**
   * Start periodic background synchronization.
   * On Android: registers a PeriodicWorkRequest (WorkManager).
   * On iOS: registers a BGTaskScheduler task.
   */
  registerBackgroundSync(config: BackgroundSyncConfig): Promise<void>

  /**
   * Cancel periodic background synchronization and purge stored credentials.
   */
  unregisterBackgroundSync(): Promise<void>

  /**
   * Report whether a background synchronization task is registered.
   */
  isBackgroundSyncRegistered(): Promise<boolean>
}