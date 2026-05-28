import Foundation

/// Helper to manage time periods
class TimeRangeHelper {
  
  /// Period type
  enum TimeRange: String {
    case today = "today"
    case yesterday = "yesterday"
    case thisWeek = "this_week"
    case lastWeek = "last_week"
    case thisMonth = "this_month"
    case lastMonth = "last_month"
    case thisYear = "this_year"
    case lastYear = "last_year"
    case last7Days = "last_7_days"
    case last30Days = "last_30_days"
    case last90Days = "last_90_days"
    case custom = "custom"
  }
  
  /// Returns start and end dates for a given period
  static func getDates(for timeRange: TimeRange, customStart: Date? = nil, customEnd: Date? = nil) -> (start: Date, end: Date) {
    let calendar = Calendar.current
    let now = Date()
    // Safe fallback when a Calendar computation returns nil (extremely rare).
    let today = (start: calendar.startOfDay(for: now), end: now)

    switch timeRange {
    case .today:
      return today

    case .yesterday:
      guard let yesterday = calendar.date(byAdding: .day, value: -1, to: now) else { return today }
      let start = calendar.startOfDay(for: yesterday)
      guard let end = calendar.date(byAdding: .day, value: 1, to: start) else { return today }
      return (start, end)

    case .thisWeek:
      guard let start = calendar.date(from: calendar.dateComponents([.yearForWeekOfYear, .weekOfYear], from: now)) else { return today }
      return (start, now)

    case .lastWeek:
      guard let thisWeekStart = calendar.date(from: calendar.dateComponents([.yearForWeekOfYear, .weekOfYear], from: now)),
            let lastWeekStart = calendar.date(byAdding: .weekOfYear, value: -1, to: thisWeekStart) else { return today }
      return (lastWeekStart, thisWeekStart)

    case .thisMonth:
      guard let start = calendar.date(from: calendar.dateComponents([.year, .month], from: now)) else { return today }
      return (start, now)

    case .lastMonth:
      guard let thisMonthStart = calendar.date(from: calendar.dateComponents([.year, .month], from: now)),
            let lastMonthStart = calendar.date(byAdding: .month, value: -1, to: thisMonthStart) else { return today }
      return (lastMonthStart, thisMonthStart)

    case .thisYear:
      guard let start = calendar.date(from: calendar.dateComponents([.year], from: now)) else { return today }
      return (start, now)

    case .lastYear:
      guard let thisYearStart = calendar.date(from: calendar.dateComponents([.year], from: now)),
            let lastYearStart = calendar.date(byAdding: .year, value: -1, to: thisYearStart) else { return today }
      return (lastYearStart, thisYearStart)

    case .last7Days:
      guard let start = calendar.date(byAdding: .day, value: -7, to: now) else { return today }
      return (start, now)

    case .last30Days:
      guard let start = calendar.date(byAdding: .day, value: -30, to: now) else { return today }
      return (start, now)

    case .last90Days:
      guard let start = calendar.date(byAdding: .day, value: -90, to: now) else { return today }
      return (start, now)

    case .custom:
      guard let customStart = customStart, let customEnd = customEnd else {
        return today
      }
      return (customStart, customEnd)
    }
  }
  
  /// Create a readable description of the period
  static func description(for timeRange: TimeRange, start: Date? = nil, end: Date? = nil) -> String {
    let formatter = DateFormatter()
    formatter.dateStyle = .medium
    formatter.timeStyle = .none
    
    switch timeRange {
    case .today:
      return "Today"
    case .yesterday:
      return "Yesterday"
    case .thisWeek:
      return "This week"
    case .lastWeek:
      return "Last week"
    case .thisMonth:
      return "This month"
    case .lastMonth:
      return "Last month"
    case .thisYear:
      return "This year"
    case .lastYear:
      return "Last year"
    case .last7Days:
      return "Last 7 days"
    case .last30Days:
      return "Last 30 days"
    case .last90Days:
      return "Last 90 days"
    case .custom:
      if let start = start, let end = end {
        return "\(formatter.string(from: start)) - \(formatter.string(from: end))"
      }
      return "Custom period"
    }
  }
}
