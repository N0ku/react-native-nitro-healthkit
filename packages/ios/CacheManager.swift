import Foundation

/// Cache manager for HealthKit
/// Manages data cache with TTL and maximum size
class CacheManager {
  static let shared = CacheManager()
  
  private struct CacheEntry {
    let data: Any
    let timestamp: Date
    let ttl: TimeInterval
    
    var isExpired: Bool {
      return Date().timeIntervalSince(timestamp) > ttl
    }
  }
  
  private var cache: [String: CacheEntry] = [:]
  private let lock = NSLock()
  private var maxSize: Int = 100
  
  private init() {}
  
  func configure(maxSize: Int) {
    lock.lock()
    defer { lock.unlock() }
    self.maxSize = maxSize
  }
  
  /// To save a value in cache
  func set<T>(_ key: String, value: T, ttl: TimeInterval) {
    lock.lock()
    defer { lock.unlock() }
    
    // Clean up cache if too large
    if cache.count >= maxSize {
      cleanupExpiredEntries()
      
      // If still too large, remove oldest entries
      if cache.count >= maxSize {
        let sortedKeys = cache.sorted { $0.value.timestamp < $1.value.timestamp }.map { $0.key }
        let keysToRemove = sortedKeys.prefix(maxSize / 4) // Remove 25% of oldest entries
        keysToRemove.forEach { cache.removeValue(forKey: $0) }
      }
    }
    
    cache[key] = CacheEntry(data: value, timestamp: Date(), ttl: ttl)
  }
  
  /// Get a value from cache
  func get<T>(_ key: String) -> T? {
    lock.lock()
    defer { lock.unlock() }
    
    guard let entry = cache[key] else { return nil }
    
    if entry.isExpired {
      cache.removeValue(forKey: key)
      return nil
    }
    
    return entry.data as? T
  }
  
  /// Check if a key exists in cache
  func has(_ key: String) -> Bool {
    lock.lock()
    defer { lock.unlock() }
    
    guard let entry = cache[key] else { return false }
    
    if entry.isExpired {
      cache.removeValue(forKey: key)
      return false
    }
    
    return true
  }
  
  /// Remove a key from cache
  func remove(_ key: String) {
    lock.lock()
    defer { lock.unlock() }
    cache.removeValue(forKey: key)
  }
  
  /// To clear the cache
  func clear() {
    lock.lock()
    defer { lock.unlock() }
    cache.removeAll()
  }
  
  /// Clean expired entries
  private func cleanupExpiredEntries() {
    let expiredKeys = cache.filter { $0.value.isExpired }.map { $0.key }
    expiredKeys.forEach { cache.removeValue(forKey: $0) }
  }
  
  /// Generate a cache key
  static func generateKey(type: String, startDate: Date, endDate: Date, options: String = "") -> String {
    let formatter = ISO8601DateFormatter()
    return "\(type)_\(formatter.string(from: startDate))_\(formatter.string(from: endDate))_\(options)"
  }
}
