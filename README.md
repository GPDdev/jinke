# 今刻

一个打开就能写的 Android 心情与想法记录本。原生 Java + Android 系统控件 + SQLite，没有第三方运行时依赖、账号、广告、后台服务或应用联网权限。

已有安装包：[下载 jinke.apk](jinke.apk)。

## 安装到 vivo X200 Pro mini

1. 把 `build/jinke.apk` 传到手机，例如通过 USB、微信文件传输助手或其他文件传输工具。
2. 在手机文件管理器里打开 APK。按系统提示，临时允许该文件管理器安装未知来源应用。
3. 安装后关闭这项安装权限，点击桌面的「今刻」。支持 Android 8.0 及以上；APK 不含原生库，不限定 CPU 架构。

## 使用

- 打开应用直接输入，键盘自动弹出；也可选「平静、开心、低落、焦虑、疲惫」，不选就用「随记」。
- 停止输入约 0.4 秒自动存草稿；离开应用时再保存一次。正常切换、关闭和重新打开会恢复草稿。极短时间内强制结束进程、设备突然断电等情况仍可能丢失尚未落盘的最后几个字。
- 点击「保存此刻」生成带日期时间的记录，并清空输入框供下一条使用。
- 「语音输入」调用手机的系统识别窗口，识别结果插入当前光标位置或替换选中文字。没有可调用的识别窗口时，使用 vivo/Jovi 或其他输入法键盘上的麦克风；不需要安装 Google 服务。
- 「记录」查看全文，支持复制、编辑和二次确认删除；编辑不改变原记录创建时间。
- 「导出」一次导出全部正式记录，支持 TXT、Markdown、JSON，UTF-8 编码。存在非空草稿时会先确认保存，避免漏掉当前内容。系统文件窗口可选择下载目录或其他可用位置；导出后用文件管理器分享即可。
- 自动跟随系统深浅色模式，适配键盘避让和系统导航区域。

## 数据与隐私

文字保存在应用私有 SQLite 数据库中，没有本应用的云端同步，禁用 Android 备份与系统数据迁移。语音由外部系统服务或输入法处理，可能需要网络，隐私规则取决于服务提供方；应用仅请求优先离线，不能保证识别服务支持或遵守离线请求，也不保存录音。

导出文件为明文，请妥善保管。卸载应用、清除应用数据或换机会失去本机记录，请先导出。JSON 包含原始文字、心情、记录 ID、创建及修改时间，时间以 Unix 毫秒存储；当前版本只提供导出，不提供 JSON 导入恢复。

## 构建

Windows 需要 JDK 17 或更高版本，`java`、`javac`、`jar`、`keytool` 在 PATH 中。首次构建从 Google 官方仓库下载 Android API 35 和 Build Tools 35.0.0，约 124 MB，并核对官方 SHA-1；工具仅保存在项目 `.tools/` 内。请遵守下载工具包内的许可说明。

```powershell
cd D:\Desktop\files\jinke-android
powershell -NoProfile -ExecutionPolicy Bypass -File .\build.ps1
```

脚本先运行文本导出自检，再编译、生成 DEX、对齐、签名并验证 APK，输出 `build/jinke.apk`。项目同时提供 Gradle 配置，可在 Android Studio 中导入；命令行脚本不需要安装 Android Studio 或 Gradle。

首次构建生成独立本地签名，保存在 `.signing/jinke.keystore` 和 `.signing/password.txt`。请私下备份整个 `.signing` 目录，不要上传或分享。以后覆盖安装必须沿用此签名并适当递增版本号；丢失签名后不能无损覆盖安装。Android Studio 默认 debug 签名与此签名不同，不要为了切换签名而卸载已有记录的应用。

## 检查

已运行：Java / XML / DEX 编译、中文和 emoji / 多行导出 / 时区 / 修改时间 / 空列表的可运行自检、APK 签名及 ZIP 对齐、最终 Manifest 零申请权限检查。

尚未执行：vivo 真机操作、系统语音识别、系统文件选择器、SQLite 设备回归检查。不能仅凭编译通过宣称这些已通过真机验证。

生成独立设备检查 APK：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\build.ps1 -BuildDeviceCheck
```

手机通过 USB 连接并开启 USB 调试、授权 ADB 后运行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\build.ps1 -DeviceCheck
```

该命令会使用同一签名覆盖安装本应用并安装测试 APK，但不卸载本应用、不清除现有数据。测试使用单独的临时数据库，验证草稿恢复、事务保存、原 ID 编辑、删除、JSON 转义以及保存失败时保留草稿，完成后删除测试数据库。成功标记为 `JINKE_CHECK_OK`。

真机验收建议：打开立即输入 → 退出重开恢复草稿 → 保存两条 → 编辑一条 → 语音追加 / 取消 → 依次导出三种格式 → 用文件管理器核对中文、换行、emoji → 深色模式和横竖屏下确认键盘不遮挡保存按钮。测试导出时也应在系统文件选择器中旋转屏幕，确认仍能完成导出。

系统 API 参考：[RecognizerIntent 与离线请求](https://developer.android.com/reference/android/speech/RecognizerIntent#EXTRA_PREFER_OFFLINE)、[系统文档导出](https://developer.android.com/training/data-storage/shared/documents-files)、[Android Gradle 版本兼容表](https://developer.android.com/build/releases/about-agp)。
