# 墨页 · Markview

**[English](README.md) | 简体中文**

Windows 和 Android 客户端共用本地渲染页面。Markdown 解析、安全清理和 Mermaid 引擎均随应用内置，本地文档无需网络。

- **查看**：单窗口阅读渲染后的 Markdown。
- **编辑**：左侧编辑 Markdown 源码，右侧只读实时预览；拖动中间分隔线调整宽度。
- **打开**：拖入 `.md`、`.markdown`、`.mdown`、`.mkd` 或 `.txt` 文件，或点击顶部「打开文件」。按 UTF-8 读取。
- **导出**：点击「导出文件」下载当前 Markdown。原文件不会被直接覆盖。
- **主题**：点击顶部主题按钮，在白色与黑色主题之间切换。
- **草稿**：自动保存到当前浏览器的本地存储；恢复需使用相同浏览器和文件路径。浏览器清理数据、隐私模式或存储权限受限时可能无法保留。

快捷键：`Ctrl / Cmd + O` 打开，`Ctrl / Cmd + S` 导出；编辑器内 `Tab` 缩进，`Shift + Tab` 取消缩进。选项卡可用方向键切换，分隔器可用左右方向键调整。

## SFTP 远程文件

Windows 和 Android 应用顶部提供「SFTP」按钮。输入服务器地址、SSH 端口（默认 22）、用户名、密码和初始目录后，可以浏览远程目录并打开 `.md`、`.markdown`、`.mdown`、`.mkd` 或 `.txt` 文件。

- 新服务器连接成功后自动保存名称、主机、端口、用户名、密码和初始目录；相同主机、端口和用户名会更新已有配置。服务器列表支持编辑、删除和一键连接，并按最近使用排序。
- Windows 使用系统 `safeStorage` 加密密码，配置保存在当前用户的应用数据目录；Android 使用 Android Keystore 中的 AES-GCM 密钥加密，配置仅保存在应用私有存储。密码不会返回网页层，也不会写入网页草稿或 `localStorage`。
- Android 的云备份和设备迁移会排除 SFTP 配置；卸载应用会清除配置和加密密钥。系统加密不可用时只保存非敏感连接信息，并在连接后提示密码未保存。
- 打开远程文档后，「导出文件」变为「保存远程」，点击按钮或按 `Ctrl / Cmd + S` 会将 UTF-8 内容直接写回原远程路径。
- 本地文件仍使用原生打开与导出对话框；新建或打开其他本地文件后，会退出当前远程文档的写回状态。
- 关闭应用或点击「断开」会关闭会话，但不会删除已保存的服务器配置；请仅连接可信服务器。
- 单个远程文件上限为 10 MB。纯网页版没有原生 SFTP 后端，因此不显示此入口。

支持标题、表格、只读任务清单、引用、代码块、链接、图片等常见 Markdown 语法。`mermaid` 代码块可离线渲染为图表，支持明暗主题、编辑时异步刷新和逐图语法错误提示。引用的远程图片仍需要网络；同目录图片可用相对路径引用。HTML 与 Mermaid 生成的 SVG 均会经过安全过滤。

客户端共用根目录 `index.html`，并需同时携带 `vendor/` 和 `web/` 资源，暂不支持只分发单个 HTML 文件。`.work/` 保存开发时的源片段、依赖和验证记录。Marked 与 DOMPurify 的许可保留在 HTML 中，Mermaid 许可位于 `vendor/mermaid-LICENSE.txt`。

## Windows 应用

Windows x64 安装程序可在 [Releases](https://github.com/shoswaller/mdView/releases) 页面下载。安装后可在开始菜单或桌面启动「墨页」。支持双击 `.md`、`.markdown`、`.mdown`、`.mkd` 文件打开，重复打开文档会切换到同一窗口。

- 保留网页版的查看、分栏编辑、实时预览、拖放、主题和草稿功能。
- 「打开文件」与「导出文件」使用 Windows 原生文件对话框，导出时确认目标位置后写入 UTF-8 文件。
- 外部网页链接由系统浏览器打开，Markdown 内容仍在本地处理。

开发运行使用 `npm start`，重新生成安装包使用 `npm run dist`。桌面入口位于 `windows/main.js`，安全桥接位于 `windows/preload.js`。

## Android 应用

Android APK 可在 [Releases](https://github.com/shoswaller/mdView/releases) 页面下载，支持 Android 6.0（API 23）及以上版本。应用名为「墨页」，无需登录，也不申请存储权限。

- Markdown 与 Mermaid 引擎、样式和图标均内置，可离线阅读、编辑和查看图表。
- 「打开文件」调用 Android 系统文件选择器，按 UTF-8 读取。
- 「导出文件」调用系统「另存为」，只有写入成功后才将文档标记为已导出。
- 草稿保存在应用本地；外部网页链接交给系统浏览器打开。

Android 原生工程位于 `android/`，共享网页资源位于 `android/app/src/main/assets/`（`index.html`、`vendor/` 和 `web/`），需与根目录对应文件保持同步。构建需要 JDK 17、Android SDK 35 和 Gradle 8.9；在 `android/local.properties` 中配置本机 `sdk.dir` 后，可运行 `gradlew assembleRelease`。默认使用本机 Android 调试证书签名，适合直接侧载测试。正式分发或覆盖旧签名版本时，必须通过 `MARKVIEW_KEYSTORE`、`MARKVIEW_KEYSTORE_PASSWORD`、`MARKVIEW_KEY_ALIAS`、`MARKVIEW_KEY_PASSWORD` 环境变量提供原正式签名配置。
