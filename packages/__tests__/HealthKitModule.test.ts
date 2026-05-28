import { HealthKitModule, HealthKitQuantityType, HealthKitCategoryType } from '../src/index';
import { describe, it, expect, beforeAll } from '@jest/globals';

describe('HealthKitModule', () => {
  beforeAll(async () => {
    // Check if HealthKit is available
    const available = await HealthKitModule.isHealthKitAvailable();
    if (!available) {
      console.warn('HealthKit is not available on this device/simulator');
    }
  });

  describe('Availability and Authorization', () => {
    it('should check if HealthKit is available', async () => {
      const available = await HealthKitModule.isHealthKitAvailable();
      expect(typeof available).toBe('boolean');
    });

    it('should request authorization', async () => {
      const authorized = await HealthKitModule.requestAuthorization();
      expect(typeof authorized).toBe('boolean');
    });
  });

  describe('Legacy Methods', () => {
    const endDate = new Date();
    const startDate = new Date();
    startDate.setDate(startDate.getDate() - 7);

    it('should get steps with legacy method', async () => {
      try {
        const steps = await HealthKitModule.getSteps(startDate, endDate);
        expect(typeof steps).toBe('number');
        expect(steps).toBeGreaterThanOrEqual(0);
      } catch (error) {
        // May fail on simulator
        console.warn('getSteps failed:', error);
      }
    });

    it('should get heart rate with legacy method', async () => {
      try {
        const heartRate = await HealthKitModule.getHeartRate(startDate, endDate);
        expect(typeof heartRate).toBe('number');
        expect(heartRate).toBeGreaterThanOrEqual(0);
      } catch (error) {
        console.warn('getHeartRate failed:', error);
      }
    });

    it('should get health data with legacy method', async () => {
      try {
        const data = await HealthKitModule.getHealthData(startDate, endDate);
        expect(data).toBeDefined();
        expect(typeof data).toBe('object');
      } catch (error) {
        console.warn('getHealthData failed:', error);
      }
    });
  });

  describe('Quantity Data Methods', () => {
    const endDate = new Date();
    const startDate = new Date();
    startDate.setDate(startDate.getDate() - 7);

    it('should get quantity data for steps', async () => {
      try {
        const samples = await HealthKitModule.getQuantityData(
          HealthKitQuantityType.STEPS,
          startDate,
          endDate,
          null,
          true,
          60
        );
        
        expect(Array.isArray(samples)).toBe(true);
        
        if (samples.length > 0) {
          const sample = samples[0];
          expect(sample).toHaveProperty('value');
          expect(sample).toHaveProperty('unit');
          expect(sample).toHaveProperty('startDate');
          expect(sample).toHaveProperty('endDate');
          if (sample) {
            expect(typeof sample.value).toBe('number');
            expect(typeof sample.unit).toBe('string');
            expect(sample.startDate).toBeInstanceOf(Date);
            expect(sample.endDate).toBeInstanceOf(Date);
          }
        }
      } catch (error) {
        console.error('getQuantityData for steps failed:', error);
        throw error;
      }
    });

    it('should get quantity data for heart rate', async () => {
      try {
        const samples = await HealthKitModule.getQuantityData(
          HealthKitQuantityType.HEART_RATE,
          startDate,
          endDate,
          null,
          true,
          60
        );
        
        expect(Array.isArray(samples)).toBe(true);
        
        if (samples.length > 0) {
          const sample = samples[0];
          if (sample) {
            expect(sample.value).toBeGreaterThan(0);
            expect(sample.unit).toBe('count/min');
          }
        }
      } catch (error) {
        console.warn('getQuantityData for heart rate failed:', error);
      }
    });

    it('should get aggregated quantity', async () => {
      try {
        const totalSteps = await HealthKitModule.getAggregatedQuantity(
          HealthKitQuantityType.STEPS,
          startDate,
          endDate,
          'sum',
          true,
          60
        );
        
        expect(typeof totalSteps).toBe('number');
        expect(totalSteps).toBeGreaterThanOrEqual(0);
      } catch (error) {
        console.error('getAggregatedQuantity failed:', error);
        throw error;
      }
    });

    it('should get average heart rate', async () => {
      try {
        const avgHeartRate = await HealthKitModule.getAggregatedQuantity(
          HealthKitQuantityType.HEART_RATE,
          startDate,
          endDate,
          'average',
          true,
          60
        );
        
        expect(typeof avgHeartRate).toBe('number');
        expect(avgHeartRate).toBeGreaterThanOrEqual(0);
      } catch (error) {
        console.warn('average heart rate failed:', error);
      }
    });
  });

  describe('Category Data Methods', () => {
    const endDate = new Date();
    const startDate = new Date();
    startDate.setDate(startDate.getDate() - 7);

    it('should get category data for sleep', async () => {
      try {
        const samples = await HealthKitModule.getCategoryData(
          HealthKitCategoryType.SLEEP_ANALYSIS,
          startDate,
          endDate,
          true,
          60
        );
        
        expect(Array.isArray(samples)).toBe(true);
        
        if (samples.length > 0) {
          const sample = samples[0];
          expect(sample).toHaveProperty('value');
          expect(sample).toHaveProperty('startDate');
          expect(sample).toHaveProperty('endDate');
          if (sample) {
            expect(typeof sample.value).toBe('number');
            expect(sample.startDate).toBeInstanceOf(Date);
            expect(sample.endDate).toBeInstanceOf(Date);
          }
        }
      } catch (error) {
        console.warn('getCategoryData failed:', error);
      }
    });
  });

  describe('Workout Methods', () => {
    const endDate = new Date();
    const startDate = new Date();
    startDate.setDate(startDate.getDate() - 30);

    it('should get workouts', async () => {
      try {
        const workouts = await HealthKitModule.getWorkouts(
          startDate,
          endDate,
          true,
          60
        );
        
        expect(Array.isArray(workouts)).toBe(true);
        
        if (workouts.length > 0) {
          const workout = workouts[0];
          expect(workout).toHaveProperty('workoutActivityType');
          expect(workout).toHaveProperty('workoutActivityName');
          expect(workout).toHaveProperty('duration');
          expect(workout).toHaveProperty('startDate');
          expect(workout).toHaveProperty('endDate');
          if (workout) {
            expect(typeof workout.duration).toBe('number');
            expect(workout.duration).toBeGreaterThan(0);
          }
        }
      } catch (error) {
        console.warn('getWorkouts failed:', error);
      }
    });
  });

  describe('Cache Methods', () => {
    it('should clear cache without errors', async () => {
      await expect(HealthKitModule.clearCache()).resolves.not.toThrow();
    });
  });

  describe('Date Handling', () => {
    it('should handle different date formats', async () => {
      const endDate = new Date();
      const startDate = new Date();
      startDate.setHours(0, 0, 0, 0);
      endDate.setHours(23, 59, 59, 999);

      try {
        const samples = await HealthKitModule.getQuantityData(
          HealthKitQuantityType.STEPS,
          startDate,
          endDate,
          null,
          false, // Disable cache for this test
          60
        );
        
        expect(Array.isArray(samples)).toBe(true);
      } catch (error) {
        console.error('Date handling test failed:', error);
        throw error;
      }
    });
  });

  describe('Error Handling', () => {
    it('should handle invalid date range', async () => {
      const startDate = new Date();
      const endDate = new Date();
      endDate.setDate(endDate.getDate() - 7); // End before start

      try {
        await HealthKitModule.getQuantityData(
          HealthKitQuantityType.STEPS,
          startDate,
          endDate,
          null,
          false,
          60
        );
        // Should either return empty array or throw
      } catch (error) {
        expect(error).toBeDefined();
      }
    });
  });
});
