# Changelog

## 1.1.0 · 2026-10-27

- 网页默认沉浸式全屏，适配长屏、挖孔和横竖屏；浮动菜单支持切回普通模式并记住选择。
- 登录页和菜单增加版本检查、下载进度/取消、SHA-256 与 APK 版本/包名/签名检查，以及系统授权安装。
- 支持 Windows 离线构建；增加 Android 11/15 界面和更新校验测试，以及复用原签名的手动发布工作流。
- Immersive browser mode with a reachable menu, display-cutout support and a persisted fullscreen preference.
- In-app release checks, verified APK downloads and the system update installer.

## 1.0.0 · 2026-10-27

- 局域网 LuCI 登录，网站、用户名、密码纵向排列。
- 本机加密记住登录信息，复用会话，过期后自动重登一次。
- 保留原网页、文件选择、弹窗和同源下载；适配横竖屏。

- LAN LuCI login with vertically arranged address, username and password.
- Encrypted local profiles, cookie reuse and one automatic login after expiry.
- Original web UI, dialogs, file selection and same-origin downloads.
