# OmniProxy (MD3E Expressive)

[![Release Build](https://github.com/Aa-creat/OmniProxy/actions/workflows/release.yml/badge.svg)](https://github.com/Aa-creat/OmniProxy/actions)

**OmniProxy** 是一款基于 **Material Design 3 Expressive (MD3E)** 现代化设计语言构建的 Android 局域网/热点代理服务器与网关管理工具。

零广告、零冗余追踪，底层搭载 **Go 语言原生高并发代理引擎 (`libomniproxy.so`)**，上层采用纯现代 **Kotlin + Jetpack Compose**。

---

## ✨ 核心特性

- 🎨 **Material Design 3 Expressive (MD3E) 设计**：
  - 动态形状与圆角过渡（Expressive Morphing & Pill Shapes）
  - 物理弹簧动效（Spring Physics，生动自然的回弹手感）
  - Monet 动态壁纸取色（完美适配深浅色与动态主题）
- 🚀 **Go 原生网络引擎 (High Performance JNI Core)**：
  - **HTTP / HTTPS (CONNECT 隧道)** 代理服务
  - **SOCKS5** 代理服务（支持 TCP 代理与可选身份凭据认证）
  - **PAC (Proxy Auto-Config)** 自动配置脚本托管服务
- 🌐 **VPN 热点共享与穿透（核心痛点突破）**：
  - 单设备只需开启 VPN 与 Wi-Fi 热点，其他连入热点的从设备（电脑、Switch/PS5、车载、TV、其他手机）通过配置该代理即可**无缝享受翻墙与专网穿透**，无需 Root 权限。
- 📱 **热点与已连客户端设备管理**：
  - 自动感知热点网卡与局域网推荐 IP
  - 实时读取 `/proc/net/arp` 列出当前连接到本热点的所有从设备（IP、MAC、在线状态）
  - 一键跳转系统便携式热点设置
- 📷 **一键二维码扫码配网**：
  - 动态生成当前局域网 PAC 脚本或代理参数二维码，从设备一扫即用
- 🔋 **系统级保活与快捷磁贴**：
  - 前台通知服务与 WakeLock / WifiLock 电源锁防休眠
  - 快捷下拉磁贴 (Quick Settings Tile) 一键开关
  - 支持开机自启动

---

## 🏗️ 架构分层

- **UI 层**：Android Jetpack Compose + Compose Material 3 (Expressive Spec)
- **业务架构**：Clean Architecture + MVI/MVVM + Kotlin Coroutines & Flow
- **底层代理引擎**：Go 1.22+ (`c-shared` JNI 跨平台编译至 Android NDK)
- **数据持久化**：Jetpack DataStore (Preferences)
- **CI/CD**：GitHub Actions 自动跨平台编译 Go `.so`、构建 Release APK 并发布 Pre-Release

---

## 🛠️ 构建与发布

本项目通过 **GitHub Actions CI/CD** 进行全自动编译与打包：
- 每次推送新版本 Tag（例如 `v1.0.0`）或在 Actions 控制台触发，自动完成 NDK/Go 交叉编译、APK 对齐与签名，并在 **Releases** 页面生成预发布安装包。

---

## 🔒 声明

本项目为个人所有，未经作者明确授权，保留所有权利 (All Rights Reserved)。
