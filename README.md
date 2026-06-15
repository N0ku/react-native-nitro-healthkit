# react-native-nitro-healthkit 🏥

[![npm version](https://img.shields.io/npm/v/react-native-nitro-healthkit?style=for-the-badge&logo=npm&color=CB3837)](https://www.npmjs.com/package/react-native-nitro-healthkit)
[![CI](https://img.shields.io/github/actions/workflow/status/N0ku/react-native-nitro-healthkit/ci.yml?style=for-the-badge&logo=githubactions&logoColor=white&label=CI)](https://github.com/N0ku/react-native-nitro-healthkit/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg?style=for-the-badge)](LICENSE)
[![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg?style=for-the-badge)](#-contributing)

![React Native](https://img.shields.io/badge/React_Native-20232A?style=for-the-badge&logo=react&logoColor=61DAFB)
![TypeScript](https://img.shields.io/badge/TypeScript-007ACC?style=for-the-badge&logo=typescript&logoColor=white)
![Swift](https://img.shields.io/badge/Swift-FA7343?style=for-the-badge&logo=swift&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![iOS](https://img.shields.io/badge/iOS-000000?style=for-the-badge&logo=apple&logoColor=white)
![Android](https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)

A high-performance React Native library exposing **a single TypeScript API on top of Apple HealthKit (iOS) and Android Health Connect (Android)** via **Nitro Modules**. Built with Swift + Kotlin and powered by modern C++ interop for native performance.

> **Cross-platform.** Health Connect is the canonical Android health data API; everything that writes into it (Samsung Health, Google Fit, Fitbit since 2024, Withings, Oura, MyFitnessPal, …) is readable through this module on Android. See the [Android support matrix](#-android-support-matrix) below.

## ✨ Features

- 🚀 **Ultra-fast**: Built with Nitro Modules for native performance
- 🌍 **Cross-platform**: One TypeScript API, two native implementations (iOS Swift + Android Kotlin)
- 📊 **Comprehensive**: Steps, heart rate, active energy, distance, floors, sleep, workouts, and ~130 quantity / 70 category types
- 🔒 **Privacy-first**: Proper HealthKit & Health Connect authorization flows
- 📱 **iOS Native**: Pure Swift implementation with HealthKit framework
- 🤖 **Android Native**: Pure Kotlin implementation on top of `androidx.health.connect:connect-client`
- ✍️  **Writes**: `writeQuantityData` / `writeCategoryData` (insert manual samples)
- 👀 **Observers**: `observeQuantityChanges` / `observeCategoryChanges`
- 🌙 **Background sync**: register a periodic background job (Android WorkManager / iOS `BGTaskScheduler`) that POSTs deltas to your backend
- 🎯 **Type-safe**: Full TypeScript support
- ⚡ **Promise-based**: Modern async/await API

## 📦 Installation

```bash
npm install react-native-nitro-healthkit
# or
yarn add react-native-nitro-healthkit
```

### iOS Setup

> **Minimum iOS deployment target: 14.0.**

1. **Install pods:**
   ```bash
   cd example/my-app/ios && pod install
   ```

2. **Add HealthKit capability:**
   - Open your Xcode project
   - Select your target → Signing & Capabilities
   - Click "+ Capability" and add "HealthKit"

3. **Add privacy descriptions to `Info.plist`** (`NSHealthShareUsageDescription` is required to read; `NSHealthUpdateUsageDescription` is required only if you call `writeQuantityData` / `writeCategoryData`):
   ```xml
   <key>NSHealthShareUsageDescription</key>
   <string>We need access to your health data to track your activity</string>
   <key>NSHealthUpdateUsageDescription</key>
   <string>We need access to your health data to track your activity</string>
   ```

4. **Ensure entitlements are set.** Your `*.entitlements` file should contain:
   ```xml
   <key>com.apple.developer.healthkit</key>
   <true/>
   ```
   For `observeQuantityChanges` / `observeCategoryChanges` to fire while the app is backgrounded, also add the background-delivery entitlement:
   ```xml
   <key>com.apple.developer.healthkit.background-delivery</key>
   <true/>
   ```

5. **Background sync only** (skip if you don't call `registerBackgroundSync`). iOS requires the background-task handler to be registered *at launch*, before `application(_:didFinishLaunchingWithOptions:)` returns:

   - Declare the task identifier and background modes in `Info.plist`:
     ```xml
     <key>BGTaskSchedulerPermittedIdentifiers</key>
     <array>
       <string>com.nitrohealthkit.sync</string>
     </array>
     <key>UIBackgroundModes</key>
     <array>
       <string>fetch</string>
       <string>processing</string>
     </array>
     ```
   - Register the launch handler from your `AppDelegate`:
     ```swift
     import NitroHealthkit // Swift

     func application(_ application: UIApplication,
                      didFinishLaunchingWithOptions launchOptions: ...) -> Bool {
       HealthKitBackgroundSync.registerLaunchHandler()
       // ...
     }
     ```
     ```objc
     // Objective-C AppDelegate
     #import <NitroHealthkit/NitroHealthkit-Swift.h>
     [HealthKitBackgroundSync registerLaunchHandler];
     ```
   iOS decides when to actually run the task — `intervalMinutes` is a lower bound, not a guarantee.

### Android Setup

Health Connect ships in the platform on Android 14+. On Android 8–13, users install the "Health Connect" app from the Play Store; the module detects its presence via [`HealthConnectClient.getSdkStatus`](https://developer.android.com/health-and-fitness/guides/health-connect/develop/get-started) and surfaces it via `isHealthKitAvailable()`.

1. **Minimum SDK**: `26` (Android 8.0). The module's `build.gradle` defaults match.

2. **Declare permissions** in your host app's `AndroidManifest.xml` (or via `app.config.ts` if you use Expo prebuild). The library's own manifest declares the same set so it merges naturally.
   ```xml
   <uses-permission android:name="android.permission.health.READ_STEPS" />
   <uses-permission android:name="android.permission.health.WRITE_STEPS" />
   <uses-permission android:name="android.permission.health.READ_HEART_RATE" />
   <uses-permission android:name="android.permission.health.READ_SLEEP" />
   <uses-permission android:name="android.permission.health.READ_EXERCISE" />
   <uses-permission android:name="android.permission.health.READ_ACTIVE_CALORIES_BURNED" />
   <uses-permission android:name="android.permission.health.READ_DISTANCE" />
   <uses-permission android:name="android.permission.health.READ_FLOORS_CLIMBED" />
   <!-- …and the WRITE_* counterparts if you call writeQuantityData / writeCategoryData -->
   ```

3. **Add the Health Connect rationale intent filter** to your main `Activity` (Google requires this — without it, the system permission dialog won't show a link back to your app):
   ```xml
   <activity android:name=".MainActivity" …>
     <intent-filter>
       <action android:name="androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE" />
     </intent-filter>
   </activity>
   ```

4. **First-run flow**: `requestAuthorization()` on Android only *reports* whether the user has already granted the default set of permissions — requesting them needs an Activity, so it must be triggered from your UI layer:
   ```kotlin
   val requestPermissions = registerForActivityResult(
     PermissionController.createRequestPermissionResultContract()
   ) { granted -> /* update state */ }
   requestPermissions.launch(setOf(
     HealthPermission.getReadPermission(StepsRecord::class),
     HealthPermission.getReadPermission(HeartRateRecord::class),
     // ... etc.
   ))
   ```
   On the JS side, the host app handles this via `react-native-permissions` or a thin Kotlin glue Activity — the module deliberately does not own this flow because the choice of UI is host-app territory.

5. **Production launch**: for each `WRITE_*` permission you ship, Google Play asks for a written justification (review takes ~3 business days). Plan accordingly.

## 🚀 Usage

### Basic Example

```typescript
import { HealthKitModule } from 'react-native-nitro-healthkit';

// Request authorization
const authorized = await HealthKitModule.requestAuthorization();

if (authorized) {
  // Get today's steps
  const today = new Date();
  const startOfDay = new Date(today.setHours(0, 0, 0, 0));
  const steps = await HealthKitModule.getSteps(startOfDay, new Date());
  
  console.log(`Steps today: ${steps}`);
}
```

### Get Health Data for Multiple Days

```typescript
// Get last 7 days of health data
const endDate = new Date();
const startDate = new Date();
startDate.setDate(startDate.getDate() - 7);

const healthData = await HealthKitModule.getHealthData(startDate, endDate);

console.log('Health Data:', {
  steps: healthData.steps,
  heartRate: healthData.heartRate,
  activeEnergy: healthData.activeEnergy,
  distance: healthData.distance,
});
```

### Complete Component Example

```typescript
import React, { useState } from 'react';
import { View, Button, Text, Alert } from 'react-native';
import { HealthKitModule, type HealthData } from 'react-native-nitro-healthkit';

export default function HealthScreen() {
  const [isAuthorized, setIsAuthorized] = useState(false);
  const [healthData, setHealthData] = useState<HealthData | null>(null);

  const requestPermissions = async () => {
    try {
      const authorized = await HealthKitModule.requestAuthorization();
      setIsAuthorized(authorized);
      
      if (!authorized) {
        Alert.alert('Authorization denied');
      }
    } catch (error) {
      Alert.alert('Error', `${error}`);
    }
  };

  const fetchHealthData = async () => {
    if (!isAuthorized) {
      Alert.alert('Please authorize HealthKit first');
      return;
    }

    try {
      const endDate = new Date();
      const startDate = new Date();
      startDate.setDate(startDate.getDate() - 7);

      const data = await HealthKitModule.getHealthData(startDate, endDate);
      setHealthData(data);
    } catch (error) {
      Alert.alert('Error', `${error}`);
    }
  };

  return (
    <View>
      <Button
        title="Request Authorization"
        onPress={requestPermissions}
      />
      
      {isAuthorized && (
        <Button
          title="Fetch Health Data (7 days)"
          onPress={fetchHealthData}
        />
      )}

      {healthData && (
        <View>
          {healthData.steps && (
            <Text>Steps: {Math.round(healthData.steps)}</Text>
          )}
          {healthData.heartRate && (
            <Text>Heart Rate: {Math.round(healthData.heartRate)} bpm</Text>
          )}
        </View>
      )}
    </View>
  );
}
```

## 📚 API Reference

### `requestAuthorization(): Promise<boolean>`

Requests authorization to access HealthKit data.

**Returns:** `Promise<boolean>` - `true` if authorized, `false` otherwise

**Example:**
```typescript
const authorized = await HealthKitModule.requestAuthorization();
```

---

### `getSteps(startDate: Date, endDate: Date): Promise<number>`

Gets the total step count for the specified period.

**Parameters:**
- `startDate: Date` - Start of the period
- `endDate: Date` - End of the period

**Returns:** `Promise<number>` - Total steps count

**Example:**
```typescript
const steps = await HealthKitModule.getSteps(
  new Date('2025-10-17'),
  new Date('2025-10-24')
);
```

---

### `getHeartRate(startDate: Date, endDate: Date): Promise<number>`

Gets the average heart rate for the specified period.

**Parameters:**
- `startDate: Date` - Start of the period
- `endDate: Date` - End of the period

**Returns:** `Promise<number>` - Average heart rate in BPM

**Example:**
```typescript
const bpm = await HealthKitModule.getHeartRate(
  new Date('2025-10-17'),
  new Date('2025-10-24')
);
```

---

### `getHealthData(startDate: Date, endDate: Date): Promise<HealthData>`

Gets comprehensive health data for the specified period. This method fetches all available metrics in parallel and returns partial data if some metrics are unavailable.

**Parameters:**
- `startDate: Date` - Start of the period
- `endDate: Date` - End of the period

**Returns:** `Promise<HealthData>` - Object containing available health metrics

**Example:**
```typescript
const data = await HealthKitModule.getHealthData(
  new Date('2025-10-17'),
  new Date('2025-10-24')
);
```

---

### `HealthData` Interface

```typescript
interface HealthData {
  steps?: number;           // Total steps count
  heartRate?: number;       // Average heart rate (BPM)
  activeEnergy?: number;    // Active energy burned (kcal)
  distance?: number;        // Distance traveled (meters)
  sleepAnalysis?: string;   // Sleep summary
}
```

> `getHealthData` currently populates `steps` and `heartRate`; the other fields are reserved on the
> interface. To read active energy, distance or sleep today, use `getAggregatedQuantity` /
> `getQuantityData` (e.g. `ACTIVE_ENERGY_BURNED`, `DISTANCE_WALKING_RUNNING`) and `getCategoryData`
> (`SLEEP_ANALYSIS`).

## 🔍 Error Handling

The library handles errors gracefully. If a specific metric is unavailable (e.g., no heart rate data), other metrics will still be returned:

```typescript
const data = await HealthKitModule.getHealthData(startDate, endDate);

// Even if heart rate data is unavailable, steps will be returned
if (data.steps) {
  console.log(`Steps: ${data.steps}`);
}

if (!data.heartRate) {
  console.log('No heart rate data available');
}
```

## 🏗️ Architecture

This library is built with [Nitro Modules](https://github.com/mrousavy/nitro), providing:

- **Native performance**: Direct Swift/C++ implementation
- **Type safety**: Full TypeScript definitions generated from native specs
- **Modern APIs**: Promise-based async/await interface
- **Zero-copy**: Efficient data passing between JavaScript and native

### Project Structure

```
packages/
├── ios/                                       # Swift HealthKit implementation
│   ├── HealthKitModule.swift
│   ├── CacheManager.swift
│   ├── Observers/HealthKitObserverManager.swift   # HKObserverQuery + background delivery
│   ├── BackgroundSync/                            # BGTaskScheduler + Keychain
│   │   ├── HealthKitBackgroundSync.swift
│   │   └── KeychainCredentialsStore.swift
│   └── NitroHealthkitObjcBridge.swift
├── android/                                   # Kotlin Health Connect implementation
│   ├── build.gradle
│   └── src/main/kotlin/io/github/n0ku/nitrohealthkit/
│       ├── HealthKitModule.kt                 # extends HybridHealthKitSpec
│       ├── cache/CacheManager.kt              # parity with iOS CacheManager
│       ├── mappers/                           # HK ↔ Health Connect mapping
│       │   ├── QuantityMapper.kt
│       │   ├── CategoryMapper.kt
│       │   └── WorkoutMapper.kt
│       ├── observers/ChangesObserver.kt       # Health Connect changes API
│       ├── workers/HealthSyncWorker.kt        # WorkManager periodic sync
│       ├── auth/SecureCredentialsStore.kt     # EncryptedSharedPreferences-backed
│       └── util/                              # TimeRangeHelper, context holder
├── src/
│   ├── index.ts                               # JS entry point + types
│   └── specs/Example.nitro.ts                 # Nitro spec (source of truth)
├── nitrogen/                                  # Generated Swift / Kotlin / C++ stubs
└── lib/                                       # Compiled JavaScript
```

## 🤖 Android support matrix

| HealthKit type | Android record | Read | Write | Notes |
|---|---|:-:|:-:|---|
| `STEPS` | `StepsRecord` | ✅ | ✅ | aggregate via `StepsRecord.COUNT_TOTAL` |
| `HEART_RATE` | `HeartRateRecord` (samples) | ✅ | ✅ | per-sample flatten |
| `RESTING_HEART_RATE` | `RestingHeartRateRecord` | ✅ | ✅ | |
| `HEART_RATE_VARIABILITY_SDNN` | `HeartRateVariabilityRmssdRecord` | ✅ | ❌ | HC has RMSSD, not SDNN — best-effort |
| `ACTIVE_ENERGY_BURNED` | `ActiveCaloriesBurnedRecord` | ✅ | ✅ | kcal |
| `BASAL_ENERGY_BURNED` | `BasalMetabolicRateRecord` | ✅ | ❌ | kcal/day |
| `DIETARY_ENERGY_CONSUMED` | `TotalCaloriesBurnedRecord` | ✅ | ✅ | |
| `DISTANCE_*` | `DistanceRecord` | ✅ | ✅ | metres |
| `FLIGHTS_CLIMBED` | `FloorsClimbedRecord` | ✅ | ✅ | |
| `BODY_MASS` / `HEIGHT` / `BODY_FAT_PERCENTAGE` / `LEAN_BODY_MASS` | `WeightRecord` / `HeightRecord` / `BodyFatRecord` / `LeanBodyMassRecord` | ✅ | ✅ | |
| `BLOOD_GLUCOSE` / `BLOOD_PRESSURE_*` / `BLOOD_OXYGEN_SATURATION` | `BloodGlucoseRecord` / `BloodPressureRecord` / `OxygenSaturationRecord` | ✅ | ❌ | |
| `BODY_TEMPERATURE` | `BodyTemperatureRecord` | ✅ | ✅ | |
| `RESPIRATORY_RATE` | `RespiratoryRateRecord` | ✅ | ✅ | |
| `VO2_MAX` | `Vo2MaxRecord` | ✅ | ❌ | |
| `WALKING_SPEED` / `RUNNING_SPEED` | `SpeedRecord` (samples) | ✅ | ❌ | m/s |
| `RUNNING_POWER` / `CYCLING_POWER` | `PowerRecord` (samples) | ✅ | ❌ | watts |
| `DIETARY_WATER` | `HydrationRecord` | ✅ | ✅ | litres |
| `SLEEP_ANALYSIS` | `SleepSessionRecord.stages` | ✅ | ✅ | stages unrolled into one sample each |
| `MENSTRUAL_FLOW` / `INTERMENSTRUAL_BLEEDING` / `OVULATION_TEST_RESULT` / `CERVICAL_MUCUS_QUALITY` / `SEXUAL_ACTIVITY` | matching HC records | ✅ | partial | |
| Workouts (`getWorkouts`) | `ExerciseSessionRecord` | ✅ | ❌ | `workoutActivityType` aligned with `HKWorkoutActivityType` raw values |
| Apple-only (`APPLE_EXERCISE_TIME`, `APPLE_STAND_HOUR`, `MINDFUL_SESSION`, `HANDWASHING_EVENT`, …) | — | ⛔ | ⛔ | returns empty list + logs warning. Use `ExerciseSessionRecord` for active time. |

> Reading an Apple-only type or a type Health Connect doesn't model returns `[]` (and `writeQuantityData` returns `false`) — the module never throws for unsupported types so cross-platform code keeps working.

## 🌙 Background sync

```typescript
import { HealthKitModule } from 'react-native-nitro-healthkit';

// After login — the native job pulls deltas periodically and POSTs them.
await HealthKitModule.registerBackgroundSync({
  apiBaseUrl: 'https://api.example.com',
  jwtToken: '<user JWT>',
  intervalMinutes: 15,
  types: ['HKQuantityTypeIdentifierStepCount', 'HKQuantityTypeIdentifierHeartRate'],
  syncPath: '/users/health-data',
});

// On logout — stops the job AND wipes the stored credentials at rest.
await HealthKitModule.unregisterBackgroundSync();

// Optional: is a sync currently registered?
const active = await HealthKitModule.isBackgroundSyncRegistered();
```

Both platforms `POST {apiBaseUrl}{syncPath}` with `Authorization: Bearer <jwt>` and a body of
`{ source, syncedAt, entries: [{ type, samples: [...] }] }` containing the new samples since the last
successful checkpoint, and clear the stored credentials on a `401`/`403` response.

- **Android**: a WorkManager `PeriodicWorkRequest` (floor of 15 min). Credentials live in
  `EncryptedSharedPreferences` (AES-256-GCM, MasterKey backed by the Android Keystore).
- **iOS**: a `BGTaskScheduler` app-refresh task (identifier `com.nitrohealthkit.sync`). Credentials
  live in the Keychain (`kSecAttrAccessibleAfterFirstUnlock`). Requires the one-time launch
  registration and `Info.plist` entries described in [iOS Setup](#ios-setup) — without them the
  call stores credentials but the OS never runs the task. iOS schedules opportunistically, so runs
  are best-effort, not guaranteed at a fixed interval.

## 👀 Observers

```typescript
const sub = await HealthKitModule.observeQuantityChanges(
  HealthKitQuantityType.STEPS,
  (token) => {
    // Re-fetch what you need; the token is opaque (Health Connect's changes cursor).
    void HealthKitModule.getQuantityData(/* ... */);
  },
);

// later …
await HealthKitModule.removeObserver(sub);

// or drop every active subscription at once
await HealthKitModule.removeAllObservers();
```

The `token` passed to your callback is opaque and platform-specific (Health Connect's changes cursor
on Android, a serialized `HKQueryAnchor` on iOS) — treat it as a "something changed" signal and
re-fetch what you need. On **iOS**, observers use `HKObserverQuery` with background delivery (add the
`com.apple.developer.healthkit.background-delivery` entitlement to keep them firing while
backgrounded). On **Android**, Health Connect has no push channel, so the Kotlin side runs a
30-second polling coroutine per subscription.

## 🧪 Testing

An example app is included in the `example/` directory:

```bash
cd example/my-app
npm install
npx expo run:ios     # iOS
npx expo run:android # Android (Health Connect must be installed/active on the device)
```

From the repo root the `Makefile` exposes:

```bash
make test            # TS (jest) + Kotlin (gradle)
make test-ts
make test-android    # ./gradlew :react-native-nitro-healthkit:test
```

CI runs both suites on every push/PR — see `.github/workflows/ci.yml`. After editing `src/specs/Example.nitro.ts`, regenerate the Swift/Kotlin/C++ stubs with `make nitrogen` (or `npm run specs` inside `packages/`).

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## 📄 License

MIT © N0ku — see [LICENSE](LICENSE).

## 🙏 Acknowledgments

- Built with [Nitro Modules](https://github.com/mrousavy/nitro) by [@mrousavy](https://github.com/mrousavy)
- Powered by Apple HealthKit and Android Health Connect

## 📞 Support

- 🐛 [Report an issue](https://github.com/N0ku/react-native-nitro-healthkit/issues)
- 💬 [Discussions](https://github.com/N0ku/react-native-nitro-healthkit/discussions)
