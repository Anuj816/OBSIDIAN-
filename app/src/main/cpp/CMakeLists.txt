cmake_minimum_required(VERSION 3.22.1)
project("obsidian_projectm")

# Set projectM CMake options
set(ENABLE_SDL_UI OFF CACHE BOOL "")
set(BUILD_TESTING OFF CACHE BOOL "")
set(ENABLE_INSTALL OFF CACHE BOOL "")
set(ENABLE_PLAYLIST OFF CACHE BOOL "")
set(ENABLE_GLES ON CACHE BOOL "")
set(BUILD_SHARED_LIBS OFF CACHE BOOL "")
set(ENABLE_SYSTEM_GLM OFF CACHE BOOL "")
set(ENABLE_DEBUG_POSTFIX OFF CACHE BOOL "" FORCE)
set(CMAKE_DEBUG_POSTFIX "" CACHE STRING "" FORCE)

add_subdirectory(projectm-4.1.7)

set(CMAKE_DEBUG_POSTFIX "" CACHE STRING "" FORCE)

add_library(
        obsidian_projectm
        SHARED
        obsidian_projectm_jni.cpp)

set_target_properties(obsidian_projectm PROPERTIES
        OUTPUT_NAME "obsidian_projectm"
        DEBUG_POSTFIX "")

target_include_directories(obsidian_projectm PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}/projectm-4.1.7/src/api/include
)

find_library(log-lib log)
find_library(GLES3-lib GLESv3)
find_library(EGL-lib EGL)

target_link_libraries(
        obsidian_projectm
        projectM
        ${log-lib}
        ${GLES3-lib}
        ${EGL-lib})
