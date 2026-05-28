import { NitroModules } from 'react-native-nitro-modules'
import type { HealthKit } from './specs/Example.nitro'

// Lazily evaluate createHybridObject so importing this module in JS
// (e.g. Metro bundler) doesn't call into native code during module
// evaluation. Consumers should call `getHealthKit()` to obtain the
// runtime instance.
let _healthKitInstance: HealthKit | null = null

function getHealthKitInstance(): HealthKit {
	if (!_healthKitInstance) {
		_healthKitInstance = NitroModules.createHybridObject<HealthKit>('HealthKit')
	}
	return _healthKitInstance
}

// Export as HealthKitModule for direct access
export const HealthKitModule = getHealthKitInstance()

// Also export the getter for lazy initialization
export function getHealthKit(): HealthKit {
	return getHealthKitInstance()
}

export type {
  HealthData,
  HealthKit,
  QuantityDataPoint,
  CategoryDataPoint,
  WorkoutDataPoint,
  CacheConfig,
  QueryOptions,
  BackgroundSyncConfig,
  HealthChangeEvent
} from './specs/Example.nitro'

export { 
  HealthKitQuantityType, 
  HealthKitCategoryType, 
  TimeRange,
  AggregationType 
} from './specs/Example.nitro'

export {
  useHealthKitQuantity,
  useHealthKitCategory,
  useHealthKitRealtime,
  useHealthKitData,
  useHealthKitAuthorization
} from './hooks'
