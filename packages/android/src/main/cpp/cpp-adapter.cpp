//
// cpp-adapter.cpp
//
// Library entry point for libNitroPackages.so on Android.
//
// Two responsibilities:
//   1. Implement JNI_OnLoad so fbjni / the Nitrogen-generated JNI bridges
//      (JHybridHealthKitSpec::registerNatives, etc.) wire up correctly.
//   2. Register the "HealthKit" HybridObject constructor with Nitro's
//      HybridObjectRegistry. The lambda is invoked every time JS calls
//      NitroModules.createHybridObject('HealthKit') — it constructs the
//      Kotlin `HealthKitModule()` (which triggers its own initHybrid() to
//      create the C++ counterpart) and returns a shared_ptr<HybridObject>
//      that aliases the C++ side of that Java instance.
//

#include <jni.h>
#include <memory>

#include <fbjni/fbjni.h>

#include <NitroModules/HybridObjectRegistry.hpp>
#include <NitroModules/JNISharedPtr.hpp>

#include "JHybridHealthKitSpec.hpp"
#include "NitroPackagesOnLoad.hpp"

using namespace facebook;
using namespace margelo::nitro;
using namespace margelo::nitro::healthkit;

namespace {

// JNI descriptor for the concrete Kotlin implementation that extends the
// Nitrogen-generated HybridHealthKitSpec abstract class.
constexpr const char* kHealthKitModuleClass = "io/github/n0ku/nitrohealthkit/HealthKitModule";

// Construct a fresh HealthKitModule on the Kotlin side and bridge it to a
// std::shared_ptr<HybridObject> that points at its C++ counterpart.
std::shared_ptr<HybridObject> createHealthKit() {
  // The Kotlin class has a no-arg constructor. Its `init` block invokes
  // initHybrid() which in turn calls JHybridHealthKitSpec::initHybrid(jThis)
  // on the C++ side, populating mHybridData with our JHybridHealthKitSpec.
  auto cls = jni::findClassStatic(kHealthKitModuleClass);
  auto ctor = cls->getConstructor<JHybridHealthKitSpec::javaobject()>();
  auto localInstance = cls->newObject(ctor);
  auto globalInstance = jni::make_global(localInstance);

  // Wrap the Java reference in a shared_ptr whose deleter holds onto the
  // global_ref (so the Kotlin object stays alive as long as the C++ shared_ptr
  // does). ref->cthis() returns the C++ part of the HybridClass.
  auto typedShared =
      JNISharedPtr::make_shared_from_jni<JHybridHealthKitSpec>(globalInstance);
  return std::static_pointer_cast<HybridObject>(typedShared);
}

}  // namespace

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* /*reserved*/) {
  // Delegate to the Nitrogen-generated initialise() which registers JNI
  // natives for the Spec classes.
  const jint nitroResult = margelo::nitro::healthkit::initialize(vm);
  if (nitroResult != JNI_VERSION_1_6) {
    return nitroResult;
  }

  // Once JNI is set up, register the HybridObject constructor. We do it here
  // (rather than in a static initialiser) because findClassStatic /
  // make_global require a fully attached JNI environment.
  HybridObjectRegistry::registerHybridObjectConstructor(
      "HealthKit",
      []() -> std::shared_ptr<HybridObject> { return createHealthKit(); });

  return JNI_VERSION_1_6;
}
