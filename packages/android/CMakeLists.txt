# Top-level CMake project for the NitroHealthkit shared library.
#
# CMAKE_SOURCE_DIR resolves to `packages/android/` because that's where this file lives,
# which is what Nitrogen's autolinking.cmake assumes for its relative `../nitrogen/...`
# paths (see `../nitrogen/generated/android/NitroHealthkit+autolinking.cmake`).

cmake_minimum_required(VERSION 3.22.1)
project(NitroHealthkit CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# 1. Declare the library *first* so the autolinking script can add target_sources to it.
add_library(
  NitroHealthkit
  SHARED
  src/main/cpp/cpp-adapter.cpp
)

# 2. Pull in the auto-generated Nitrogen sources / headers / link libs.
#    This file expects CMAKE_SOURCE_DIR to be `packages/android/` so its `../nitrogen/...`
#    paths resolve to `packages/nitrogen/...`.
include(${CMAKE_SOURCE_DIR}/../nitrogen/generated/android/NitroHealthkit+autolinking.cmake)
