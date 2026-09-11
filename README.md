# 轻触连点器 AutoTapper v2

Android 无 ROOT 连点器，基于 AccessibilityService 执行点击/滑动手势，支持普通连点、多任务方案、目标 App 绑定、预约定时、重复规则和可折叠悬浮窗。

## 已实现

- 任务列表与多套绑定方案
- 绑定目标 App / 页面录点
- 点击与滑动动作混合录制
- 点击速率 1–1000 次/分钟
- 随机附加间隔
- 循环次数与最长运行时长限制
- 一次 / 每天 / 工作日 / 每周定时规则
- 到点尝试唤起目标 App 并自动执行
- 可拖动、可折叠悬浮控制窗
- 无 ROOT，通过 Android 无障碍手势执行

## Android 限制

Android 不允许普通应用绕过 PIN、密码、指纹等安全锁屏。定时任务在设备已解锁且无障碍权限有效时可正常执行；部分厂商系统还需要允许后台运行、自启动和忽略电池优化。

## 构建

项目使用 Android Gradle Plugin 8.7.3、Gradle 8.9、JDK 17、compileSdk 35。GitHub Actions 会自动构建 debug APK，并以构建产物形式上传。
