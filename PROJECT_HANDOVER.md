# OmniProxy 项目全景设计与工程交接文档 (Project Handover & Architecture Document)

> **适用对象**：后续接手的 AI 助手 (LLM Agent)、人类开发者、架构师及维护者。  
> **文档版本**：`v1.0.0` (配合 Release `v1.0.0-preview`)  
> **最后更新时间**：2026-11-22  
> **开源仓库**：[https://github.com/Aa-creat/OmniProxy](https://github.com/Aa-creat/OmniProxy)

---

## 目录 (Table of Contents)
1. [项目背景与核心定位](#1-项目背景与核心定位)
2. [总体系统架构与技术选型](#2-总体系统架构与技术选型)
3. [核心业务功能与关键技术实现](#3-核心业务功能与关键技术实现)
   - 3.1 [单机 VPN 热点共享与穿透原理](#31-单机-vpn-热点共享与穿透原理)
   - 3.2 [热点已连客户端管理 (ARP 邻居探测)](#32-热点已连客户端管理-arp-邻居探测)
   - 3.3 [Go 原生高并发网络引擎 (JNI Core)](#33-go-原生高并发网络引擎-jni-core)
   - 3.4 [Material Design 3 Expressive (MD3E) 设计系统](#34-material-design-3-expressive-md3e-设计系统)
   - 3.5 [系统级前台保活与下拉快捷磁贴 (Quick Tile)](#35-系统级前台保活与下拉快捷磁贴-quick-tile)
4. [项目工程目录与代码导航](#4-项目工程目录与代码导航)
5. [CI/CD、签名安全与自动化发布规范](#5-cicd签名安全与自动化发布规范)
6. [当前工作进展与交付成果](#6-当前工作进展与交付成果)
7. [关键开发踩坑记录与避坑指南 (AI 必读)](#7-关键开发踩坑记录与避坑指南-ai-必读)
8. [后续演进路线与待办事项 (Next Steps & Roadmap)](#8-后续演进路线与待办事项-next-steps--roadmap)

---

## 1. 项目背景与核心定位

### 1.1 诞生背景
在移动端（尤其是 Android 生态），原版 *Every Proxy* 是一款经典的网络代理服务器应用，能够将手机作为局域网代理共享网络。但原版存在明显的痛点：
- 包含商业广告与冗余的第三方追踪 SDK；
- UI 停留在早期 Material 风格，缺乏现代交互活力与弹性微动效；
- 缺少直观的热点客户端设备管理与网络拓扑洞察；
- 对于最核心的痛点场景——**“单部手机同时开启 VPN 客户端与 Wi-Fi 热点，令连接热点的设备（电脑、Switch、车载中控、其他手机）穿透 VPN 访问外网”**——缺乏系统化的场景引导与无缝配网体验。

### 1.2 项目定位
**OmniProxy** 是对此类应用的高标准自研重构。以 **零广告、现代 MD3E 设计、高性能 Go 语言原生核心、深度热点/VPN 网关协同** 为核心特色，致力于成为 Android 平台上最优雅、功能最实用的免 Root 局域网代理与热点共享工具。

- **应用包名**：`io.github.aacreat.omniproxy`
- **目标 SDK**：`compileSdk = 35`, `targetSdk = 35`, `minSdk = 26` (覆盖 Android 8.0 至 Android 15，覆盖率 > 99%)
- **许可协议**：专有自研 / 视需求开源 (默认干净独立，无侵权资源)

---

## 2. 总体系统架构与技术选型

项目采用分层清晰的现代 Android 架构 (Clean Architecture + MVI/MVVM)，结合 Go 语言 CGO/JNI 跨平台编译能力：

```mermaid
graph TD
    subgraph UI Layer [MD3E Expressive UI - Jetpack Compose 1.3+]
        Dashboard[仪表盘 DashboardScreen]
        HotspotMgr[热点管理 HotspotManagerScreen]
        Settings[设置中心 SettingsScreen]
        QrDialog[二维码配网 QrCodeDialog]
    end

    subgraph Business & Service Layer [Kotlin Coroutines & Flow]
        VM[ViewModel / StateFlow]
        NetMgr[NetworkManager 多网卡/ARP探测]
        PrefMgr[PreferencesManager Proto DataStore]
        FGService[OmniProxyService 前台服务]
        TileService[ProxyTileService 快捷磁贴]
        BootReceiver[BootReceiver 开机自启]
    end

    subgraph Native Bridge Layer [JNI JNIEnv Bridge]
        NativeProxyKt[NativeProxy.kt 动态加载与回退]
        CgoBridge[core-proxy-go/cmd/jni/main.go CGO接口]
    end

    subgraph Core Engine [Go 1.22+ Native Proxy - libomniproxy.so]
        HttpTunnel[HTTP/HTTPS CONNECT 隧道转发]
        Socks5Server[SOCKS5 TCP/UDP 状态机]
        PacServer[PAC 动态脚本托管服务]
        StatsCollector[原子流量与连接计数器]
    end

    UI Layer --> Business & Service Layer
    Business & Service Layer --> Native Bridge Layer
    Native Bridge Layer --> Core Engine
```

### 2.1 技术选型决策矩阵
| 组件模块 | 选定方案 | 为什么选择此方案（决策理由） |
| :--- | :--- | :--- |
| **UI 框架** | **Jetpack Compose + Material 3 1.3+** | 官方对 **MD3E (Expressive)** 动效与动态组件支持最完整，声明式状态流极佳。 |
| **代理核心** | **Go 1.22+ (C-Shared JNI)** | Go 标准库 `net` 原生处理高并发、高吞吐 TCP/UDP 性能极佳，支持标准 HTTP CONNECT 隧道与 SOCKS5 握手，无 JVM GC 对网络大吞吐的卡顿影响。 |
| **本地配置持久化** | **Jetpack DataStore (Preferences)** | 替代废弃的 `SharedPreferences`，天然支持 Kotlin Flow 响应式观察与事务安全。 |
| **异步与生命周期** | **Coroutines + StateFlow** | 统一单向数据流，UI 自动感知代理运行状态与网速更新。 |
| **扫码组件** | **ZXing Core** | 轻量纯 Java 二维码生成引擎，无重量级相机扫描 SDK 依赖，减小包体积。 |

---

## 3. 核心业务功能与关键技术实现

### 3.1 单机 VPN 热点共享与穿透原理
#### 痛点说明
在标准 Android 系统上，用户开启科学上网/专网 VPN（通过 `VpnService` 创建的 `tun0` 虚拟接口）后，如果再开启移动热点（`ap0`），从设备（如笔记本或游戏机）连接该热点后，流量默认由 Linux 内核 iptables 的 FORWARD 规则直接转发至物理移动网卡（`rmnet_data`）或 Wi-Fi 物理网卡，**完全绕过了手机上的 `tun0` VPN 接口**。

#### OmniProxy 解决方案
1. OmniProxy 作为代理服务器进程运行在手机的“本机用户空间”内；
2. 手机本机的出站网络请求（由代理服务器发起的连接）受到系统路由表的完整管理，**必然穿透系统当前激活的 VPN 隧道**；
3. OmniProxy 将代理服务器（HTTP `8080`、SOCKS5 `1080` 或 PAC `8088`）绑定在全局（`0.0.0.0`）或热点网关 IP（如 `192.168.43.1`）；
4. 连入热点的从设备只需将代理设置为该 IP:Port，从设备的所有外网访问流量均由 OmniProxy 经由手机的 VPN 隧道透明转发出去，**完全不需要手机 Root 权限**。

### 3.2 热点已连客户端管理 (ARP 邻居探测)
- **实现位置**：`core/network/NetworkManager.kt:getHotspotClients()`
- **实现机理**：读取 Linux 系统内核的 ARP 映射缓存 `/proc/net/arp`。
- **提取字段**：
  - `IP address`（如 `192.168.43.102`）
  - `HW address`（MAC 地址，如 `AA:BB:CC:DD:EE:FF`）
  - `Flags`（仅过滤有效在线的标志 `0x2`）
- 界面实时展现连接该热点的所有从设备数量与设备卡片，并提供“一键刷新”能力。

### 3.3 Go 原生高并发网络引擎 (JNI Core)
- **代码目录**：`core-proxy-go/`
- **核心功能**：
  1. **HTTP/HTTPS 代理 (`pkg/proxy/http.go`)**：
     - 普通 HTTP 请求直接透明转发；
     - HTTPS 连接识别 `CONNECT host:port HTTP/1.1`，建立双工 TCP Pipe，通过 `io.Copy` 与自定义 `CountingReader` 统计上下行字节。
     - 支持 HTTP Basic Auth 凭据认证（`Proxy-Authorization` 头校验）。
  2. **SOCKS5 代理 (`pkg/proxy/socks5.go`)**：
     - 实现完整 RFC 1928 握手状态机；
     - 支持无密码（`0x00`）与用户名/密码（`0x02`，RFC 1929）认证；
     - 支持 IPv4、Domain（域名解析防污染）、IPv6 目标地址。
  3. **PAC 脚本服务器 (`pkg/proxy/pac.go`)**：
     - 动态生成针对局域网的 `proxy.pac` 文件，自动绕过局域网/保留地址（`10.0.0.0/8`, `192.168.0.0/16`, `127.0.0.0/8` 等），将外网请求精准路由给本机的 HTTP/SOCKS5 代理。
  4. **JNI 跨语言桥接 (`cmd/jni/main.go`)**：
     - 采用静态 C 辅助函数包装 `GetStringUTFChars` 和 `NewStringUTF`，阻绝 CGO 的指针类型推导异常；
     - 导出 `startHttpProxy`、`stopHttpProxy`、`startSocks5Proxy`、`stopSocks5Proxy`、`startPacServer`、`stopPacServer`、`getStatsJson`。

### 3.4 Material Design 3 Expressive (MD3E) 设计系统
- **定义位置**：`ui/theme/`
- **设计特色**：
  - **Expressive Shapes (`Shape.kt`)**：
    - 主容器卡片采用超大圆角 `RoundedCornerShape(32.dp)` (`HeroCardShape`)；
    - 状态指示与药丸徽章使用 `RoundedCornerShape(50)` (`PillShape`)；
    - 次级内容卡片嵌套 `RoundedCornerShape(16.dp)` (`CardInnerShape`)。
  - **Spring Motion (`Motion.kt`)**：
    - 开关、高亮徽章与卡片形变采用物理弹簧动效 `spring(dampingRatio = 0.7f, stiffness = 400f)`，具备富有张力的自然回弹感。
  - **Dynamic Monet Palette (`Color.kt`, `Theme.kt`)**：
    - 在 Android 12+ 设备上自动提取壁纸色系（Monet）；
    - 在深浅色主题下保持高对比度，代理开启时自动将背景渐变弹性过渡至 Primary Container。
  - **一键扫码配置二维码 (`QrCodeDialog.kt`)**：
    - 基于 ZXing 将 PAC URL 或代理配置参数动态渲染为高清二维码，方便手机、平板或车载设备一扫配置。

### 3.5 系统级前台保活与下拉快捷磁贴 (Quick Tile)
- **前台服务 (`OmniProxyService.kt`)**：
  - 适配 Android 14/15 规范，声明 `foregroundServiceType="specialUse"`；
  - 启动时自动申请 `PowerManager.PARTIAL_WAKE_LOCK` 与 `WifiManager.WIFI_MODE_FULL_HIGH_PERF`，防止息屏后 CPU 睡眠或 Wi-Fi 断联；
  - 常驻通知栏秒级轮询活跃连接数与上传/下载流量并格式化展示；提供“一键停止”PendingIntent。
- **下拉磁贴 (`ProxyTileService.kt`)**：
  - 注册 `android.service.quicksettings.TileService`；
  - 在系统下拉控制中心提供 OmniProxy 快捷磁贴，响应式监听服务运行状态并支持一键启停。
- **开机自启 (`BootReceiver.kt`)**：
  - 监听 `BOOT_COMPLETED` 广播，若用户在设置中开启“设备开机时自动启动”，将在后台静默拉起代理服务。

---

## 4. 项目工程目录与代码导航

```
Every Proxy/
├── .github/
│   └── workflows/
│       └── release.yml              # CI/CD 流水线：Go/NDK 编译、APK 签名与 Pre-Release
├── app/
│   ├── build.gradle.kts             # 模块构建配置 (compileSdk 35, Compose, NDK ABI 过滤)
│   ├── proguard-rules.pro           # 混淆规则 (保护 JNI 原生绑定与 Model)
│   └── src/main/
│       ├── AndroidManifest.xml      # 权限、Activity、Service、Tile 与 Receiver 声明
│       ├── jniLibs/                 # Go 编译生成的 .so 库放置目录 (arm64-v8a, armeabi-v7a, x86_64)
│       ├── java/io/github/aacreat/omniproxy/
│       │   ├── MainActivity.kt      # 主界面：TopAppBar、状态徽章、NavigationBar 切换
│       │   ├── OmniProxyApplication.kt # 应用程序入口与通知渠道 (NotificationChannel) 初始化
│       │   ├── core/
│       │   │   ├── model/Models.kt  # 核心模型：ProxyConfig, ProxyStats, HotspotClient 等
│       │   │   ├── jni/NativeProxy.kt # JNI 接口包装器与安全回退逻辑
│       │   │   ├── network/NetworkManager.kt # 网络探测器、网关推荐与 /proc/net/arp 读取
│       │   │   └── data/PreferencesManager.kt # DataStore 配置持久化
│       │   ├── service/
│       │   │   ├── OmniProxyService.kt # 核心前台保活服务、流量轮询与常驻通知
│       │   │   ├── ProxyTileService.kt # 系统下拉快捷磁贴
│       │   │   └── BootReceiver.kt     # 开机广播接收器
│       │   └── ui/
│       │       ├── theme/           # MD3E 主题：Color, Shape, Motion, Theme
│       │       ├── components/      # Expressive 卡片、网络横幅、二维码弹窗
│       │       └── screens/         # 控制台 Dashboard、热点管理 Hotspot、设置 Settings
│       └── res/                     # 字符串、自适应图标与 Vector Drawables
├── core-proxy-go/                   # Go 原生代理核心工程
│   ├── go.mod                       # 模块定义: io.github.aacreat.omniproxy/core
│   ├── build-android.sh             # NDK 交叉编译脚本 (生成全平台 libomniproxy.so)
│   ├── cmd/jni/main.go              # JNI 导出函数与 C-Go 类型安全转换
│   └── pkg/proxy/
│       ├── http.go                  # HTTP/HTTPS CONNECT 隧道代理实现
│       ├── socks5.go                # SOCKS5 状态机实现
│       ├── pac.go                   # PAC 动态脚本服务器实现
│       └── stats.go                 # 流量统计与原子计数
├── gradle/
│   ├── libs.versions.toml           # 统一依赖版本编排 (AGP 8.5.2, Kotlin 2.0.21, Compose)
│   └── wrapper/
├── build.gradle.kts                 # 根工程构建配置
├── settings.gradle.kts              # 仓库与模块声明
├── PROJECT_HANDOVER.md              # [本文档] 项目全景与交接指南
└── README.md                        # 面向用户的公开说明
```

---

## 5. CI/CD、签名安全与自动化发布规范

工作流文件位于 `.github/workflows/release.yml`，每次向 `main` 分支推送 Tag（如 `v1.0.0-preview`）或手动在 GitHub Actions 点击触发：

1. **环境矩阵准备**：
   - 使用 `ubuntu-latest` 运行器；
   - 安装 Go 1.22 (`actions/setup-go@v5`)；
   - 安装 JDK 17 (`actions/setup-java@v4`, Temurin)；
   - 通过系统自带 `$ANDROID_HOME` 静默安装 Android NDK `26.1.10909125` 与 Build-Tools `35.0.0`。
2. **Go 原生动态库交叉编译**：
   - 自动执行 `core-proxy-go/build-android.sh`；
   - 生成 `app/src/main/jniLibs/{arm64-v8a, armeabi-v7a, x86_64}/libomniproxy.so`。
3. **Gradle APK 编译**：
   - 运行 `./gradlew assembleRelease` 生成 Release 产物。
4. **企业级双模安全签名 (Dual-Mode Signing)**：
   - 执行 `zipalign -v -p 4` 进行 4 字节内存对齐；
   - **生产证书模式**：若配置了 GitHub Secrets（`RELEASE_KEYSTORE_BASE64`、`KEY_ALIAS`、`KEYSTORE_PASSWORD`、`KEY_PASSWORD`），则从 Secrets 解密真实证书签名；
   - **自动回退模式**：若未配置 Secrets，CI 自动在内存临时生成合法的标准 Release Keystore，并通过 `apksigner` 开启 **v1 + v2 + v3 完整签名方案**；
   - 执行 `apksigner verify --verbose` 严格验证签名有效性。
5. **完整性校验与自动发布**：
   - 计算生成的 APK 的 `sha256sum` 并输出 `checksums-sha256.txt`；
   - 调用 `softprops/action-gh-release@v2` 自动发布 GitHub Pre-Release，并将 `OmniProxy-release.apk` 与校验和文件附在 Release 页面供用户直接下载。

---

## 6. 当前工作进展与交付成果

- [x] **Git 仓库与 GitHub 公开同步**：[https://github.com/Aa-creat/OmniProxy](https://github.com/Aa-creat/OmniProxy) 已创建并同步至最新提交；
- [x] **Go 网络底层研发完成**：HTTP CONNECT、SOCKS5、PAC 均已编写并通过交叉编译；
- [x] **Android 应用业务层开发完成**：Kotlin + Compose + MD3E 设计语言全部落地；
- [x] **VPN 热点共享与 ARP 设备探测实现**：支持单机开启 VPN 热点并通过 ARP 监控连入设备；
- [x] **CI/CD 流水线跑通**：已在 GitHub Actions 成功跑通构建流程；
- [x] **首个 Pre-Release 正式发布**：
  - **Release Tag**：`v1.0.0-preview`
  - **发布地址**：[https://github.com/Aa-creat/OmniProxy/releases/tag/v1.0.0-preview](https://github.com/Aa-creat/OmniProxy/releases/tag/v1.0.0-preview)
  - **产物文件**：`OmniProxy-release.apk` (已包含全套 v2/v3 签名与 Go .so，真机可直接安装) 与 `checksums-sha256.txt`。

---

## 7. 关键开发踩坑记录与避坑指南 (AI 必读)

> [!WARNING]
> 后续 AI 助手在修改或扩展本项目时，**必须严格遵守以下关键注意事项**，避免重复踩坑：

1. **PowerShell 文件写入编码陷阱 (Windows)**：
   - **坑点**：Windows PowerShell 5.1 的 `Set-Content` / `Out-File` 默认采用 UTF-16 LE（带 BOM）。若用其修改 `.kt` 文件，会在每个字符间填充 `\x00`，Linux 上的 Kotlin 编译器会报一大堆类似 `Syntax error: Expecting '"'` 或解析乱码。
   - **规则**：修改文件必须使用 `write_file` / `edit_file` 原生工具，或在 PowerShell 中显式指定 `-Encoding utf8`。
2. **CGO 与 JNI 类型比较规则**：
   - **坑点**：在 Go 代码中，不能直接对 JNI 类型（如 `jstring`）使用 `jstr == nil`，编译器会报 `mismatched types _Ctype_jstring and untyped nil`。
   - **规则**：所有涉及 JNI 指针与字符串转换的操作，统一封装在 C 语言代码块（如 `core-proxy-go/cmd/jni/main.go` 中的 `get_string_utf_chars` 和 `new_string_utf`），Go 代码只负责判断 C 返回的 `cStr == nil`。
3. **GitHub Actions 表达式语法**：
   - **坑点**：GitHub Actions 的表达式语法 `${{ a || b }}` 不能像 JS 一样短路返回字符串，非法使用会导致 workflow 在 0 秒直接报 invalid failure。
   - **规则**：优先使用标准 Action（如 `softprops/action-gh-release@v2`）直接传入 `${{ github.ref_name }}`。
4. **Android 14/15 前台服务类型**：
   - **坑点**：Android 14+ 强制前台服务声明 `foregroundServiceType`，否则抛出 `SecurityException`。
   - **规则**：本项目已声明 `foregroundServiceType="specialUse"`，并在 `AndroidManifest.xml` 中配置了对应的 `<property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" .../>`。后续不要随意移除此属性。

---

## 8. 后续演进路线与待办事项 (Next Steps & Roadmap)

对于后续接手本项目进一步增强的 AI 或开发者，推荐以下功能演进路线：

- [ ] **热点直接限速与单设备管理**：
  - 基于 `/proc/net/arp` 发现设备后，进一步结合 Linux `iptables` / `tc`（若设备具备 Root 权限）对指定 IP/MAC 提供带宽限速或一键断开连接；
- [ ] **Tun2Socks 全局透明拦截（第二阶段）**：
  - 目前热点穿透依赖从设备在 Wi-Fi 中设置代理或 PAC；未来可在本机引入 `VpnService` + `tun2socks` 网卡劫持，实现连入热点的从设备零设置（甚至无需配置代理）直接透明走 VPN 转发；
- [ ] **多级级联代理 (Proxy Chaining)**：
  - 允许在设置中配置上游代理（Upstream SOCKS5/HTTP），实现 OmniProxy -> 上级代理 -> 互联网的两级中继；
- [ ] **生产 Keystore 接入**：
  - 用户可自建私有签名密钥，将 base64 存入 GitHub Secrets `RELEASE_KEYSTORE_BASE64`，CI 将自动切换为专属生产签名。
