// Mock for react-native-nitro-modules
const NitroModules = {
  createHybridObject: jest.fn((moduleName) => {
    // Return a mock object with common HealthKit methods
    return {
      // Availability and Authorization
      isHealthKitAvailable: jest.fn().mockResolvedValue(true),
      requestAuthorization: jest.fn().mockResolvedValue(true),
      
      // Legacy Methods
      getSteps: jest.fn().mockResolvedValue(0),
      getHeartRate: jest.fn().mockResolvedValue(0),
      getDistance: jest.fn().mockResolvedValue(0),
      getActiveEnergyBurned: jest.fn().mockResolvedValue(0),
      getHealthData: jest.fn().mockResolvedValue({
        steps: 0,
        heartRate: 0,
        distance: 0,
        activeEnergyBurned: 0,
      }),
      
      // New Quantity Data Methods
      getQuantityData: jest.fn().mockResolvedValue([]),
      getAggregatedQuantity: jest.fn().mockResolvedValue(0),
      
      // Category Data Methods
      getCategoryData: jest.fn().mockResolvedValue([]),
      getSleepAnalysis: jest.fn().mockResolvedValue([]),
      
      // Workout Methods
      getWorkouts: jest.fn().mockResolvedValue([]),
      
      // Activity Summary
      getActivitySummary: jest.fn().mockResolvedValue({}),
      
      // Cache Methods
      clearCache: jest.fn().mockResolvedValue(undefined),
      clearAllCache: jest.fn().mockResolvedValue(undefined),
      
      // Legacy Sample Methods
      getQuantitySamples: jest.fn().mockResolvedValue([]),
      getCategorySamples: jest.fn().mockResolvedValue([]),
    };
  }),
};

module.exports = {
  NitroModules,
};
