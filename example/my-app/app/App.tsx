import { StatusBar } from 'expo-status-bar'
import {
  StyleSheet,
  Text,
  View,
  Alert,
  ScrollView,
  ActivityIndicator,
  TouchableOpacity,
  RefreshControl,
} from 'react-native'
import { SafeAreaView } from 'react-native-safe-area-context'
import { useState, useEffect, useCallback } from 'react'

import { 
  HealthKitModule, 
  HealthKitQuantityType,
  type QuantityDataPoint,
  type CategoryDataPoint,
  type WorkoutDataPoint,
} from 'react-native-nitro-healthkit'

type TabType = 'today' | 'week' | 'month'

interface TestResults {
  steps?: number
  stepSamples?: QuantityDataPoint[]
  heartRate?: number
  heartRateSamples?: QuantityDataPoint[]
  sleepData?: CategoryDataPoint[]
  workouts?: WorkoutDataPoint[]
  activeEnergy?: number
  distance?: number
}

const App = () => {
  const [isAuthorized, setIsAuthorized] = useState(false)
  const [loading, setLoading] = useState(false)
  const [activeTab, setActiveTab] = useState<TabType>('today')
  const [testResults, setTestResults] = useState<TestResults | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    checkAuthorization()
  }, [])

  const checkAuthorization = async () => {
    try {
      const available = await HealthKitModule.isHealthKitAvailable()
      console.log('HealthKit available:', available)
      if (!available) {
        setError('HealthKit is not available on this device')
      }
    } catch (error) {
      console.error('Error checking authorization:', error)
      setError(`Error: ${error}`)
    }
  }

  const requestPermissions = async () => {
    try {
      setLoading(true)
      setError(null)
      const authorized = await HealthKitModule.requestAuthorization()
      setIsAuthorized(authorized)
      
      if (authorized) {
        Alert.alert('✅ Success', 'HealthKit access granted!')
        await fetchData()
      } else {
        Alert.alert('❌ Denied', 'HealthKit access denied')
      }
    } catch (error) {
      const errorMsg = `${error}`
      setError(errorMsg)
      Alert.alert('Error', errorMsg)
    } finally {
      setLoading(false)
    }
  }

  const fetchData = useCallback(async () => {
    if (!isAuthorized) {
      return
    }

    try {
      setLoading(true)
      setError(null)
      
      const endDate = new Date()
      let startDate = new Date()

      switch (activeTab) {
        case 'today':
          startDate.setHours(0, 0, 0, 0)
          endDate.setHours(23, 59, 59, 999)
          break
        case 'week':
          startDate.setDate(startDate.getDate() - 7)
          startDate.setHours(0, 0, 0, 0)
          break
        case 'month':
          startDate.setDate(startDate.getDate() - 30)
          startDate.setHours(0, 0, 0, 0)
          break
      }

      console.log('📅 Fetching data from', startDate, 'to', endDate)

      // Test all methods
      const results: TestResults = {}

      try {
        // Test legacy method
        console.log('🚶 Testing getSteps...')
        results.steps = await HealthKitModule.getSteps(startDate, endDate)
        console.log('✅ Steps:', results.steps)
      } catch (err) {
        console.error('❌ getSteps failed:', err)
      }

      try {
        // Test new getQuantityData
        console.log('🚶 Testing getQuantityData for steps...')
        results.stepSamples = await HealthKitModule.getQuantityData(
          HealthKitQuantityType.STEPS,
          startDate,
          endDate,
          null,
          true,
          60
        )
        console.log('✅ Step samples:', results.stepSamples.length)
      } catch (err) {
        console.error('❌ getQuantityData failed:', err)
      }

      try {
        // Test heart rate
        console.log('💓 Testing heart rate...')
        results.heartRateSamples = await HealthKitModule.getQuantityData(
          HealthKitQuantityType.HEART_RATE,
          startDate,
          endDate,
          null,
          true,
          60
        )
        console.log('✅ Heart rate samples:', results.heartRateSamples.length)
      } catch (err) {
        console.error('❌ Heart rate failed:', err)
      }

      try {
        // Test aggregated data
        console.log('🔢 Testing aggregated active energy...')
        results.activeEnergy = await HealthKitModule.getAggregatedQuantity(
          HealthKitQuantityType.ACTIVE_ENERGY_BURNED,
          startDate,
          endDate,
          'sum',
          true,
          60
        )
        console.log('✅ Active energy:', results.activeEnergy)
      } catch (err) {
        console.error('❌ Active energy failed:', err)
      }

      setTestResults(results)
    } catch (error) {
      const errorMsg = `${error}`
      setError(errorMsg)
      Alert.alert('Error', errorMsg)
    } finally {
      setLoading(false)
    }
  }, [isAuthorized, activeTab])

  useEffect(() => {
    if (isAuthorized) {
      fetchData()
    }
  }, [isAuthorized, activeTab, fetchData])

  const renderTabButtons = () => (
    <View style={styles.tabContainer}>
      <TouchableOpacity
        style={[styles.tab, activeTab === 'today' && styles.activeTab]}
        onPress={() => setActiveTab('today')}
      >
        <Text style={[styles.tabText, activeTab === 'today' && styles.activeTabText]}>
          Today
        </Text>
      </TouchableOpacity>

      <TouchableOpacity
        style={[styles.tab, activeTab === 'week' && styles.activeTab]}
        onPress={() => setActiveTab('week')}
      >
        <Text style={[styles.tabText, activeTab === 'week' && styles.activeTabText]}>
          7 days
        </Text>
      </TouchableOpacity>

      <TouchableOpacity
        style={[styles.tab, activeTab === 'month' && styles.activeTab]}
        onPress={() => setActiveTab('month')}
      >
        <Text style={[styles.tabText, activeTab === 'month' && styles.activeTabText]}>
          30 days
        </Text>
      </TouchableOpacity>
    </View>
  )

  const renderData = () => {
    if (!testResults) {
      return (
        <View style={styles.emptyContainer}>
          <Text style={styles.emptyIcon}>📊</Text>
          <Text style={styles.emptyText}>No data available</Text>
          <Text style={styles.emptySubtext}>
            Tap the button below to load the data
          </Text>
          <TouchableOpacity style={styles.refreshButton} onPress={fetchData}>
            <Text style={styles.refreshButtonText}>🔄 Load data</Text>
          </TouchableOpacity>
        </View>
      )
    }

    return (
      <ScrollView 
        style={styles.contentContainer}
        refreshControl={
          <RefreshControl refreshing={loading} onRefresh={fetchData} />
        }
      >
        <Text style={styles.sectionTitle}>Test results</Text>
        
        {error && (
          <View style={styles.errorCard}>
            <Text style={styles.errorIcon}>❌</Text>
            <Text style={styles.errorText}>{error}</Text>
          </View>
        )}

        <View style={styles.card}>
          <Text style={styles.cardIcon}>🚶</Text>
          <Text style={styles.cardTitle}>Steps (Legacy Method)</Text>
          <Text style={styles.cardValue}>
            {testResults.steps !== undefined ? Math.round(testResults.steps).toLocaleString() : 'N/A'}
          </Text>
          <Text style={styles.cardStatus}>
            {testResults.steps !== undefined ? '✅ Success' : '❌ Failed'}
          </Text>
        </View>

        <View style={styles.card}>
          <Text style={styles.cardIcon}>🚶</Text>
          <Text style={styles.cardTitle}>Step samples (getQuantityData)</Text>
          <Text style={styles.cardValue}>
            {testResults.stepSamples ? `${testResults.stepSamples.length} samples` : 'N/A'}
          </Text>
          <Text style={styles.cardStatus}>
            {testResults.stepSamples ? '✅ Success' : '❌ Failed'}
          </Text>
          {testResults.stepSamples && testResults.stepSamples.length > 0 && (
            <Text style={styles.cardDetail}>
              Total: {Math.round(testResults.stepSamples.reduce((sum, s) => sum + s.value, 0)).toLocaleString()} steps
            </Text>
          )}
        </View>

        <View style={styles.card}>
          <Text style={styles.cardIcon}>❤️</Text>
          <Text style={styles.cardTitle}>Heart rate</Text>
          <Text style={styles.cardValue}>
            {testResults.heartRateSamples ? `${testResults.heartRateSamples.length} samples` : 'N/A'}
          </Text>
          <Text style={styles.cardStatus}>
            {testResults.heartRateSamples ? '✅ Success' : '❌ Failed'}
          </Text>
          {testResults.heartRateSamples && testResults.heartRateSamples.length > 0 && (
            <Text style={styles.cardDetail}>
              Avg: {Math.round(testResults.heartRateSamples.reduce((sum, s) => sum + s.value, 0) / testResults.heartRateSamples.length)} bpm
            </Text>
          )}
        </View>

        <View style={styles.card}>
          <Text style={styles.cardIcon}>🔥</Text>
          <Text style={styles.cardTitle}>Active energy (Aggregated)</Text>
          <Text style={styles.cardValue}>
            {testResults.activeEnergy !== undefined ? `${Math.round(testResults.activeEnergy)} kcal` : 'N/A'}
          </Text>
          <Text style={styles.cardStatus}>
            {testResults.activeEnergy !== undefined ? '✅ Success' : '❌ Failed'}
          </Text>
        </View>

        <TouchableOpacity style={styles.refreshButton} onPress={fetchData}>
          <Text style={styles.refreshButtonText}>🔄 Re-run tests</Text>
        </TouchableOpacity>
      </ScrollView>
    )
  }

  if (!isAuthorized) {
    return (
      <SafeAreaView style={styles.container}>
        <StatusBar style="auto" />
        <View style={styles.centerContainer}>
          <Text style={styles.title}>🏥 Nitro HealthKit</Text>
          <Text style={styles.subtitle}>Full feature showcase</Text>

          <View style={styles.authCard}>
            <Text style={styles.authTitle}>⚠️ Authorization required</Text>
            <Text style={styles.authText}>
              This app needs access to your HealthKit data to work.
            </Text>

            <TouchableOpacity
              style={styles.authButton}
              onPress={requestPermissions}
              disabled={loading}
            >
              {loading ? (
                <ActivityIndicator color="#fff" />
              ) : (
                <Text style={styles.authButtonText}>Grant HealthKit access</Text>
              )}
            </TouchableOpacity>
          </View>
        </View>
      </SafeAreaView>
    )
  }

  return (
    <SafeAreaView style={styles.container}>
      <StatusBar style="auto" />

      <View style={styles.header}>
        <Text style={styles.headerTitle}>🏥 Nitro HealthKit</Text>
      </View>

      {renderTabButtons()}

      {loading ? (
        <View style={styles.loadingContainer}>
          <ActivityIndicator size="large" color="#007AFF" />
        </View>
      ) : (
        renderData()
      )}
    </SafeAreaView>
  )
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f5f5f5',
  },
  centerContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    padding: 20,
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: 16,
    backgroundColor: '#fff',
    borderBottomWidth: 1,
    borderBottomColor: '#e0e0e0',
  },
  headerTitle: {
    fontSize: 20,
    fontWeight: 'bold',
  },
  title: {
    fontSize: 32,
    fontWeight: 'bold',
    marginBottom: 10,
    textAlign: 'center',
  },
  subtitle: {
    fontSize: 16,
    color: '#666',
    marginBottom: 40,
    textAlign: 'center',
  },
  authCard: {
    backgroundColor: '#fff',
    borderRadius: 16,
    padding: 24,
    width: '100%',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 8,
    elevation: 3,
  },
  authTitle: {
    fontSize: 20,
    fontWeight: 'bold',
    marginBottom: 12,
    textAlign: 'center',
  },
  authText: {
    fontSize: 14,
    color: '#666',
    textAlign: 'center',
    marginBottom: 24,
    lineHeight: 20,
  },
  authButton: {
    backgroundColor: '#007AFF',
    borderRadius: 12,
    padding: 16,
    alignItems: 'center',
  },
  authButtonText: {
    color: '#fff',
    fontSize: 16,
    fontWeight: '600',
  },
  tabContainer: {
    flexDirection: 'row',
    backgroundColor: '#fff',
    borderBottomWidth: 1,
    borderBottomColor: '#e0e0e0',
  },
  tab: {
    flex: 1,
    paddingVertical: 12,
    alignItems: 'center',
    borderBottomWidth: 2,
    borderBottomColor: 'transparent',
  },
  activeTab: {
    borderBottomColor: '#007AFF',
  },
  tabText: {
    fontSize: 14,
    color: '#666',
    fontWeight: '500',
  },
  activeTabText: {
    color: '#007AFF',
    fontWeight: '600',
  },
  contentContainer: {
    flex: 1,
    padding: 16,
  },
  emptyContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    padding: 40,
  },
  emptyIcon: {
    fontSize: 64,
    marginBottom: 16,
  },
  emptyText: {
    fontSize: 18,
    fontWeight: '600',
    marginBottom: 8,
  },
  emptySubtext: {
    fontSize: 14,
    color: '#666',
    textAlign: 'center',
    marginBottom: 24,
  },
  sectionTitle: {
    fontSize: 24,
    fontWeight: 'bold',
    marginBottom: 16,
  },
  card: {
    backgroundColor: '#fff',
    borderRadius: 12,
    padding: 20,
    marginBottom: 12,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 2,
  },
  cardIcon: {
    fontSize: 32,
    marginBottom: 8,
  },
  cardTitle: {
    fontSize: 14,
    color: '#666',
    marginBottom: 4,
  },
  cardValue: {
    fontSize: 28,
    fontWeight: 'bold',
  },
  cardStatus: {
    fontSize: 12,
    marginTop: 8,
    fontWeight: '600',
  },
  cardDetail: {
    fontSize: 14,
    color: '#666',
    marginTop: 4,
  },
  errorCard: {
    backgroundColor: '#fff',
    borderRadius: 12,
    padding: 16,
    marginBottom: 12,
    borderLeftWidth: 4,
    borderLeftColor: '#ff3b30',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 2,
  },
  errorIcon: {
    fontSize: 24,
    marginBottom: 8,
  },
  errorText: {
    fontSize: 14,
    color: '#ff3b30',
  },
  refreshButton: {
    backgroundColor: '#007AFF',
    borderRadius: 12,
    padding: 16,
    alignItems: 'center',
    marginTop: 8,
    marginBottom: 24,
  },
  refreshButtonText: {
    color: '#fff',
    fontSize: 16,
    fontWeight: '600',
  },
  loadingContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
})

export default App
