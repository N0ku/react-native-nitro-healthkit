import { useCallback, useEffect, useState } from 'react'
import type { HealthKit, CategoryDataPoint, HealthData } from './specs/Example.nitro'
import { HealthKitQuantityType, HealthKitCategoryType, TimeRange } from './specs/Example.nitro'

/** Local midnight of the given date, without mutating it. */
function startOfDay(d: Date): Date {
  return new Date(d.getFullYear(), d.getMonth(), d.getDate())
}

/** `n` days before the given date at the same time of day, without mutating it. */
function daysAgo(d: Date, n: number): Date {
  return new Date(
    d.getFullYear(),
    d.getMonth(),
    d.getDate() - n,
    d.getHours(),
    d.getMinutes(),
    d.getSeconds(),
    d.getMilliseconds()
  )
}

/**
 * Hook to fetch HealthKit quantity data
 * @param type Quantity type to fetch
 * @param timeRange Time period
 * @param aggregationType Aggregation type (sum, average, min, max)
 * @param enableCache Enable cache
 * @param cacheTTL Cache time-to-live in seconds (default: 60)
 * @param autoRefresh Auto-refresh interval in ms (0 = disabled)
 */
export function useHealthKitQuantity(
  healthKit: HealthKit,
  type: HealthKitQuantityType,
  timeRange: TimeRange = TimeRange.TODAY,
  aggregationType: 'sum' | 'average' | 'min' | 'max' = 'sum',
  enableCache: boolean = true,
  cacheTTL: number = 60,
  autoRefresh: number = 0
) {
  const [data, setData] = useState<number | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<Error | null>(null)

  const fetchData = useCallback(async () => {
    try {
      setLoading(true)
      setError(null)

      // Get dates for the period. `now` is never mutated so each branch derives
      // its bounds from a stable reference.
      const now = new Date()
      let startDate: Date
      let endDate: Date = now

      switch (timeRange) {
        case TimeRange.TODAY:
          startDate = startOfDay(now)
          break
        case TimeRange.YESTERDAY: {
          const yesterday = daysAgo(now, 1)
          startDate = startOfDay(yesterday)
          endDate = new Date(yesterday.getFullYear(), yesterday.getMonth(), yesterday.getDate(), 23, 59, 59, 999)
          break
        }
        case TimeRange.THIS_WEEK:
          startDate = new Date(now.getFullYear(), now.getMonth(), now.getDate() - now.getDay())
          break
        case TimeRange.LAST_7_DAYS:
          startDate = daysAgo(now, 7)
          break
        case TimeRange.THIS_MONTH:
          startDate = new Date(now.getFullYear(), now.getMonth(), 1)
          break
        case TimeRange.LAST_30_DAYS:
          startDate = daysAgo(now, 30)
          break
        default:
          startDate = startOfDay(now)
      }

      const value = await healthKit.getAggregatedQuantity(
        type,
        startDate,
        endDate,
        aggregationType,
        enableCache,
        cacheTTL
      )

      setData(value)
    } catch (err) {
      setError(err as Error)
    } finally {
      setLoading(false)
    }
  }, [healthKit, type, timeRange, aggregationType, enableCache, cacheTTL])

  useEffect(() => {
    fetchData()

    // Auto-refresh if enabled
    if (autoRefresh > 0) {
      const interval = setInterval(fetchData, autoRefresh)
      return () => clearInterval(interval)
    }
    
    return undefined
  }, [fetchData, autoRefresh])

  return { data, loading, error, refetch: fetchData }
}

/**
 * Hook to fetch HealthKit category data
 */
export function useHealthKitCategory(
  healthKit: HealthKit,
  type: HealthKitCategoryType,
  timeRange: TimeRange = TimeRange.TODAY,
  enableCache: boolean = true,
  cacheTTL: number = 60
) {
  const [data, setData] = useState<CategoryDataPoint[] | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<Error | null>(null)

  const fetchData = useCallback(async () => {
    try {
      setLoading(true)
      setError(null)

      // Extract specific category data
      const categoryData = await healthKit.getCategoryData(
        type,
        startOfDay(new Date()),
        new Date(),
        enableCache,
        cacheTTL
      )

      setData(categoryData)
    } catch (err) {
      setError(err as Error)
    } finally {
      setLoading(false)
    }
  }, [healthKit, type, timeRange, enableCache, cacheTTL])

  useEffect(() => {
    fetchData()
  }, [fetchData])

  return { data, loading, error, refetch: fetchData }
}

/**
 * Hook to fetch multiple metrics in real-time
 * @param types List of data types to fetch
 * @param enableCache Enable cache
 * @param cacheTTL Cache time-to-live in seconds
 * @param refreshInterval Refresh interval in ms (0 = manual only)
 */
export function useHealthKitRealtime(
  healthKit: HealthKit,
  types: HealthKitQuantityType[],
  enableCache: boolean = true,
  cacheTTL: number = 30, // Shorter cache for real-time data
  refreshInterval: number = 60000 // 1 minute by default
) {
  const [data, setData] = useState<Record<string, number>>({})
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<Error | null>(null)

  const fetchData = useCallback(async () => {
    try {
      setLoading(true)
      setError(null)

      // Fan out per metric — getRealtimeData was dropped because fbjni can't
      // marshal `Map<JString, double>` on Android. Same effect for the caller.
      // A rejection propagates to the outer catch so `error` is surfaced rather
      // than silently reported as 0.
      const now = new Date()
      const dayStart = startOfDay(now)
      const entries = await Promise.all(
        types.map(async (type) => {
          const v = await healthKit.getAggregatedQuantity(type, dayStart, now, 'sum', enableCache, cacheTTL)
          return [type, v] as const
        })
      )
      setData(Object.fromEntries(entries))
    } catch (err) {
      setError(err as Error)
    } finally {
      setLoading(false)
    }
  }, [healthKit, types, enableCache, cacheTTL])

  useEffect(() => {
    fetchData()

    if (refreshInterval > 0) {
      const interval = setInterval(fetchData, refreshInterval)
      return () => clearInterval(interval)
    }
    
    return undefined
  }, [fetchData, refreshInterval])

  return { data, loading, error, refetch: fetchData }
}

/**
 * Hook to fetch complete health data for a period
 */
export function useHealthKitData(
  healthKit: HealthKit,
  timeRange: TimeRange = TimeRange.TODAY,
  customStartDate?: Date,
  customEndDate?: Date,
  enableCache: boolean = true,
  cacheTTL: number = 60
) {
  const [data, setData] = useState<HealthData | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<Error | null>(null)

  const fetchData = useCallback(async () => {
    try {
      setLoading(true)
      setError(null)

      const result = await healthKit.getHealthDataForTimeRange(
        timeRange,
        customStartDate || null,
        customEndDate || null,
        enableCache,
        cacheTTL
      )

      setData(result)
    } catch (err) {
      setError(err as Error)
    } finally {
      setLoading(false)
    }
  }, [healthKit, timeRange, customStartDate, customEndDate, enableCache, cacheTTL])

  useEffect(() => {
    fetchData()
  }, [fetchData])

  return { data, loading, error, refetch: fetchData }
}

/**
 * Hook to manage HealthKit authorizations
 */
export function useHealthKitAuthorization(healthKit: HealthKit) {
  const [isAuthorized, setIsAuthorized] = useState(false)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<Error | null>(null)

  const requestAuthorization = useCallback(async () => {
    try {
      setLoading(true)
      setError(null)

      const authorized = await healthKit.requestAuthorization()
      setIsAuthorized(authorized)

      return authorized
    } catch (err) {
      setError(err as Error)
      return false
    } finally {
      setLoading(false)
    }
  }, [healthKit])

  const checkStatus = useCallback(async (type: string) => {
    try {
      const status = await healthKit.checkAuthorizationStatus(type)
      return status
    } catch (err) {
      setError(err as Error)
      return 'notDetermined'
    }
  }, [healthKit])

  useEffect(() => {
    const checkAvailability = async () => {
      const available = await healthKit.isHealthKitAvailable()
      if (available) {
        // Check if already authorized
        const status = await checkStatus(HealthKitQuantityType.STEPS)
        setIsAuthorized(status === 'sharingAuthorized')
      }
      setLoading(false)
    }

    void checkAvailability()
  }, [healthKit, checkStatus])

  return { isAuthorized, loading, error, requestAuthorization, checkStatus }
}
