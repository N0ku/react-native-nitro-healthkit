import Foundation

@objcMembers
public class NitroHealthkitObjcBridge: NSObject {
  public override init() {
    super.init()
  }

  /// Create and return a retained unsafe pointer to a `HybridHealthKitSpec_cxx` that wraps
  /// the Swift `HealthKitModule` implementation. The pointer is retained and must be
  /// transferred to C++ which will take ownership via `create_std__shared_ptr_HybridHealthKitSpec_`.
  @objc
  public func createCxxWrapper() -> UnsafeMutableRawPointer {
    let impl = HealthKitModule()
    let wrapper = HybridHealthKitSpec_cxx(impl)
    return wrapper.toUnsafe()
  }
}
