require "json"

package = JSON.parse(File.read(File.join(__dir__, "package.json")))

Pod::Spec.new do |s|
  s.name         = "NitroHealthkit"
  s.version      = package["version"]
  s.summary      = package["description"]
  s.homepage     = "https://github.com/N0ku/react-native-nitro-healthkit"
  s.license      = package["license"]
  s.authors      = package["author"]
  
  s.platforms    = { :ios => "13.4" }
  s.source       = { :git => "https://github.com/N0ku/react-native-nitro-healthkit.git", :tag => "#{s.version}" }
  
  s.source_files = [
    # Implementation (Swift)
    "ios/**/*.{swift}",
    # Autolinking/Registration (Objective-C++)
    "ios/**/*.{m,mm}",
    # Implementation (C++ objects)
    "cpp/**/*.{hpp,cpp}",
  ]
  
  s.frameworks = "HealthKit"
  
  s.pod_target_xcconfig = {
    # C++ compiler flags, mainly for folly.
    "GCC_PREPROCESSOR_DEFINITIONS" => "$(inherited) FOLLY_NO_CONFIG FOLLY_CFG_NO_COROUTINES"
  }
  
  # Load the Nitrogen autolinking
  load 'nitrogen/generated/ios/NitroHealthkit+autolinking.rb'
  add_nitrogen_files(s)
  
  s.dependency 'React-jsi'
  s.dependency 'React-callinvoker'
  install_modules_dependencies(s)
end
