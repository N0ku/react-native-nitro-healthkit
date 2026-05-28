# Unit tests - react-native-nitro-healthkit

This folder contains the unit tests for the `react-native-nitro-healthkit` module.
The native module is mocked (see `__mocks__/`), so these tests run in plain Node —
no device or simulator required, and they run in CI.

## 🧪 Running the tests

```bash
# All tests
npm test

# Watch mode
npm run test:watch

# With coverage
npm run test:coverage
```

## 📋 Covered tests

### Availability and Authorization
- HealthKit availability check
- Authorization request

### Legacy methods
- `getSteps()` - Fetch steps (legacy method)
- `getHeartRate()` - Fetch heart rate
- `getHealthData()` - Fetch all data

### Quantity data methods
- `getQuantityData()` for steps
- `getQuantityData()` for heart rate
- `getAggregatedQuantity()` for sums/averages
- Validation of the returned data structure

### Category data methods
- `getCategoryData()` for sleep analysis
- Category data validation

### Workout methods
- `getWorkouts()` for workouts
- Workout data validation

### Cache methods
- `clearCache()` - Cache clearing

### Hooks
- `useHealthKitQuantity`, `useHealthKitCategory`, `useHealthKitRealtime`,
  `useHealthKitData`, `useHealthKitAuthorization`

### Error handling
- Invalid date ranges
- API error propagation

## 📊 Test structure

```
__tests__/
  ├── HealthKitModule.test.ts  # Native module surface (mocked)
  └── hooks.test.ts            # React hooks
```

## 🔧 Jest configuration

The Jest configuration lives in `package.json`. The native module and
`react-native` are mapped to mocks under `__mocks__/`.

## 🚀 CI

The tests run on every push/PR via `.github/workflows/ci.yml`:

```yaml
- name: Unit tests
  run: npm test -- --ci
```

## 📈 Coverage

```bash
npm run test:coverage
```

The report is generated in `coverage/lcov-report/index.html`.

## 📚 Resources

- [Jest documentation](https://jestjs.io/)
- [Testing React Native](https://reactnative.dev/docs/testing-overview)
- [Apple HealthKit documentation](https://developer.apple.com/documentation/healthkit)
