import Foundation
import Security

/// Encrypted persistent storage for the JWT, backend URL and sync config that the
/// background sync task needs at execution time — the iOS counterpart of Android's
/// `SecureCredentialsStore` (which uses `EncryptedSharedPreferences`).
///
/// Backed by the iOS Keychain (`kSecClassGenericPassword`). Items use
/// `kSecAttrAccessibleAfterFirstUnlock` so a `BGTaskScheduler` task can still read the
/// credentials while the device is locked (but only after the first unlock since boot).
/// Values are wiped via `clear()` on `unregisterBackgroundSync` or when the backend
/// rejects the token (401/403).
final class KeychainCredentialsStore: @unchecked Sendable {
  private let service: String

  init(service: String) {
    self.service = service
  }

  func save(apiBaseUrl: String, jwtToken: String, syncPath: String, types: [String], intervalMinutes: Int) {
    set(Key.apiBaseUrl, apiBaseUrl)
    set(Key.jwt, jwtToken)
    set(Key.syncPath, syncPath)
    set(Key.types, types.joined(separator: ","))
    set(Key.intervalMinutes, String(intervalMinutes))
  }

  func apiBaseUrl() -> String? { get(Key.apiBaseUrl) }

  func jwtToken() -> String? { get(Key.jwt) }

  func syncPath() -> String { get(Key.syncPath) ?? Self.defaultSyncPath }

  func types() -> [String] {
    (get(Key.types) ?? "")
      .split(separator: ",")
      .map { String($0).trimmingCharacters(in: .whitespaces) }
      .filter { !$0.isEmpty }
  }

  func intervalMinutes() -> Int { Int(get(Key.intervalMinutes) ?? "") ?? Self.defaultIntervalMinutes }

  func clear() {
    [Key.apiBaseUrl, Key.jwt, Key.syncPath, Key.types, Key.intervalMinutes].forEach(delete)
  }

  // MARK: - Keychain primitives

  private func set(_ account: String, _ value: String) {
    delete(account)
    guard let data = value.data(using: .utf8) else { return }
    let query: [String: Any] = [
      kSecClass as String: kSecClassGenericPassword,
      kSecAttrService as String: service,
      kSecAttrAccount as String: account,
      kSecValueData as String: data,
      kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlock,
    ]
    SecItemAdd(query as CFDictionary, nil)
  }

  private func get(_ account: String) -> String? {
    let query: [String: Any] = [
      kSecClass as String: kSecClassGenericPassword,
      kSecAttrService as String: service,
      kSecAttrAccount as String: account,
      kSecReturnData as String: true,
      kSecMatchLimit as String: kSecMatchLimitOne,
    ]
    var result: AnyObject?
    guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess,
          let data = result as? Data else {
      return nil
    }
    return String(data: data, encoding: .utf8)
  }

  private func delete(_ account: String) {
    let query: [String: Any] = [
      kSecClass as String: kSecClassGenericPassword,
      kSecAttrService as String: service,
      kSecAttrAccount as String: account,
    ]
    SecItemDelete(query as CFDictionary)
  }

  private enum Key {
    static let apiBaseUrl = "api_base_url"
    static let jwt = "jwt"
    static let syncPath = "sync_path"
    static let types = "types"
    static let intervalMinutes = "interval_minutes"
  }

  static let defaultSyncPath = "/users/health-data"
  static let defaultIntervalMinutes = 15
}
