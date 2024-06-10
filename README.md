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
  <a href="https://github.com/2094085327/WuLiangBot-Kotlin/blob/master/README.md">文档</a>
  ·
  <a href="https://github.com/2094085327/WuLiangBot-Kotlin/releases">下载</a>
  ·
  <a href="https://github.com/2094085327/WuLiangBot-Kotlin/blob/master/README.md">开始使用</a>
  ·
  <a href="https://github.com/2094085327/WuLiangBot-Kotlin">参与贡献</a>
</p>

## 特别鸣谢

- [`MisakaTAT/Shiro`](https://github.com/MisakaTAT/Shiro/blob/main/README.md):
  本项目采用了MisakaTAT编写的框架,使用此框架实现对QQ机器人官方的实现.

## 配置文件

<details>
<summary>application.yml</summary>

```yaml
 server:
  # SpringBoot 项目的运行端口即为客户端反向 Websocket 连接端口
  port: 5555

shiro:
  ws:
    # 该配置为正向连接示例
    client:
      enable: false
      url: "ws://your-domain:port"
    # 该配置为反向连接示例
    server:
      enable: true
      url: "/ws/shiro"

# web服务配置
web_config:
  port:  # 端口
  img_bed_path:  #图床地址 无图床可以使用Telegraph-Image（自行搜索搭建，无需服务器），若使用其他图床需要修改 WebImgUtil.kt 中的代码

#gensokyo链接配置 可前往gensokyo 官方文档查看配置教程
gensokyo_config:
  port:  #端口

# 天气接口配置
weather:
  key: # 天气接口key，前往 和风天气获取 https://dev.qweather.com/  免费

# github配置 用于更新机器人资源 可以前往仓库自行fork更新
github:
  owner: "2094085327"
  repo: "Tencent-Bot-Kotlin"
  access-token: # 如果使用"Tencent-Bot-Kotlin"仓库进行更新，请向邮箱 2094085327@qq.com 发送申请获取 access-token


##配置数据源
spring:
  thymeleaf:
    prefix: classpath:/templates/
    encoding: UTF-8
    mode: HTML5
    cache: false
  datasource:
    driver-class-name: org.mariadb.jdbc.Driver # Jdbc 驱动
    url:  # 数据库地址
    username: #数据库用户名
    password: #数据库密码
    type: com.alibaba.druid.pool.DruidDataSource # 自定义数据源
    initialSize: 5
    minIdle: 5
    maxActive: 20
    maxWait: 60000
    testWhileIdle: true
    testOnBorrow: true
    timeBetweenEvictionRunsMillis: 60000
    minEvictableIdleTimeMillis: 300000
    druid:
      test-on-borrow: false
      validationQuery: SELECT 1
      testWhileIdle: true
      testOnBorrow: true
      testOnReturn: false
    filter:
      wall:
        config:
          show-allow: true
    #3.基础监控配置
    web-stat-filter:
      enabled: true
      url-pattern: /*
      #设置不统计哪些URL
      exclusions: "*.js,*.gif,*.jpg,*.png,*.css,*.ico,/druid/*"
      session-stat-enable: true
      session-stat-max-count: 100
    stat-view-servlet:
      enabled: true
      url-pattern: /druid/*
      reset-enable: true
      #设置监控页面的登录名和密码
      login-username: admin
      login-password: admin
      allow: 127.0.0.1

    # redis配置
    redis:
      host: 127.0.0.1
      port: 6379


# mybatis-plus相关配置
mybatis-plus:
  # xml扫描，多个目录用逗号或者分号分隔（告诉 Mapper 所对应的 XML 文件位置）
  mapper-locations: classpath:mapper/*.xml
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
    # 是否开启自动驼峰命名规则映射:从数据库列名到Java属性驼峰命名的类似映射
    map-underscore-to-camel-case: true
    # 如果查询结果中包含空值的列，则 MyBatis 在映射的时候，不会映射这个字段
    call-setters-on-nulls: true
    # 这个配置会将执行的sql打印出来，在开发或测试的时候可以用
    #    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
    # 关闭日志
    log-impl: org.apache.ibatis.logging.nologging.NoLoggingImpl
```
</details>

## 接口

- [x] 天气地理 API
- [x] 原神抽卡数据 API
- [x] Warframe Market API

## 关于 ISSUE

以下 ISSUE 会被直接关闭

- 提交 BUG 不使用 Template
- 询问已知问题
- 提问找不到重点
- 重复提问

> 请注意, 开发者并没有义务回复您的问题. 您应该具备基本的提问技巧。

## 性能

有待测试