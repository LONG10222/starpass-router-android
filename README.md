# 星渡路由 · Android

[下载 APK](https://github.com/LONG10222/starpass-router-android/releases/latest) · [English](README.en.md) · [问题反馈](https://github.com/LONG10222/starpass-router-android/issues)

一个保留原 LuCI 网页的局域网登录入口。Android 8.0 及以上，无广告、无云端账号、无 VPN 服务。

## 使用

1. 手机连接软路由所在的局域网。
2. 按顺序填写 **网站地址 → 用户名 → 密码**，点击 **登录并记住**。
3. 登录后默认全屏显示原管理网页。右下角 **菜单** 可以退出/进入全屏、刷新、打开登录设置或检查更新；下次打开记住全屏选择。

全屏隐藏 App 工具栏、状态栏和导航栏，并允许内容延伸至挖孔区域；从屏幕边缘滑动可临时显示系统栏。登录设置保持系统安全边距和键盘避让。横竖屏都按实际窗口尺寸排版，不写死某一机型的高度。Redmi K70 Pro 的澎湃 OS 设置可能影响应用显示区域，仍需对应版本实机确认。

## 应用内更新

登录页 **检查更新** 或网页 **菜单 → 检查更新** 读取本仓库 GitHub 最新正式 Release。确认后在 App 内显示下载进度，支持取消；下载完成检查 SHA-256、APK 包名、递增的版本号和与当前 App 相同的签名，再调用系统安装界面。首次安装更新需允许“安装未知应用”，从设置返回后继续安装。最终安装由系统和用户确认。

更新只连接 GitHub，手机需要能够访问 GitHub；不会向更新服务器发送路由器地址、用户名、密码或 Cookie。不自动检查、不强制更新。下载失败、文件损坏或签名不同会显示错误，可重新检查后重试。

发布者必须使用 `v主版本.次版本.修订号` 标签（如 `v1.1.0`），附加 `StarPass-Router-1.1.0.apk`，并确保 GitHub Release API 返回该文件的 `sha256:` digest。`versionName` 必须与标签相符、`versionCode` 必须递增，所有可覆盖安装的版本必须使用同一签名。预发布、草稿、测试 APK 和旧版本不会触发更新。

**1.0.0 的签名不能从 APK 反推出私钥。** 如果丢失原 `.signing/`，重新生成的签名无法覆盖原安装；需要找回原私钥，或先在手机上自行备份必要信息后迁移安装。仓库不保存私钥。

网站可填 `http://192.168.1.1` 或完整 LuCI 页面，例如 `http://192.168.1.1/cgi-bin/luci/admin/services/portalproxy`。示例地址需要换成自己的路由器地址。目前只接收私有 IPv4 地址，支持自定义端口和 HTTP/HTTPS。

会话仍有效时直接复用；服务器会话过期并回到登录页时，使用保存的信息重新登录。密码错误只尝试一次，返回登录设置，避免重复提交。App 不请求退出、不修改路由器会话超时、不定时保活；路由器重启、改密码或清除 App 数据后不能保证原会话持续有效。

## 网页功能

保留原网页、手机 User-Agent 和响应式排版，不替换菜单或注入管理页面样式。旧版 WebView 缺失标准 `replaceChildren` 接口时，仅补齐 DOM 接口以保持动态面板正常渲染。支持 JavaScript 弹窗、系统文件选择和同源文件下载。站外链接交给系统浏览器；下载不会把登录 Cookie 带到站外域名。不提供 USB 或外网穿透功能。

网站、用户名、密码使用 Android Keystore AES-GCM 加密后保存在 App 私有目录，系统备份关闭。Cookie 通过原 WebView 保存，未清理其他浏览器数据，也没有在 APK 中预置任何路由器的账号或密码。HTTP 的传输方式与原 LuCI 网站相同，不等于 HTTPS。

## 构建

项目没有第三方运行依赖，也不打包代理核心。可使用 Android Studio/Gradle，或用已安装的 SDK/JDK 离线构建：

```sh
export ANDROID_HOME=/path/to/android-sdk
export JAVA_HOME=/path/to/jdk
python3 scripts/build.py
```

离线脚本支持 Windows、macOS 和 Linux，使用已安装的 SDK `platforms/android-36*`、build-tools 和 JDK 17 或更高版本。版本号从 `app/build.gradle` 读取，当前输出在 `build/release/StarPass-Router-1.1.0.apk`。本机首次构建会在 `.signing/` 创建开发者签名，之后继续复用。也可通过 `ROUTER_SIGNING_DIR` 指定已有签名目录。该目录含签名私钥，不能上传或放进源码归档；保留它才能发布可覆盖安装的后续版本。正式发布建议执行 `python3 scripts/build.py --require-existing-key`，缺少签名时直接失败。

标准 Gradle 配置使用 AGP 8.13.2 / compileSdk 36 / targetSdk 36。私有测试 APK 的 `TestRunner` 只由离线脚本 `--debug` 加入，不进入交付 APK。

手动签名发布工作流在 GitHub Actions 的 `signed-release`。先设置仓库 Actions secrets `STARPASS_KEYSTORE_BASE64`（原 `starpass-router.jks` 的 base64）和 `STARPASS_KEYSTORE_PASSWORD`（原 `key.json` 中的密码），再对经过测试的分支手动运行，填写匹配版本的标签。工作流不生成新签名，先上传为草稿，确认 GitHub 已生成校验值后发布正式版。签名 alias 为 `starpass-router`。执行该工作流会公开发布 APK，应在实机验证完成后运行。

## 测试

- 33 项地址/同源策略断言。
- 7 项登录脚本测试：探测不填密码、只提交原 POST 表单、保留隐藏字段、错误来源/iframe 不填凭据。
- 35 项更新策略断言：数字版本比较、严格发布来源、重定向域名和 SHA-256 格式。
- CI 配置 Android 11/15 x86_64 模拟器：1440×3200 显示区域、全屏尺寸、横竖屏、菜单切换、字段顺序、错误密码停止重试、加密保存及设置页保留 Cookie。
- Android 更新测试：正式版筛选、文件完整性、取消、实际 APK 签名一致/不一致、标签与 APK 版本不匹配、拒绝降级和 Provider 只读路径约束。
- 强制停止重开继续复用 Cookie，服务器会话失效后只自动登录一次，测试服务器的退出请求始终为零。
- 实体手机安装、不同 Android/WebView 版本及设备特定网页功能仍需对应设备确认。

本地模拟器验证：先执行 `python3 scripts/build.py --debug --test-update-fixtures`，启动含 WebView 的 Android 模拟器，再执行 `python3 scripts/emulator_test.py`。脚本清除模拟器内此 App 的测试数据；请勿对有真实登录信息的手机运行。测试截图和结果保存在 `build/emulator/`。测试用更新 APK 只放在 debug 构建，正式 APK 审计拒绝测试 Runner 和嵌套 APK。

实现参考：[WebView CookieManager](https://developer.android.com/reference/android/webkit/CookieManager)、[Android Keystore](https://developer.android.com/privacy-and-security/keystore)、[WebView 原生接口边界](https://developer.android.com/privacy-and-security/risks/insecure-webview-native-bridges)。没有使用 `addJavascriptInterface`，不会绕过 HTTPS 证书验证。
