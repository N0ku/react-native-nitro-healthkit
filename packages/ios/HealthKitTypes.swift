import Foundation
import HealthKit

/// Extension to map HealthKit types
extension HKQuantityTypeIdentifier {
  
  /// Convert string to HKQuantityTypeIdentifier
  static func from(_ string: String) -> HKQuantityTypeIdentifier? {
    switch string {
    // Activity & Fitness
    case "HKQuantityTypeIdentifierStepCount":
      return .stepCount
    case "HKQuantityTypeIdentifierDistanceWalkingRunning":
      return .distanceWalkingRunning
    case "HKQuantityTypeIdentifierDistanceCycling":
      return .distanceCycling
    case "HKQuantityTypeIdentifierDistanceSwimming":
      return .distanceSwimming
    case "HKQuantityTypeIdentifierFlightsClimbed":
      return .flightsClimbed
      
    // Energy & Nutrition
    case "HKQuantityTypeIdentifierActiveEnergyBurned":
      return .activeEnergyBurned
    case "HKQuantityTypeIdentifierBasalEnergyBurned":
      return .basalEnergyBurned
    case "HKQuantityTypeIdentifierDietaryEnergyConsumed":
      return .dietaryEnergyConsumed
      
    // Vital Signs
    case "HKQuantityTypeIdentifierHeartRate":
      return .heartRate
    case "HKQuantityTypeIdentifierRestingHeartRate":
      return .restingHeartRate
    case "HKQuantityTypeIdentifierHeartRateVariabilitySDNN":
      return .heartRateVariabilitySDNN
    case "HKQuantityTypeIdentifierOxygenSaturation":
      return .oxygenSaturation
    case "HKQuantityTypeIdentifierBodyTemperature":
      return .bodyTemperature
    case "HKQuantityTypeIdentifierBloodPressureSystolic":
      return .bloodPressureSystolic
    case "HKQuantityTypeIdentifierBloodPressureDiastolic":
      return .bloodPressureDiastolic
    case "HKQuantityTypeIdentifierRespiratoryRate":
      return .respiratoryRate
      
    // Body Measurements
    case "HKQuantityTypeIdentifierBodyMass":
      return .bodyMass
    case "HKQuantityTypeIdentifierHeight":
      return .height
    case "HKQuantityTypeIdentifierBodyMassIndex":
      return .bodyMassIndex
    case "HKQuantityTypeIdentifierBodyFatPercentage":
      return .bodyFatPercentage
    case "HKQuantityTypeIdentifierLeanBodyMass":
      return .leanBodyMass
    case "HKQuantityTypeIdentifierWaistCircumference":
      return .waistCircumference
      
    // Nutrition
    case "HKQuantityTypeIdentifierDietaryFatTotal":
      return .dietaryFatTotal
    case "HKQuantityTypeIdentifierDietaryFatSaturated":
      return .dietaryFatSaturated
    case "HKQuantityTypeIdentifierDietaryProtein":
      return .dietaryProtein
    case "HKQuantityTypeIdentifierDietaryCarbohydrates":
      return .dietaryCarbohydrates
    case "HKQuantityTypeIdentifierDietarySugar":
      return .dietarySugar
    case "HKQuantityTypeIdentifierDietaryFiber":
      return .dietaryFiber
      
    // Mobility
    case "HKQuantityTypeIdentifierWalkingSpeed":
      return .walkingSpeed
    case "HKQuantityTypeIdentifierWalkingStepLength":
      return .walkingStepLength
    case "HKQuantityTypeIdentifierAppleWalkingSteadiness":
      if #available(iOS 15.0, *) {
        return .appleWalkingSteadiness
      }
      return nil
      
    // VO2 Max
    case "HKQuantityTypeIdentifierVO2Max":
      return .vo2Max
      
    default:
      return nil
    }
  }
  
  /// Return the appropriate unit for this type
  func defaultUnit() -> HKUnit {
    switch self {
    // Steps & Count
    case .stepCount, .flightsClimbed:
      return HKUnit.count()
      
    // Distance
    case .distanceWalkingRunning, .distanceCycling, .distanceSwimming:
      return HKUnit.meter()
      
    // Energy
    case .activeEnergyBurned, .basalEnergyBurned, .dietaryEnergyConsumed:
      return HKUnit.kilocalorie()
      
    // Heart Rate
    case .heartRate, .restingHeartRate:
      return HKUnit.count().unitDivided(by: .minute())
      
    // HRV
    case .heartRateVariabilitySDNN:
      return HKUnit.secondUnit(with: .milli)
      
    // Oxygen Saturation
    case .oxygenSaturation:
      return HKUnit.percent()
      
    // Temperature
    case .bodyTemperature:
      return HKUnit.degreeCelsius()
      
    // Blood Pressure
    case .bloodPressureSystolic, .bloodPressureDiastolic:
      return HKUnit.millimeterOfMercury()
      
    // Respiratory
    case .respiratoryRate:
      return HKUnit.count().unitDivided(by: .minute())
      
    // Body Measurements
    case .bodyMass:
      return HKUnit.gramUnit(with: .kilo)
    case .height:
      return HKUnit.meter()
    case .bodyMassIndex:
      return HKUnit.count()
    case .bodyFatPercentage:
      return HKUnit.percent()
    case .leanBodyMass:
      return HKUnit.gramUnit(with: .kilo)
    case .waistCircumference:
      return HKUnit.meter()
      
    // Nutrition
    case .dietaryFatTotal, .dietaryFatSaturated, .dietaryProtein, .dietaryCarbohydrates, .dietarySugar, .dietaryFiber:
      return HKUnit.gram()
      
    // Walking
    case .walkingSpeed:
      return HKUnit.meter().unitDivided(by: .second())
    case .walkingStepLength:
      return HKUnit.meter()
      
    default:
      return HKUnit.count()
    }
  }
  
  /// Return the appropriate statistics type
  func statisticsOptions() -> HKStatisticsOptions {
    switch self {
    // Types cumulatifs (somme)
    case .stepCount, .distanceWalkingRunning, .distanceCycling, .distanceSwimming,
         .flightsClimbed, .activeEnergyBurned, .basalEnergyBurned, .dietaryEnergyConsumed,
         .dietaryFatTotal, .dietaryFatSaturated, .dietaryProtein, .dietaryCarbohydrates,
         .dietarySugar, .dietaryFiber:
      return .cumulativeSum
      
    // Types discrets (moyenne)
    case .heartRate, .restingHeartRate, .heartRateVariabilitySDNN, .oxygenSaturation,
         .bodyTemperature, .bloodPressureSystolic, .bloodPressureDiastolic, .respiratoryRate,
         .bodyMass, .height, .bodyMassIndex, .bodyFatPercentage, .leanBodyMass,
         .waistCircumference, .walkingSpeed, .walkingStepLength, .vo2Max:
      return .discreteAverage
      
    default:
      return .discreteAverage
    }
  }
}

/// Extension for categories
extension HKCategoryTypeIdentifier {
  
  /// Convert string to HKCategoryTypeIdentifier
  static func from(_ string: String) -> HKCategoryTypeIdentifier? {
    switch string {
    case "HKCategoryTypeIdentifierSleepAnalysis":
      return .sleepAnalysis
    case "HKCategoryTypeIdentifierAppleStandHour":
      return .appleStandHour
    case "HKCategoryTypeIdentifierMindfulSession":
      return .mindfulSession
      
    // Menstruation
    case "HKCategoryTypeIdentifierMenstrualFlow":
      return .menstrualFlow
    case "HKCategoryTypeIdentifierIntermenstrualBleeding":
      return .intermenstrualBleeding
    case "HKCategoryTypeIdentifierSexualActivity":
      return .sexualActivity
      
    // Events
    case "HKCategoryTypeIdentifierLowHeartRateEvent":
      return .lowHeartRateEvent
    case "HKCategoryTypeIdentifierHighHeartRateEvent":
      return .highHeartRateEvent
    case "HKCategoryTypeIdentifierIrregularHeartRhythmEvent":
      return .irregularHeartRhythmEvent
      
    default:
      return nil
    }
  }
}
