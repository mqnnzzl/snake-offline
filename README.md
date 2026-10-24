# 贪吃蛇 · Snake Club

一个原生 Android 单机小游戏。奶油白与薄荷绿配色，无广告、无账号、不申请网络或存储权限。无需游戏引擎、WebView 或第三方运行库。

[下载 Android 安装包](dist/snake-offline-v1.0.0.apk?raw=true) · [查看验证记录](dist/验证记录.txt)

<img src="dist/preview.png" alt="贪吃蛇 Android 游戏运行截图" width="320" />

## 安装

安装文件：`dist/snake-offline-v1.0.0.apk`

把 APK 传到 Android 手机，使用文件管理器打开并安装。如果系统提示，请为打开此文件的应用允许“安装未知应用”。支持 Android 6.0 及以上；APK 使用本地专用密钥签名，可直接安装。

## 操作

- 点击“开始游戏”，倒计时后开始。
- 滑动棋盘或点击方向按钮转向；电脑模拟器也支持方向键 / WASD。
- 每颗果子加 10 分并增长一格，撞到墙或身体结束游戏；不能直接反向掉头。
- 悠闲 / 经典 / 极速分别为 220 / 150 / 100 毫秒一步，各自保存最高分。
- 点击“暂停”或按空格暂停，点击“继续”恢复。切到后台、打开玩法说明时自动暂停。
- “重新开始”会在当前游戏尚未结束时询问确认。切换速度需先回到开局界面。
- 填满 20 × 20 棋盘即获胜。

## 源码与构建

- `app/src/main/java/com/mintarcade/snake/SnakeGame.java`：与 Android 无关的游戏规则。
- `app/src/main/java/com/mintarcade/snake/MainActivity.java`：原生界面、绘制、触屏控制、生命周期和纪录保存。
- `tests/com/mintarcade/snake/SnakeGameTest.java`：移动、输入缓冲、碰撞、计分、恢复、通关及随机游戏测试。
- `build.ps1`：测试、资源编译、Java 编译、DEX 转换、打包、对齐、签名与校验。
- `setup-tools.ps1`：下载固定版本的官方工具并校验哈希。

在项目目录执行：

```powershell
# 首次构建：下载并校验构建工具
powershell -NoProfile -ExecutionPolicy Bypass -File .\setup-tools.ps1

# 编译、测试并生成签名 APK
powershell -NoProfile -ExecutionPolicy Bypass -File .\build.ps1
```

工具链使用 Java 17、Android Platform 35 和 Build Tools 35.0.0。首次准备工具需要联网，之后构建和游戏均不需要联网。无需安装 Android Studio 或 Gradle。

本地签名密钥和随机密码保存在 `.signing/`，已从版本管理排除。请私下备份整个目录；以后升级同一应用需保留相同签名。不要分发这个目录或把它提交到仓库。APK 和源码可以单独分发。

## 验证

构建脚本执行 48,931 项规则断言（包含随机游戏中的状态检查），并验证 APK 的 v1 / v2 / v3 签名和 ZIP 对齐。已在本机 Android 15 MuMu 模拟器成功安装启动；真机兼容性未逐型号测试。

`dist/SHA256SUMS.txt` 保存最终 APK 的 SHA-256 校验值。
