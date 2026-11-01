# derminal

原生 Android 终端，Compose 界面，离线 Debian 13 Trixie，基于 PRoot，无需 root。

支持 Android 8.0 及以上 ARM64 设备。安装后首次打开自动展开系统，无需另外安装 Termux。

## 使用

- 点击顶部 `+` 新建标签，长按标签改名，关闭会话需要确认。
- 功能键 Ctrl、Alt、Shift 轻点后生效一次，长按锁定；Fn 切换 F 键及翻页键。
- 长按终端文字选择、复制。设置的会话分区提供粘贴、搜索和导出。
- `apt update && apt upgrade` 更新 Debian，`apt install <软件包>` 安装软件。
- 设置中的文件导入将文件放入 `/shared`；授予存储权限后，新会话可访问 `/sdcard`。

## 构建

GitHub Actions 工作流 `.github/workflows/android.yml` 在 GitHub 托管的 Ubuntu runner 制作离线 Debian 镜像、更新软件包、收集对应源代码，再用 NDK 28 编译原生 PTY，执行测试、Lint、构建并发布。所有构建和系统打包均在 Actions 完成。签名材料使用仓库加密 Secrets，保持升级签名不变。

手动构建需要 JDK 21、Android SDK 36、NDK 28.2.13676358、Gradle 8.13，恢复运行环境后执行 `sh tools/build_native.sh` 及 `./gradlew :app:assembleDebug`。

运行环境由 Actions 中的 `sudo python3 tools/prepare_runtime.py --chroot` 制作。底包与本地保留的原生依赖固定 SHA256，具体软件版本由发布包清单及 runtime.json 记录。

## 运行约束

Android 的可写应用目录执行策略要求此类 Linux 用户空间采用 targetSdk 28，直接安装时部分系统会提示目标版本较旧。应用采用 compileSdk 36 和当前 AndroidX。PRoot 共享 Android 内核，无法运行 Docker、systemd 或内核模块。后台会话可能被系统回收。

应用的工程检查、终端测试与真机界面验收分别记录，不以编译成功替代真实操作验证。详见 [研究与设计](docs/研究与设计.md) 和 [第三方许可](THIRD_PARTY.md)。
