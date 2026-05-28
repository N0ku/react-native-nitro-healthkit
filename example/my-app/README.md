# react-native-nitro-healthkit - Example app

This example app demonstrates all the features of the `react-native-nitro-healthkit` module.

## 🎯 Goals

This app lets you:
- ✅ Test all of the HealthKit module's methods
- ✅ Verify that data is fetched correctly
- ✅ Compare legacy vs new methods
- ✅ Debug potential issues

## 🚀 Running

```bash
# Install dependencies
npm install

# Run on iOS
npm run ios

# Run on a specific simulator
npx expo run:ios --device "iPhone 15 Pro"
```

## 📱 Tested features

### Authorization
- ✅ `isHealthKitAvailable()` - Check availability
- ✅ `requestAuthorization()` - Request permissions

### Legacy methods
- ✅ `getSteps(startDate, endDate)` - Fetch steps
- ✅ `getHeartRate(startDate, endDate)` - Fetch heart rate
- ✅ `getHealthData(startDate, endDate)` - Fetch all data

### New methods (getQuantityData)
- ✅ Fetch step samples
- ✅ Fetch heart rate
- ✅ Fetch active energy
- ✅ Fetch distance

### Aggregation methods
- ✅ `getAggregatedQuantity()` - Sum, average, min, max

### Category data
- ✅ `getCategoryData()` - Sleep analysis

### Workouts
- ✅ `getWorkouts()` - Fetch workouts

## 🧪 Expected results

### On a real device
All methods should work and return data when:
- Permissions are granted
- HealthKit data exists for the selected period
- The user has recorded data

### On a simulator
⚠️ iOS simulators have limitations:
- Limited or missing HealthKit data
- Some methods may return empty arrays
- This is normal and expected

## 📊 Interface

The app shows:
- **Test status**: ✅ Success or ❌ Failed for each method
- **Fetched data**: Number of samples, aggregated values
- **Details**: Averages, computed totals
- **Errors**: Detailed error messages when applicable

## 🔧 Test periods

- **Today**: Today's data
- **7 days**: Last week's data
- **30 days**: Last month's data

## 📝 Notes

- Tests are non-destructive and read-only
- Data is cached for 60 seconds
- Pull-to-refresh to force a reload
- Detailed logs are available in the console

## 🐛 Debug

If a method fails:
1. Check HealthKit permissions in Settings > Privacy
2. Verify data exists in the Health app
3. Look at the logs in Xcode for more details
4. Try a real device rather than a simulator
