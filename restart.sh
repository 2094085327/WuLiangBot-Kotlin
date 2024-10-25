#!/bin/bash

# 停止Java应用程序
sudo cat resources/others/app.pid | xargs kill
sudo kill "$1"

# 等待一些时间，确保进程已经终止
sleep 5

# 启动Java应用程序
nohup java -jar "$1" >> "$2" 2>&1 &