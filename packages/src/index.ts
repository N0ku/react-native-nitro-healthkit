import { NitroModules } from 'react-native-nitro-modules'
import type { HealthKit } from './specs/HealthKit.nitro'

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

// Export as HealthKitModule for direct, property-style access. We wrap the
// instance in a Proxy so `createHybridObject` runs only on the first property
// access, never at module-evaluation time. Importing this module (Metro, web,
// Jest, Expo Go) must not call into native code until a method is actually used.
export const HealthKitModule: HealthKit = new Proxy({} as HealthKit, {
	get(_target, prop) {
		const instance = getHealthKitInstance() as unknown as Record<string | symbol, unknown>
		const value = instance[prop]
		return typeof value === 'function'
			? (value as (...args: unknown[]) => unknown).bind(instance)
			: value
	},
})

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
} from './specs/HealthKit.nitro'

export { 
  HealthKitQuantityType, 
  HealthKitCategoryType, 
  TimeRange,
  AggregationType 
} from './specs/HealthKit.nitro'

export {
  useHealthKitQuantity,
  useHealthKitCategory,
  useHealthKitRealtime,
  useHealthKitData,
  useHealthKitAuthorization
} from './hooks'
