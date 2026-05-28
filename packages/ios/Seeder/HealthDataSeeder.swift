import Foundation
import HealthKit

#if DEBUG
class HealthDataSeeder {
    private let healthStore: HKHealthStore
    
    // Workout types to seed
    private let workoutTypes: [HKWorkoutActivityType] = [
        .running, .walking, .cycling, .swimming, .yoga,
        .functionalStrengthTraining, .hiking, .elliptical
    ]
    
    init(healthStore: HKHealthStore) {
        self.healthStore = healthStore
    }
    
    func seedData() async throws {
        print("🌱 Starting HealthKit data seeding...")
        
        // 1. Seed Steps (Last 2 years)
        do {
            try await seedSteps()
        } catch {
            print("   ⚠️ Failed to seed steps: \(error.localizedDescription)")
        }
        
        // 2. Seed Heart Rate (Last 2 years)
        do {
            try await seedHeartRate()
        } catch {
            print("   ⚠️ Failed to seed heart rate: \(error.localizedDescription)")
        }
        
        // 3. Seed Workouts (Last 2 years)
        do {
            try await seedWorkouts()
        } catch {
            print("   ⚠️ Failed to seed workouts: \(error.localizedDescription)")
        }
        
        // 4. Seed Active Energy Burned
        do {
            try await seedActiveEnergy()
        } catch {
            print("   ⚠️ Failed to seed active energy: \(error.localizedDescription)")
        }
        
        // 5. Seed Distance
        do {
            try await seedDistance()
        } catch {
            print("   ⚠️ Failed to seed distance: \(error.localizedDescription)")
        }
        
        print("✅ HealthKit data seeding completed!")
    }
    
    private func seedSteps() async throws {
        guard let stepType = HKQuantityType.quantityType(forIdentifier: .stepCount) else { return }
        
        var samples: [HKQuantitySample] = []
        let now = Date()
        let calendar = Calendar.current
        
        // Generate data for the last 2 years (approx 730 days)
        for dayOffset in 0..<730 {
            guard let date = calendar.date(byAdding: .day, value: -dayOffset, to: now) else { continue }
            
            // Generate 5-10 entries per day
            for _ in 0..<Int.random(in: 5...10) {
                let randomHour = Int.random(in: 8...20)
                let randomMinute = Int.random(in: 0...59)
                
                var components = calendar.dateComponents([.year, .month, .day], from: date)
                components.hour = randomHour
                components.minute = randomMinute
                
                guard let startDate = calendar.date(from: components),
                      let endDate = calendar.date(byAdding: .minute, value: 15, to: startDate) else { continue }
                
                let steps = Double.random(in: 100...1000)
                let quantity = HKQuantity(unit: .count(), doubleValue: steps)
                let sample = HKQuantitySample(type: stepType, quantity: quantity, start: startDate, end: endDate)
                samples.append(sample)
            }
        }
        
        try await healthStore.save(samples)
        print("   ✅ Seeded \(samples.count) step samples")
    }
    
    private func seedHeartRate() async throws {
        guard let hrType = HKQuantityType.quantityType(forIdentifier: .heartRate) else { return }
        
        var samples: [HKQuantitySample] = []
        let now = Date()
        let calendar = Calendar.current
        
        // Generate data for the last 2 years
        for dayOffset in 0..<730 {
            guard let date = calendar.date(byAdding: .day, value: -dayOffset, to: now) else { continue }
            
            // Generate 3-5 samples per day
            for _ in 0..<Int.random(in: 3...5) {
                let randomHour = Int.random(in: 0...23)
                let randomMinute = Int.random(in: 0...59)
                
                var components = calendar.dateComponents([.year, .month, .day], from: date)
                components.hour = randomHour
                components.minute = randomMinute
                
                guard let sampleDate = calendar.date(from: components) else { continue }
                
                let hr = Double.random(in: 60...100)
                let quantity = HKQuantity(unit: HKUnit.count().unitDivided(by: .minute()), doubleValue: hr)
                let sample = HKQuantitySample(type: hrType, quantity: quantity, start: sampleDate, end: sampleDate)
                samples.append(sample)
            }
        }
        
        try await healthStore.save(samples)
        print("   ✅ Seeded \(samples.count) heart rate samples")
    }
    
    private func seedActiveEnergy() async throws {
        guard let energyType = HKQuantityType.quantityType(forIdentifier: .activeEnergyBurned) else { return }
        
        var samples: [HKQuantitySample] = []
        let now = Date()
        let calendar = Calendar.current
        
        for dayOffset in 0..<730 {
            guard let date = calendar.date(byAdding: .day, value: -dayOffset, to: now) else { continue }
            
            // Generate 3-6 energy entries per day
            for _ in 0..<Int.random(in: 3...6) {
                let randomHour = Int.random(in: 6...22)
                
                var components = calendar.dateComponents([.year, .month, .day], from: date)
                components.hour = randomHour
                components.minute = Int.random(in: 0...59)
                
                guard let startDate = calendar.date(from: components),
                      let endDate = calendar.date(byAdding: .minute, value: 30, to: startDate) else { continue }
                
                let energy = Double.random(in: 50...200)
                let quantity = HKQuantity(unit: .kilocalorie(), doubleValue: energy)
                let sample = HKQuantitySample(type: energyType, quantity: quantity, start: startDate, end: endDate)
                samples.append(sample)
            }
        }
        
        try await healthStore.save(samples)
        print("   ✅ Seeded \(samples.count) active energy samples")
    }
    
    private func seedDistance() async throws {
        guard let distanceType = HKQuantityType.quantityType(forIdentifier: .distanceWalkingRunning) else { return }
        
        var samples: [HKQuantitySample] = []
        let now = Date()
        let calendar = Calendar.current
        
        for dayOffset in 0..<730 {
            guard let date = calendar.date(byAdding: .day, value: -dayOffset, to: now) else { continue }
            
            // Generate 4-8 distance entries per day
            for _ in 0..<Int.random(in: 4...8) {
                let randomHour = Int.random(in: 7...21)
                
                var components = calendar.dateComponents([.year, .month, .day], from: date)
                components.hour = randomHour
                components.minute = Int.random(in: 0...59)
                
                guard let startDate = calendar.date(from: components),
                      let endDate = calendar.date(byAdding: .minute, value: 20, to: startDate) else { continue }
                
                let distance = Double.random(in: 100...2000) // meters
                let quantity = HKQuantity(unit: .meter(), doubleValue: distance)
                let sample = HKQuantitySample(type: distanceType, quantity: quantity, start: startDate, end: endDate)
                samples.append(sample)
            }
        }
        
        try await healthStore.save(samples)
        print("   ✅ Seeded \(samples.count) distance samples")
    }
    
    private func seedWorkouts() async throws {
        let now = Date()
        let calendar = Calendar.current
        var workoutCount = 0
        
        // Generate workouts for the last 2 years
        for dayOffset in 0..<730 {
            // 30% chance of workout per day
            guard Double.random(in: 0...1) <= 0.3 else { continue }
            
            guard let date = calendar.date(byAdding: .day, value: -dayOffset, to: now) else { continue }
            
            let randomHour = Int.random(in: 7...20)
            
            var components = calendar.dateComponents([.year, .month, .day], from: date)
            components.hour = randomHour
            components.minute = 0
            components.second = 0
            
            guard let startDate = calendar.date(from: components) else { continue }
            
            let durationMinutes = Int.random(in: 20...60)
            guard let endDate = calendar.date(byAdding: .minute, value: durationMinutes, to: startDate) else { continue }
            
            // Pick a random workout type
            let activityType = workoutTypes.randomElement() ?? .running
            
            do {
                try await createWorkout(
                    activityType: activityType,
                    start: startDate,
                    end: endDate,
                    energyBurned: Double.random(in: 150...500),
                    distance: Double.random(in: 2000...10000)
                )
                workoutCount += 1
            } catch {
                // Continue even if one workout fails
                continue
            }
        }
        
        print("   ✅ Seeded \(workoutCount) workouts")
    }
    
    private func createWorkout(
        activityType: HKWorkoutActivityType,
        start: Date,
        end: Date,
        energyBurned: Double,
        distance: Double
    ) async throws {
        let configuration = HKWorkoutConfiguration()
        configuration.activityType = activityType
        configuration.locationType = .outdoor
        
        let builder = HKWorkoutBuilder(healthStore: healthStore, configuration: configuration, device: nil)
        
        try await builder.beginCollection(at: start)
        
        // Add energy burned sample
        if let energyType = HKQuantityType.quantityType(forIdentifier: .activeEnergyBurned) {
            let energyQuantity = HKQuantity(unit: .kilocalorie(), doubleValue: energyBurned)
            let energySample = HKQuantitySample(type: energyType, quantity: energyQuantity, start: start, end: end)
            try await builder.addSamples([energySample])
        }
        
        // Add distance sample for applicable workout types
        if activityType == .running || activityType == .walking || activityType == .cycling || activityType == .hiking {
            if let distanceType = HKQuantityType.quantityType(forIdentifier: .distanceWalkingRunning) {
                let distanceQuantity = HKQuantity(unit: .meter(), doubleValue: distance)
                let distanceSample = HKQuantitySample(type: distanceType, quantity: distanceQuantity, start: start, end: end)
                try await builder.addSamples([distanceSample])
            }
        }
        
        try await builder.endCollection(at: end)
        
        // Finish and save the workout
        _ = try await builder.finishWorkout()
    }
}
#endif
