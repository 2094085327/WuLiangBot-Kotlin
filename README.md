<p align="center">
  <a href="https://github.com/2094085327/WuLiangBot-Kotlin">
    <img src="resources/others/head.png" width="200" height="200" style="border-radius: 100px" alt="WuLiang">
  </a>
</p>

<div align="center">

# WuLiangBot-Kotlin

_✨
基于 [OneBot](https://github.com/howmanybots/onebot/blob/master/README.md) [Shiro](https://github.com/MisakaTAT/Shiro/blob/main/README.md)
QQ官方机器人Api Kotlin 实现 ✨_


</div>

<p align="center">
  <a href="https://raw.githubusercontent.com/hoshinonyaruko/gensokyo/main/LICENSE">
    <img src="https://img.shields.io/github/license/hoshinonyaruko/gensokyo" alt="license">
  </a>
    <img src="https://img.shields.io/badge/JDK-17+-brightgreen.svg?style=flat-square" alt="jdk-version">
  <a href="https://github.com/howmanybots/onebot/blob/master/README.md">
    <img src="https://img.shields.io/badge/OneBot-v11-blue?style=flat&logo=data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAEAAAABABAMAAABYR2ztAAAAIVBMVEUAAAAAAAADAwMHBwceHh4UFBQNDQ0ZGRkoKCgvLy8iIiLWSdWYAAAAAXRSTlMAQObYZgAAAQVJREFUSMftlM0RgjAQhV+0ATYK6i1Xb+iMd0qgBEqgBEuwBOxU2QDKsjvojQPvkJ/ZL5sXkgWrFirK4MibYUdE3OR2nEpuKz1/q8CdNxNQgthZCXYVLjyoDQftaKuniHHWRnPh2GCUetR2/9HsMAXyUT4/3UHwtQT2AggSCGKeSAsFnxBIOuAggdh3AKTL7pDuCyABcMb0aQP7aM4AnAbc/wHwA5D2wDHTTe56gIIOUA/4YYV2e1sg713PXdZJAuncdZMAGkAukU9OAn40O849+0ornPwT93rphWF0mgAbauUrEOthlX8Zu7P5A6kZyKCJy75hhw1Mgr9RAUvX7A3csGqZegEdniCx30c3agAAAABJRU5ErkJggg==" alt="WuLiang">
  </a>
</p>

<p align="center">
  <a href="#功能特性">功能特性</a>
  ·
  <a href="#快速开始">快速开始</a>
  ·
  <a href="#配置说明">配置说明</a>
  ·
  <a href="#贡献">贡献</a>
</p>

## 功能特性

WuLiangBot-Kotlin 是一个功能丰富的QQ机器人，集成了多个游戏和实用工具模块：

### 🎮 游戏娱乐
- **人生重开模拟器**: 体验不同的人生轨迹
- **原神模拟抽卡**: 模拟原神游戏的抽卡系统（已停止维护）
- **Warframe信息查询**: 提供全面的Warframe游戏信息

### 🌍 实用工具
- **天气查询**: 查询全球城市天气情况
- **地理信息查询**: 获取城市地理信息
- **缓存管理**: 动态清除和更新机器人缓存

### 🤖 机器人管理
- **指令管理**: 灵活的指令系统和权限控制
- **状态监控**: 实时监控机器人运行状态
- **数据统计**: 日活跃用户统计等功能

## 快速开始

### 环境要求
- JDK 17 或更高版本
- Maven 3.6+ (用于构建项目)
- MySQL 8.0+ (用于数据存储)

### 构建项目
#### 克隆项目
```bash
git clone https://github.com/2094085327/WuLiangBot-Kotlin.git
```
#### 进入项目目录
```bash
cd WuLiangBot-Kotlin
```
#### 使用Maven构建项目
```bash
./mvnw clean install
```

## 配置文件

在使用本机器人前需要配置配置文件,配置文件放置于`wuliang-admin/src/main/resources`目录下.
<details>
<summary>application.yml</summary>

```yaml
server:
  # SpringBoot 项目的运行端口即为客户端反向 Websocket 连接端口
  port: 5555

spring:
  mvc:
    pathmatch:
      matching-strategy: ant-path-matcher
  profiles.active: prod

  redis:
    host: 127.0.0.1
    port: 6379

frontend:
  port: 16666
  host: localhost

swagger:
  enabled: true
  pathMapping:

logging:
  logfile:
    # 日志文件名
    name: WuLiang
    # 日志文件路径
    root-path: logs
    # 日志文件最大大小
    max-size: 50MB
  level:
    bot.wuliang: debug
    # 日志文件配置
  config: classpath:logback-spring.xml

mybatis-plus:
  # xml扫描，多个目录用逗号或者分号分隔（告诉 Mapper 所对应的 XML 文件位置）
  mapper-locations: classpath*:mapper/*.xml, classpath:mapper/log/*.xml
  type-aliases-package: bot.wuliang.entity
  # 以下配置均有默认值,可以不设置
  global-config:
    db-config:
      #主键类型 AUTO:"数据库ID自增" INPUT:"用户输入ID",ID_WORKER:"全局唯一ID (数字类型唯一ID)", UUID:"全局唯一ID UUID";
      id-type: auto
      #字段策略 IGNORED:"忽略判断"  NOT_NULL:"非 NULL 判断")  NOT_EMPTY:"非空判断"
      field-strategy: NOT_EMPTY
      #数据库类型
      db-type: MYSQL
  configuration:
    default-executor-type: simple
    use-generated-keys: true
    # 是否开启自动驼峰命名规则映射:从数据库列名到Java属性驼峰命名的类似映射
    map-underscore-to-camel-case: true
    # 如果查询结果中包含空值的列，则 MyBatis 在映射的时候，不会映射这个字段
    call-setters-on-nulls: true
    # 这个配置会将执行的sql打印出来，在开发或测试的时候可以用
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
    # 关闭日志
    # log-impl: org.apache.ibatis.logging.nologging.NoLoggingImpl
```

</details>

<details>
<summary>application-template.yml</summary>

```yaml
# Web相关配置
web_config:
  # 服务端口
  port: 5555
  # 图床路径
  img_bed_path: "https://your-image-bed-domain.com"


# 七牛云对象存储配置
qi_niu:
  cos:
    # 七牛云访问密钥
    access_key: "your_qiniu_access_key"
    # 七牛云私钥
    secret_key: "your_qiniu_secret_key"
    # 存储桶名称
    bucket: "your_bucket_name"
    # 七牛云域名
    url: "http://your-domain.com/"
    # 路径前缀
    path: "img/"
    # 策略过期时间(秒)
    policy_expire: 3600

# 腾讯云对象存储配置
tencent:
  cos:
    # 腾讯云密钥ID
    secret_id: "your_tencent_secret_id"
    # 腾讯云密钥
    secret_key: "your_tencent_secret_key"
    # 地域
    region: "your_region"
    # 存储桶名称
    bucket: "your_bucket_name"
    # 腾讯云COS域名
    url: "https://your-cos-domain.com/"
    # 路径前缀
    path: "img/"
    # 策略过期时间(秒)
    policy_expire: 3600
    # 编码格式
    code_format: "UTF-8"
 
# 百度云平台OCR配置
aip_ocr:
  # 应用ID
  client_id: "your_baidu_client_id"
  # 密钥
  client_secret: "your_baidu_client_secret"

# 图片加载配置
image_load:
  # 图片加载密钥
  key: "your_image_load_key"


# 天气API配置
weather:
  # 天气API密钥
  key: "your_weather_key"

# GitHub配置
github:
  # GitHub用户名
  owner: "your_github_owner"
  # 仓库名称
  repo: "your_repo_name"
  # GitHub访问令牌
  access-token: "your_github_access_token"

# 项目自定义配置
wuLiang:
  bot-config:
    # QQ开放平台appid
    appid: "your_appid"
    # QQ开放平台token
    token: "your_token"
    # QQ开放平台secret
    secret: "your_secret"
  config:
    # 图床路径，用于自动更新图片
    gallery: "https://your-gallery-domain.com/"
    # 原神本地图片路径
    localPath: "resources/Image/Genshin/"
    # 后台管理账号密码
    userName: "your_admin_username"
    password: "your_admin_password"

# Swagger API文档配置
swagger:
  # 是否启用Swagger
  enabled: true
  # 路径映射
  pathMapping:

# Spring框架配置
##配置数据源
spring:
  # MongoDB配置
  data:
    mongodb:
      # MongoDB连接URI
      uri: mongodb://username:password@host:port/database
  # Servlet配置
  servlet:
    multipart:
      # 是否启用文件上传
      enabled: true
      # 最大文件大小
      max-file-size: 1024MB
      # 最大请求大小
      max-request-size: 1024MB
  # Redis配置
  redis:
    # Redis主机地址
    host: your_redis_host
    # Redis端口
    port: your_redis_port
    # Redis密码
    password: "your_redis_password"
  # Thymeleaf模板引擎配置
  thymeleaf:
    # 模板前缀
    prefix: classpath:/templates/
    # 编码格式
    encoding: UTF-8
    # 模板模式
    mode: HTML5
    # 是否启用缓存
    cache: false
  # 数据源配置
  datasource:
    # 数据库驱动类名
    driver-class-name: org.mariadb.jdbc.Driver
    # 数据库连接URL
    url: jdbc:mariadb://your_host:port/database
    # 数据库用户名
    username: your_database_username
    # 数据库密码
    password: your_database_password
    # 数据源类型
    type: com.alibaba.druid.pool.DruidDataSource
    # 初始化连接数
    initialSize: 5
    # 最小空闲连接数
    minIdle: 5
    # 最大活跃连接数
    maxActive: 20
    # 获取连接等待超时的时间
    maxWait: 60000
    # 空闲时是否验证连接有效性
    testWhileIdle: true
    # 获取连接时是否验证有效性
    testOnBorrow: true
    # 配置间隔多久才进行一次检测，检测需要关闭的空闲连接(毫秒)
    timeBetweenEvictionRunsMillis: 60000
    # 配置一个连接在池中最小生存的时间(毫秒)
    minEvictableIdleTimeMillis: 300000
    # Druid连接池配置
    druid:
      test-on-borrow: false
      # SQL验证语句
      validationQuery: SELECT 1
      testWhileIdle: true
      testOnBorrow: true
      testOnReturn: false
    # 防火墙配置
    filter:
      wall:
        config:
          # 是否显示允许的SQL
          show-allow: true
    # Web统计过滤器配置
    web-stat-filter:
      # 是否启用
      enabled: true
      # URL匹配模式
      url-pattern: /*
      # 设置不统计哪些URL
      exclusions: "*.js,*.gif,*.jpg,*.png,*.css,*.ico,/druid/*"
      # 是否开启session统计功能
      session-stat-enable: true
      # session统计最大个数
      session-stat-max-count: 100
    # StatViewServlet配置(用于监控页面)
    stat-view-servlet:
      # 是否启用
      enabled: true
      # URL匹配模式
      url-pattern: /druid/*
      # 是否允许重置统计
      reset-enable: true
      # 监控页面登录用户名
      login-username: your_druid_username
      # 监控页面登录密码
      login-password: your_druid_password
      # 允许访问的IP地址
      allow: 127.0.0.1
  # RabbitMQ消息队列配置
  rabbitmq:
    # RabbitMQ主机地址
    host: your_rabbitmq_host
    # RabbitMQ端口
    port: your_rabbitmq_port
    # RabbitMQ用户名
    username: your_rabbitmq_username
    # RabbitMQ密码
    password: your_rabbitmq_password
    # 消息发送到交换机确认机制
    publisher-confirm-type: correlated
    # 消息发送到队列确认机制
    publisher-returns: true
    # 虚拟主机
    virtual-host: /
    # 监听器配置
    listener:
      simple:
        # 消费者最小数量
        concurrency: 10
        # 最大消费者数量
        max-concurrency: 10
        # 每次处理消息数量
        prefetch: 1
        # 启动时是否默认启动容器
        auto-startup: true
        # 被拒绝时重新进入队列
        default-requeue-rejected: true

# MyBatis-Plus配置
mybatis-plus:
  # Mapper XML文件位置
  mapper-locations: classpath*:mapper/*.xml, classpath:mapper/log/*.xml
  # 别名包扫描路径
  type-aliases-package: bot.wuliang.entity
  # 全局配置
  global-config:
    db-config:
      # 主键类型
      id-type: auto
      # 字段验证策略
      field-strategy: NOT_EMPTY
      # 数据库类型
      db-type: MYSQL
  # MyBatis原生配置
  configuration:
    # 默认执行器类型
    default-executor-type: simple
    # 允许JDBC支持自动生成主键
    use-generated-keys: true
    # 是否开启驼峰命名规则映射
    map-underscore-to-camel-case: true
    # 当查询结果为空值时，是否调用Setter方法
    call-setters-on-nulls: true

# 日志配置
logging:
  level:
    # 项目日志级别
    bot.demo.txbot: error
    # 腾讯云SDK日志级别
    com.qcloud: error

```
</details>

### 启动项目
#### 方式一：使用Maven插件运行
```bash
./mvnw spring-boot:run
```

#### 方式二：构建后运行jar包
```bash

./mvnw clean package java -jar wuliang-admin/target/*.jar
```
## 配置说明

### 主要模块

| 模块 | 功能 |
|------|------|
| `wuliang-admin` | 主应用入口和管理界面 |
| `wuliang-common` | 公共工具和基础类 |
| `wuliang-genshin` | 原神相关功能（已停止维护） |
| `wuliang-game` | 人生重开模拟器游戏 |
| `wuliang-system` | 系统核心功能 |
| `wuliang-warframe` | Warframe游戏信息查询 |
| `wuliang-weather` | 天气和地理信息查询 |

### 数据库配置
项目使用MySQL数据库，相关SQL脚本位于 [sql/](file://E:\Learning\bots\Wuliang-Bot\Tencent-Bot-Kotlin\doc\bot_log.sql) 目录中。

### 常用指令

<details>
<summary>点击查看完整指令列表</summary>

#### 基础指令
- `帮助/菜单/help` - 获取帮助信息
- `天气 城市` - 查询城市天气
- `地理 城市` - 查询城市地理信息
- `清除缓存` - 清除机器人缓存
- `更新资源` - 更新机器人资源
- `无量姬状态` - 查看机器人运行状态
- `重载指令` - 重新加载指令列表
- `日活` - 查看日活跃用户

#### Warframe指令
- `更新词库` - 更新Warframe词库
- `wm 物品名` - 查询Warframe Market物品
- `wr 物品名 词条` - 查询紫卡信息
- `wl 物品名 属性` - 查询玄骸信息
- `裂缝` - 查看普通裂缝信息
- `钢铁裂缝` - 查看钢铁裂缝信息
- `奸商` - 查看虚空商人信息
- `突击` - 查看每日突击任务
- `执刑官` - 查看每周突击任务
- 等等...

#### 人生重开模拟器
- `重开` - 开始游戏
- `天赋 1 2 3` - 选择天赋
- `分配 属性值` - 分配属性点
- `随机` - 随机分配属性
- `继续` - 继续游戏进程

#### 原神相关（已停止维护）
- `全部卡池` - 查看所有卡池
- `启用卡池` - 更改模拟抽卡卡池
- `十连` - 进行十连抽卡
- `历史记录` - 查看抽卡历史
</details>

## 特别鸣谢

- [`Kloping/qqpd-bot-java`](https://github.com/MisakaTAT/Shiro/blob/main/README.md): 本项目采用了Kloping编写的机器人SDK，使用此框架实现对QQ机器人官方的对接。

## 许可证

本项目采用 [GNU General Public License v3.0](LICENSE) 许可证。

## 贡献

欢迎提交 Issue 和 Pull Request 来帮助改进项目。
