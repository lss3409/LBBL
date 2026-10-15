cmake_minimum_required(VERSION 3.22)
project(structurefinder C)

# v0.4.57：SeedFinder 结构查找内嵌（完全离线）。
# 种子破解（crack64/crack_mt/crack_auto）不编译——SEEDFINDER_NO_CRACK
# 裁剪 wrapper.c 中的 crack 导出段。
add_library(structurefinder SHARED
    seedfinder_wrapper.c
    structurefinder_jni.c
    viability.c
    Bfinders.c
    cubiomes/biomes.c
    cubiomes/layers.c
    cubiomes/generator.c
    cubiomes/finders.c
    cubiomes/util.c
    cubiomes/noise.c
    cubiomes/biomenoise.c
    cubiomes/quadbase.c
)
target_include_directories(structurefinder PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/cubiomes
)
target_compile_definitions(structurefinder PRIVATE SEEDFINDER_NO_CRACK)
