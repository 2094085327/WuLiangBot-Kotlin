# Changelog

## [2.4.0](https://github.com/2094085327/WuLiangBot-Kotlin/compare/2.3.0...2.4.0) (2026-10-23)


### ✨ Features

* **warframe:** 新增仲裁查询与排期图片缓存 ([9c272a8](https://github.com/2094085327/WuLiangBot-Kotlin/commit/9c272a8828183cfcd809d4dcdf91b7831b9b8bf2))

## [2.3.0](https://github.com/2094085327/WuLiangBot-Kotlin/compare/2.2.1...2.3.0) (2026-10-22)


### ✨ Features

* **bounty:** 新增六类平原赏金查询指令 ([31fa92b](https://github.com/2094085327/WuLiangBot-Kotlin/commit/31fa92bb65a3bef8acb6abe9957daf99dbc6c772))
* **conquest:** 更新科研数据配置 ([42e2f7e](https://github.com/2094085327/WuLiangBot-Kotlin/commit/42e2f7ebf74d1c8cde02631c8792852581663f37))
* **warframe:** 接入 Wiki 信条武器与终幕武器轮换数据 ([0de289e](https://github.com/2094085327/WuLiangBot-Kotlin/commit/0de289e36f055235ff727496740810fb5100a0dc))
* **warframe:** 新增每日成交排行榜与按需历史查询 ([0293e33](https://github.com/2094085327/WuLiangBot-Kotlin/commit/0293e33723cd14844a0aa15d0a4b44146da79505))
* **warframe:** 新增物品价格走势命令及图片生成 ([0f5d267](https://github.com/2094085327/WuLiangBot-Kotlin/commit/0f5d267b2398b163a86d21f2a4d08d6aff92babc))
* **warframe:** 添加世界状态解析、赏金轮换生成器及多源词库合并 ([2e1bcf3](https://github.com/2094085327/WuLiangBot-Kotlin/commit/2e1bcf36ce62697fb7a68f968bc111c0a1f8e901))


### 🐛 Bug Fixes

* **worldstate:** 补充世界状态周期计算模块 ([0eac077](https://github.com/2094085327/WuLiangBot-Kotlin/commit/0eac0777778bf3be8cc1451c728ad767070eb9ab))


### ♻️ Code Refactoring

* **translation:** 翻译词库导出加载改为流式处理以降低内存占用 ([56dd1ec](https://github.com/2094085327/WuLiangBot-Kotlin/commit/56dd1ec88aa6f40bb17446d30c5ba373acbc30c8))
* **warframe:** 拆分指令、查询服务与世界状态解析模块 ([8b56c47](https://github.com/2094085327/WuLiangBot-Kotlin/commit/8b56c471d9030043d2fb6469e4d80e46a7d00ae2))
* **worldstate:** 修复并统一平原周期状态查询为单次快照获取 ([e629955](https://github.com/2094085327/WuLiangBot-Kotlin/commit/e62995540e0326abf576402202e953dceada4dc8))


### 📦 Build System

* **playwright:** 按平台裁剪Playwright驱动以减小最终 JAR 体积 ([b7bcb7d](https://github.com/2094085327/WuLiangBot-Kotlin/commit/b7bcb7d0a757c697a34052edb34db57c42d5b109))

## [2.2.1](https://github.com/2094085327/WuLiangBot-Kotlin/compare/2.2.0...2.2.1) (2026-10-01)


### 🐛 Bug Fixes

* **warframe:** 修复灵化数据缺失并调整数据位置 ([3f62a11](https://github.com/2094085327/WuLiangBot-Kotlin/commit/3f62a11534ee88bf5f722b3c151d318b8bd244fc))

## [2.2.0](https://github.com/2094085327/WuLiangBot-Kotlin/compare/2.1.0...2.2.0) (2026-10-01)


### ✨ Features

* **system,warframe:** 添加Warframe市场词库V2版本支持和帮助详情功能 ([5d87791](https://github.com/2094085327/WuLiangBot-Kotlin/commit/5d8779187a2fa828069ca8cc21aef7f803ce6c03))
* **system,warframe:** 添加Warframe市场词库V2版本支持和帮助详情功能 ([1ca0d26](https://github.com/2094085327/WuLiangBot-Kotlin/commit/1ca0d2646f75ebbb5e52213d654f7029f101fd97))
* **warframe,system,common:** 添加Warframe周常信息，添加Help详情，添加腾讯云COS生命周期管理功能 ([bd6fa05](https://github.com/2094085327/WuLiangBot-Kotlin/commit/bd6fa05835713f00b254ed3a84648f2d08bab60d))
* **warframe,system,common:** 添加Warframe周常信息，添加Help详情，添加腾讯云COS生命周期管理功能 ([857b655](https://github.com/2094085327/WuLiangBot-Kotlin/commit/857b6559363fc1b90fa0d977bb61c7c4cdcb6e27))
* **warframe:** 为Warframe增加1999日历功能 ([467a2e7](https://github.com/2094085327/WuLiangBot-Kotlin/commit/467a2e702c88a38492e0d2836193ac3272d4268d))
* **warframe:** 为Warframe增加1999日历功能 ([e46d6c6](https://github.com/2094085327/WuLiangBot-Kotlin/commit/e46d6c6a58cfb35c0bba0e17714b81d56239931e))
* **warframe:** 优化Warframe市场订单查询功能 ([86dca38](https://github.com/2094085327/WuLiangBot-Kotlin/commit/86dca38fd5e2718e983528ea7ba25e62a841d76c))
* **warframe:** 优化Warframe市场订单查询功能 ([5b6e782](https://github.com/2094085327/WuLiangBot-Kotlin/commit/5b6e7821bc6687019ac338d55b873da8cf43ce9a))
* **warframe:** 增加科研任务查询 ([66600a8](https://github.com/2094085327/WuLiangBot-Kotlin/commit/66600a829591614edb7b5528539e77114c741db9))
* **warframe:** 增加科研任务查询 ([11861ae](https://github.com/2094085327/WuLiangBot-Kotlin/commit/11861ae09be86751e3b6fa21983f9eb59caa9d5a))
* **warframe:** 重构紫卡与玄骸市场查询 ([2c4be38](https://github.com/2094085327/WuLiangBot-Kotlin/commit/2c4be382e2fa8c9a1c39008fe51ce8f03edf562e))
* **warframe:** 重构紫卡与玄骸市场查询 ([13d6478](https://github.com/2094085327/WuLiangBot-Kotlin/commit/13d6478a846d5c6aefa34812507c7d660d5fb33b))


### 🐛 Bug Fixes

* **qq-adapter:** 修复QQ消息@机器人检测和前缀剥离逻辑 ([e903e9a](https://github.com/2094085327/WuLiangBot-Kotlin/commit/e903e9a732da5fc99b71e803c5e2bb45cbaf0173))
* **qq-adapter:** 修复QQ消息@机器人检测和前缀剥离逻辑 ([8c7f94d](https://github.com/2094085327/WuLiangBot-Kotlin/commit/8c7f94dac6e80108f55059dfe93d5d0b425501fc))
* **qq-adapter:** 修复QQ消息处理中的图片消息解析问题 ([d284d33](https://github.com/2094085327/WuLiangBot-Kotlin/commit/d284d330912a667bb4ba70d13f544e85756274d6))
* **qq-adapter:** 修复QQ消息处理中的图片消息解析问题 ([86a4ce6](https://github.com/2094085327/WuLiangBot-Kotlin/commit/86a4ce65529bb64d14edc5bad1aa53187fd879c5))
* **riven:** 添加缺少的文件 ([6593000](https://github.com/2094085327/WuLiangBot-Kotlin/commit/659300047342100dfc9ecee27e336b4687c700d3))
* **warframe:** 提交缺少的科研任务文件 ([c7acd06](https://github.com/2094085327/WuLiangBot-Kotlin/commit/c7acd062221929d18326fac390a73e144b0170e1))
* **warframe:** 提交缺少的科研任务文件 ([c54eb95](https://github.com/2094085327/WuLiangBot-Kotlin/commit/c54eb95b1a57330d805855804227a47fad6e752a))
* **warframe:** 添加1999日历数据类 ([ea22cbd](https://github.com/2094085327/WuLiangBot-Kotlin/commit/ea22cbda36e8885ef7f5b892a2244f4ffdb9c7cd))
* **warframe:** 添加1999日历数据类 ([30ce03d](https://github.com/2094085327/WuLiangBot-Kotlin/commit/30ce03d3186bacde6f5122df1b0f967549a6df2e))
* **warframe:** 添加未提交的Warframe市场数据获取方法 ([8f9b6d9](https://github.com/2094085327/WuLiangBot-Kotlin/commit/8f9b6d93e70dfb967cc7d8c01a1f42cb56f89777))
* **warframe:** 添加未提交的Warframe市场数据获取方法 ([a9c5004](https://github.com/2094085327/WuLiangBot-Kotlin/commit/a9c50046c7d07944213f3687201d1d685abb0429))


### ⚡ Performance Improvements

* **warframe:** 优化紫卡与玄骸查询性能及代码结构 ([91cfe17](https://github.com/2094085327/WuLiangBot-Kotlin/commit/91cfe17bdcc8d737243ee0226773841f873a4ac1))


### ♻️ Code Refactoring

* **warframe:** 调整wm查询过滤，修改翻译，修复多虚空商人的索引问题 ([868e3e2](https://github.com/2094085327/WuLiangBot-Kotlin/commit/868e3e22a61e482b2739e6a5651d81cb6c8220d7))
* **warframe:** 调整wm查询过滤，修改翻译，修复多虚空商人的索引问题 ([8701fb5](https://github.com/2094085327/WuLiangBot-Kotlin/commit/8701fb5ede3e6813dfc579dbdaeb2d32d97fff9c))
* **warframe:** 迁移统一词库并完善世界状态解析与缓存 ([6d29afd](https://github.com/2094085327/WuLiangBot-Kotlin/commit/6d29afd2952ce7d3dd87f5120ca4e907bb4238f9))

## [2.1.0](https://github.com/2094085327/WuLiangBot-Kotlin/compare/2.0.3...2.1.0) (2026-06-24)


### ✨ Features

* **qq-adapter:** 增加对QQ适配器全量消息接收的支持 ([9d5de4a](https://github.com/2094085327/WuLiangBot-Kotlin/commit/9d5de4aa372dbe76cb471ca165007e6f957943a7))
* **qq-adapter:** 增加对QQ适配器全量消息接收的支持 ([5b6adb4](https://github.com/2094085327/WuLiangBot-Kotlin/commit/5b6adb40016b5b5cbff900a0205ed94877b03188))


### 🐛 Bug Fixes

* **warframe:** 修复getWmPrice方法参数错误导致的执行失败 ([99e36e0](https://github.com/2094085327/WuLiangBot-Kotlin/commit/99e36e03b4bb09b192f07f93cd179562d95a6d48))
* **warframe:** 修复getWmPrice方法参数错误导致的执行失败 ([5d69815](https://github.com/2094085327/WuLiangBot-Kotlin/commit/5d69815a024e7160e6738f8321682033cf76a75e))


### ♻️ Code Refactoring

* **utils:** 优化灵化武器轮换逻辑计算 ([750424d](https://github.com/2094085327/WuLiangBot-Kotlin/commit/750424d8646a1726ef6b2bbe12fd6043032c1028))

## [2.0.3](https://github.com/2094085327/WuLiangBot-Kotlin/compare/2.0.2...2.0.3) (2026-06-16)


### 🐛 Bug Fixes

* **admin:** 修复重启功能并改进README文档 ([9abbb2a](https://github.com/2094085327/WuLiangBot-Kotlin/commit/9abbb2ae4a7ccdf38e0fbf7867ad4fe3df3a1351))
* **warframe:** 更新 Warframe 状态 API 地址 ([5cd4a7e](https://github.com/2094085327/WuLiangBot-Kotlin/commit/5cd4a7ed5fd0ae2dd3d31fad6113a12d8f9af95e))

## [2.0.2](https://github.com/2094085327/WuLiangBot-Kotlin/compare/2.0.1...2.0.2) (2026-04-22)


### 🐛 Bug Fixes

* **parser:** 添加掉落数据解析相关接口和枚举 ([b90ba29](https://github.com/2094085327/WuLiangBot-Kotlin/commit/b90ba297cd0c9572bdee6233ec369790081af049))
* **warframe:** 一些未提交的文件 ([d9456d7](https://github.com/2094085327/WuLiangBot-Kotlin/commit/d9456d71e006316f4563b48ed28b0d2ff3513a91))
* **warframe:** 添加轮次枚举定义 ([76c7679](https://github.com/2094085327/WuLiangBot-Kotlin/commit/76c7679ceaa2d9a5e99f2ae2ffecf569108b6950))

## [2.0.1](https://github.com/2094085327/WuLiangBot-Kotlin/compare/2.0.0...2.0.1) (2026-04-22)


### 🐛 Bug Fixes

* **system:** 修复协程取消异常处理 ([315f31a](https://github.com/2094085327/WuLiangBot-Kotlin/commit/315f31ab93078cbc06e9827e694a27056a38f7bd))

## [2.0.0](https://github.com/2094085327/WuLiangBot-Kotlin/compare/v1.1.1...2.0.0) (2026-04-22)


### ⚠ BREAKING CHANGES

* **system:** 

### ♻️ Code Refactoring

* **system:** 重构命令系统和消息处理架构 ([3e6d395](https://github.com/2094085327/WuLiangBot-Kotlin/commit/3e6d395ceec4100356ba753231f7be4584eee894))
* **system:** 重构命令系统和消息处理架构 ([34c05e1](https://github.com/2094085327/WuLiangBot-Kotlin/commit/34c05e1ded2c9f4c24ec439ad7cdc965b9cfa45f))
