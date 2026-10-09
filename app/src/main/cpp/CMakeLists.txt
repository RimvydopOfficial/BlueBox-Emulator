cmake_minimum_required(VERSION 3.22.1)
project("bluebox_qemu_bridge")
add_library(bluebox_qemu_bridge SHARED
    bluebox_qemu_bridge.cpp
    bluebox_qemu_executor.cpp
)
find_library(log-lib log)
target_link_libraries(bluebox_qemu_bridge ${log-lib} dl)
