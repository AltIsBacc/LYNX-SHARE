add_library(lynx_android SHARED
    AndroidEngine.cpp
    Jni.cpp)
target_include_directories(lynx_android PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})
target_link_libraries(lynx_android PRIVATE lynx_core)

target_compile_definitions(lynx_ysfx PRIVATE YSFX_NO_FTS)
target_compile_options(lynx_ysfx PRIVATE "$<$<COMPILE_LANGUAGE:C,CXX>:-include${CMAKE_CURRENT_SOURCE_DIR}/compat/BionicPthread.h>")
