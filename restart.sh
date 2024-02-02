#!/bin/bash

# 停止Java应用程序
pkill -f Tencent-Bot-Kotlin-0.0.1-SNAPSHOT.jar

# 等待一些时间，确保进程已经终止
sleep 5

# 启动Java应用程序
nohup java -jar Tencent-Bot-Kotlin-0.0.1-SNAPSHOT.jar &
