#!/bin/bash

# 停止Java应用程序
sudo cat resources/others/app.pid | xargs kill

# 等待一些时间，确保进程已经终止
sleep 5

# 启动Java应用程序
nohup java -jar Tencent-Bot-Kotlin-0.0.1-SNAPSHOT.jar >> app.log 2>&1 &