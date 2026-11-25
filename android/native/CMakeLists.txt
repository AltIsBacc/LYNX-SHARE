# LYNX SHARE for Android (docs/ANDROID.md): the JUCE-free core and a small JNI bridge, as the one shared library the
# Gradle project in android/ loads. Included by the top-level CMakeLists.txt when it is built for Android.
add_library(lynx_android SHARED
    AndroidEngine.cpp
    Jni.cpp)
target_include_directories(lynx_android PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})
target_link_libraries(lynx_android PRIVATE lynx_core)
