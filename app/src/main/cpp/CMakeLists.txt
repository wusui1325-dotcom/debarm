cmake_minimum_required(VERSION 3.22)
project(debarm-pty)

add_library(debarm-pty SHARED pty_bridge.cpp)
find_library(log-lib log)
target_link_libraries(debarm-pty PRIVATE ${log-lib})
target_compile_options(debarm-pty PRIVATE -Wall -Wextra -fvisibility=hidden)
