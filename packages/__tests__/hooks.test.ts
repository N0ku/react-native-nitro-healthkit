import { describe, it, expect, jest, beforeEach } from '@jest/globals';
import { HealthKitQuantityType, HealthKitCategoryType, TimeRange } from '../src/specs/HealthKit.nitro';
import type { HealthKit } from '../src/specs/HealthKit.nitro';

// Mock React hooks
const mockSetState = jest.fn();
const mockUseState = jest.fn((initial: any) => [initial, mockSetState]);
const mockUseEffect = jest.fn((fn: any) => fn());
const mockUseCallback = jest.fn((fn: any) => fn);

jest.mock('react', () => ({
  useState: mockUseState,
  useEffect: mockUseEffect,
  useCallback: mockUseCallback,
}));

// Import hooks after mocking React
import {
  useHealthKitQuantity,
  useHealthKitCategory,
  useHealthKitRealtime,
  useHealthKitData,
  useHealthKitAuthorization,
} from '../src/hooks';

describe('HealthKit Hooks', () => {
  let mockHealthKit: jest.Mocked<HealthKit>;

  beforeEach(() => {
    jest.clearAllMocks();
    
    // Create mock HealthKit instance
    mockHealthKit = {
      isHealthKitAvailable: jest.fn<() => Promise<boolean>>().mockResolvedValue(true),
      requestAuthorization: jest.fn<() => Promise<boolean>>().mockResolvedValue(true),
      checkAuthorizationStatus: jest.fn<(type: string) => Promise<string>>().mockResolvedValue('sharingAuthorized'),
      getAggregatedQuantity: jest.fn<() => Promise<number>>().mockResolvedValue(1000),
      getCategoryData: jest.fn<() => Promise<any[]>>().mockResolvedValue([]),
      getHealthDataForTimeRange: jest.fn<() => Promise<any>>().mockResolvedValue({ steps: 10000 }),
      getSteps: jest.fn<() => Promise<number>>().mockResolvedValue(5000),
      getHeartRate: jest.fn<() => Promise<number>>().mockResolvedValue(72),
      getHealthData: jest.fn<() => Promise<any>>().mockResolvedValue({}),
      getQuantityData: jest.fn<() => Promise<any[]>>().mockResolvedValue([]),
      getWorkouts: jest.fn<() => Promise<any[]>>().mockResolvedValue([]),
      clearCache: jest.fn<() => Promise<void>>().mockResolvedValue(undefined),
    } as any;
  });

  describe('useHealthKitQuantity', () => {
    it('should initialize the hook with correct parameters', () => {
      useHealthKitQuantity(
        mockHealthKit,
        HealthKitQuantityType.STEPS,
        TimeRange.TODAY,
        'sum',
        true,
        60,
        0
      );

      expect(mockUseState).toHaveBeenCalledWith(null); // data
      expect(mockUseState).toHaveBeenCalledWith(true); // loading
      expect(mockUseState).toHaveBeenCalledWith(null); // error
      expect(mockUseCallback).toHaveBeenCalled();
      expect(mockUseEffect).toHaveBeenCalled();
    });

    it('should call getAggregatedQuantity with correct date range for TODAY', async () => {
      useHealthKitQuantity(
        mockHealthKit,
        HealthKitQuantityType.STEPS,
        TimeRange.TODAY,
        'sum',
        true,
        60,
        0
      );

      // Execute the useCallback function
      const fetchData = mockUseCallback.mock.calls[0]?.[0] as () => Promise<void>;
      await fetchData();

      expect(mockHealthKit.getAggregatedQuantity).toHaveBeenCalledWith(
        HealthKitQuantityType.STEPS,
        expect.any(Date),
        expect.any(Date),
        'sum',
        true,
        60
      );
    });

    it('should handle different aggregation types', async () => {
      const aggregationTypes: Array<'sum' | 'average' | 'min' | 'max'> = ['sum', 'average', 'min', 'max'];

      for (const aggregationType of aggregationTypes) {
        jest.clearAllMocks();
        
        useHealthKitQuantity(
          mockHealthKit,
          HealthKitQuantityType.HEART_RATE,
          TimeRange.TODAY,
          aggregationType,
          true,
          60,
          0
        );

        const fetchData = mockUseCallback.mock.calls[0]?.[0] as () => Promise<void>;
        await fetchData();

        expect(mockHealthKit.getAggregatedQuantity).toHaveBeenCalledWith(
          HealthKitQuantityType.HEART_RATE,
          expect.any(Date),
          expect.any(Date),
          aggregationType,
          true,
          60
        );
      }
    });

    it('should handle all time ranges correctly', async () => {
      const timeRanges = [
        TimeRange.TODAY,
        TimeRange.YESTERDAY,
        TimeRange.THIS_WEEK,
        TimeRange.LAST_7_DAYS,
        TimeRange.THIS_MONTH,
        TimeRange.LAST_30_DAYS,
      ];

      for (const timeRange of timeRanges) {
        jest.clearAllMocks();
        
        useHealthKitQuantity(
          mockHealthKit,
          HealthKitQuantityType.STEPS,
          timeRange,
          'sum',
          true,
          60,
          0
        );

        const fetchData = mockUseCallback.mock.calls[0]?.[0] as () => Promise<void>;
        await fetchData();

        expect(mockHealthKit.getAggregatedQuantity).toHaveBeenCalled();
      }
    });
  });

  describe('useHealthKitCategory', () => {
    it('should initialize the hook with correct parameters', () => {
      useHealthKitCategory(
        mockHealthKit,
        HealthKitCategoryType.SLEEP_ANALYSIS,
        TimeRange.TODAY,
        true,
        60
      );

      expect(mockUseState).toHaveBeenCalledWith(null); // data
      expect(mockUseState).toHaveBeenCalledWith(true); // loading
      expect(mockUseState).toHaveBeenCalledWith(null); // error
      expect(mockUseCallback).toHaveBeenCalled();
      expect(mockUseEffect).toHaveBeenCalled();
    });

    it('should call getCategoryData with correct parameters', async () => {
      useHealthKitCategory(
        mockHealthKit,
        HealthKitCategoryType.SLEEP_ANALYSIS,
        TimeRange.TODAY,
        true,
        60
      );

      const fetchData = mockUseCallback.mock.calls[0]?.[0] as () => Promise<void>;
      await fetchData();

      expect(mockHealthKit.getCategoryData).toHaveBeenCalledWith(
        HealthKitCategoryType.SLEEP_ANALYSIS,
        expect.any(Date),
        expect.any(Date),
        true,
        60
      );
    });
  });

  describe('useHealthKitRealtime', () => {
    it('should initialize the hook with correct parameters', () => {
      const types = [HealthKitQuantityType.STEPS, HealthKitQuantityType.HEART_RATE];
      
      useHealthKitRealtime(
        mockHealthKit,
        types,
        true,
        30,
        60000
      );

      expect(mockUseState).toHaveBeenCalledWith({}); // data
      expect(mockUseState).toHaveBeenCalledWith(true); // loading
      expect(mockUseState).toHaveBeenCalledWith(null); // error
      expect(mockUseCallback).toHaveBeenCalled();
      expect(mockUseEffect).toHaveBeenCalled();
    });

    it('should fan out to getAggregatedQuantity per type', async () => {
      const types = [HealthKitQuantityType.STEPS, HealthKitQuantityType.HEART_RATE];

      useHealthKitRealtime(
        mockHealthKit,
        types,
        true,
        30,
        0
      );

      const fetchData = mockUseCallback.mock.calls[0]?.[0] as () => Promise<void>;
      await fetchData();

      for (const type of types) {
        expect(mockHealthKit.getAggregatedQuantity).toHaveBeenCalledWith(
          type,
          expect.any(Date),
          expect.any(Date),
          'sum',
          true,
          30
        );
      }
    });

    it('should handle empty types array', async () => {
      useHealthKitRealtime(
        mockHealthKit,
        [],
        true,
        30,
        0
      );

      const fetchData = mockUseCallback.mock.calls[0]?.[0] as () => Promise<void>;
      await fetchData();

      expect(mockHealthKit.getAggregatedQuantity).not.toHaveBeenCalled();
    });
  });

  describe('useHealthKitData', () => {
    it('should initialize the hook with correct parameters', () => {
      useHealthKitData(
        mockHealthKit,
        TimeRange.TODAY,
        undefined,
        undefined,
        true,
        60
      );

      expect(mockUseState).toHaveBeenCalledWith(null); // data
      expect(mockUseState).toHaveBeenCalledWith(true); // loading
      expect(mockUseState).toHaveBeenCalledWith(null); // error
      expect(mockUseCallback).toHaveBeenCalled();
      expect(mockUseEffect).toHaveBeenCalled();
    });

    it('should call getHealthDataForTimeRange with correct parameters', async () => {
      useHealthKitData(
        mockHealthKit,
        TimeRange.LAST_7_DAYS,
        undefined,
        undefined,
        true,
        60
      );

      const fetchData = mockUseCallback.mock.calls[0]?.[0] as () => Promise<void>;
      await fetchData();

      expect(mockHealthKit.getHealthDataForTimeRange).toHaveBeenCalledWith(
        TimeRange.LAST_7_DAYS,
        null,
        null,
        true,
        60
      );
    });

    it('should use custom dates when provided', async () => {
      const startDate = new Date('2026-01-01');
      const endDate = new Date('2026-01-31');

      useHealthKitData(
        mockHealthKit,
        TimeRange.CUSTOM,
        startDate,
        endDate,
        true,
        60
      );

      const fetchData = mockUseCallback.mock.calls[0]?.[0] as () => Promise<void>;
      await fetchData();

      expect(mockHealthKit.getHealthDataForTimeRange).toHaveBeenCalledWith(
        TimeRange.CUSTOM,
        startDate,
        endDate,
        true,
        60
      );
    });
  });

  describe('useHealthKitAuthorization', () => {
    it('should initialize the hook with correct parameters', () => {
      useHealthKitAuthorization(mockHealthKit);

      expect(mockUseState).toHaveBeenCalledWith(false); // isAuthorized
      expect(mockUseState).toHaveBeenCalledWith(true); // loading
      expect(mockUseState).toHaveBeenCalledWith(null); // error
      expect(mockUseCallback).toHaveBeenCalled();
      expect(mockUseEffect).toHaveBeenCalled();
    });

    it('should call requestAuthorization', async () => {
      useHealthKitAuthorization(mockHealthKit);

      // The requestAuthorization callback is the first one
      const requestAuthCallback = mockUseCallback.mock.calls[0]?.[0] as () => Promise<boolean>;
      const result = await requestAuthCallback();

      expect(mockHealthKit.requestAuthorization).toHaveBeenCalled();
      expect(result).toBe(true);
    });

    it('should check authorization status', async () => {
      useHealthKitAuthorization(mockHealthKit);

      // The checkStatus callback is the second one
      const checkStatusCallback = mockUseCallback.mock.calls[1]?.[0] as (type: string) => Promise<string>;
      const result = await checkStatusCallback(HealthKitQuantityType.STEPS);

      expect(mockHealthKit.checkAuthorizationStatus).toHaveBeenCalledWith(
        HealthKitQuantityType.STEPS
      );
      expect(result).toBe('sharingAuthorized');
    });

    it('should handle authorization errors', async () => {
      mockHealthKit.requestAuthorization.mockRejectedValue(new Error('Authorization failed'));

      useHealthKitAuthorization(mockHealthKit);

      const requestAuthCallback = mockUseCallback.mock.calls[0]?.[0] as () => Promise<boolean>;
      const result = await requestAuthCallback();

      expect(result).toBe(false);
    });

    it('should check HealthKit availability on mount', async () => {
      useHealthKitAuthorization(mockHealthKit);

      // The useEffect callback is executed
      const effectCallback = mockUseEffect.mock.calls[0]?.[0] as () => Promise<void>;
      await effectCallback();

      expect(mockHealthKit.isHealthKitAvailable).toHaveBeenCalled();
    });
  });

  describe('Error Handling', () => {
    it('should handle errors in useHealthKitQuantity', async () => {
      const error = new Error('Failed to fetch quantity data');
      mockHealthKit.getAggregatedQuantity.mockRejectedValue(error);

      useHealthKitQuantity(
        mockHealthKit,
        HealthKitQuantityType.STEPS,
        TimeRange.TODAY,
        'sum',
        true,
        60,
        0
      );

      const fetchData = mockUseCallback.mock.calls[0]?.[0] as () => Promise<void>;
      await fetchData();

      // Verify the error was caught (setState should have been called with the error)
      expect(mockHealthKit.getAggregatedQuantity).toHaveBeenCalled();
    });

    it('should handle errors in useHealthKitCategory', async () => {
      const error = new Error('Failed to fetch category data');
      mockHealthKit.getCategoryData.mockRejectedValue(error);

      useHealthKitCategory(
        mockHealthKit,
        HealthKitCategoryType.SLEEP_ANALYSIS,
        TimeRange.TODAY,
        true,
        60
      );

      const fetchData = mockUseCallback.mock.calls[0]?.[0] as () => Promise<void>;
      await fetchData();

      expect(mockHealthKit.getCategoryData).toHaveBeenCalled();
    });

    it('should handle errors in useHealthKitRealtime', async () => {
      const error = new Error('Failed to fetch realtime data');
      mockHealthKit.getAggregatedQuantity.mockRejectedValue(error);

      useHealthKitRealtime(
        mockHealthKit,
        [HealthKitQuantityType.STEPS],
        true,
        30,
        0
      );

      const fetchData = mockUseCallback.mock.calls[0]?.[0] as () => Promise<void>;
      await fetchData();

      // A rejection now propagates instead of being silently coerced to 0.
      expect(mockHealthKit.getAggregatedQuantity).toHaveBeenCalled();
    });
  });

  describe('Cache Configuration', () => {
    it('should respect cache settings in useHealthKitQuantity', async () => {
      useHealthKitQuantity(
        mockHealthKit,
        HealthKitQuantityType.STEPS,
        TimeRange.TODAY,
        'sum',
        false, // Cache disabled
        120,
        0
      );

      const fetchData = mockUseCallback.mock.calls[0]?.[0] as () => Promise<void>;
      await fetchData();

      expect(mockHealthKit.getAggregatedQuantity).toHaveBeenCalledWith(
        expect.anything(),
        expect.anything(),
        expect.anything(),
        expect.anything(),
        false,
        120
      );
    });

    it('should use shorter cache TTL for realtime data', async () => {
      useHealthKitRealtime(
        mockHealthKit,
        [HealthKitQuantityType.STEPS],
        true,
        15, // Shorter TTL
        0
      );

      const fetchData = mockUseCallback.mock.calls[0]?.[0] as () => Promise<void>;
      await fetchData();

      expect(mockHealthKit.getAggregatedQuantity).toHaveBeenCalledWith(
        expect.anything(),
        expect.any(Date),
        expect.any(Date),
        'sum',
        true,
        15
      );
    });
  });
});
