//
//  NitroHealthkit.mm
//  NitroHealthkit
//

#import <Foundation/Foundation.h>

// Include C++ headers FIRST so Swift can see the margelo namespace
#include <NitroModules/HybridObjectRegistry.hpp>
#include "HybridHealthKitSpecSwift.hpp"
#include "HealthData.hpp"

// NOW include Swift header (it depends on the C++ types above)
#import "NitroHealthkit-Swift.h"

@interface NitroHealthkitAutoLoader : NSObject
@end

@implementation NitroHealthkitAutoLoader

+ (void)load {
  using namespace margelo::nitro::healthkit;
  using namespace margelo::nitro;

  HybridObjectRegistry::registerHybridObjectConstructor(
    "HealthKit",
    []() -> std::shared_ptr<HybridObject> {
      // Create Swift wrapper that returns an unsafe retained pointer to the
      // HybridHealthKitSpec_cxx Swift wrapper.
      NitroHealthkitObjcBridge* bridge = [[NitroHealthkitObjcBridge alloc] init];
      void* swiftUnsafePtr = [bridge createCxxWrapper];

      // Convert the Swift unsafe pointer into a std::shared_ptr<HybridHealthKitSpec>
      auto cppShared = margelo::nitro::healthkit::bridge::swift::create_std__shared_ptr_HybridHealthKitSpec_(swiftUnsafePtr);

      // Upcast to a generic HybridObject pointer and return it.
      return std::static_pointer_cast<HybridObject>(cppShared);
    }
  );
}

@end
