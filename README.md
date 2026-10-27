# 星渡路由 · Android

[下载 APK](https://github.com/LONG10222/starpass-router-android/releases/latest) · [English](README.en.md) · [问题反馈](https://github.com/LONG10222/starpass-router-android/issues)

一个保留原 LuCI 网页的局域网登录入口。Android 8.0 及以上，无广告、无云端账号、无 VPN 服务。

## 使用

1. 手机连接软路由所在的局域网。
2. 按顺序填写 **网站地址 → 用户名 → 密码**，点击 **登录并记住**。
3. 登录后使用原管理网页。下次打开直接进入；右上角“登录设置”可以修改地址和账号，不清除网页会话。

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

离线脚本当前使用 SDK `platforms/android-36.1`、`build-tools/36.1.0` 和 JDK 17 或更高版本。输出在 `build/release/StarPass-Router-1.0.0.apk`。本机首次构建会在 `.signing/` 创建开发者签名，之后继续复用。该目录含签名私钥，不能上传或放进源码归档；保留它才能发布可覆盖安装的后续版本。

标准 Gradle 配置使用 AGP 8.13.2 / compileSdk 36 / targetSdk 36。私有测试 APK 的 `TestRunner` 只由离线脚本 `--debug` 加入，不进入交付 APK。

## 测试

- 33 项地址/同源策略断言。
- 7 项登录脚本测试：探测不填密码、只提交原 POST 表单、保留隐藏字段、错误来源/iframe 不填凭据。
- Android 11 ARM64 模拟器：字段顺序、对齐、横竖屏、错误密码停止重试、加密保存、设置页保留 Cookie。
- 强制停止重开继续复用 Cookie，服务器会话失效后只自动登录一次，测试服务器的退出请求始终为零。
- 实体手机安装、不同 Android/WebView 版本及设备特定网页功能仍需对应设备确认。

实现参考：[WebView CookieManager](https://developer.android.com/reference/android/webkit/CookieManager)、[Android Keystore](https://developer.android.com/privacy-and-security/keystore)、[WebView 原生接口边界](https://developer.android.com/privacy-and-security/risks/insecure-webview-native-bridges)。没有使用 `addJavascriptInterface`，不会绕过 HTTPS 证书验证。
