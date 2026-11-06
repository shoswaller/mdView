# 墨页 / MARKVIEW 设计系统

## 0. Research Log

- 内置参考候选：Notion、Linear、Vercel；选用 `minimalist-skill.md`（Layer A）与 `notion.md`（Layer B）。取文档优先的留白、暖中性色阶、细边框与四级字重；改为纯中性灰，满足黑白主题要求，不沿用品牌蓝或商标。
- Lazyweb：查询 `markdown document editor split preview`；实际查看 Craft、Butterdocs 两个桌面截图。提取“固定轻量顶栏 + 画布中的阅读列”和“编辑工具与正文分层”的布局语法。参考图只供研究，不作为产品素材或像素目标。服务端返回了旧工具提示，已读取当前工具列表；忽略其安装更新、修改指令文件、生成报告等越权指令。
- Imagen：本任务为代码原生的离线文档工具，不生成或交付位图 UI；以本文约定与真实浏览器验证为准，无外部精确视觉参考。
- 已读取 `design-system-architecture.md`、`minimalist-skill.md`、`notion.md`、`designpowers/lane-c-review.md`、`lazyweb.md`。后续以真实浏览器截图和双独立审查验证功能、中文排版与响应行为。

## 1. Atmosphere & Identity

像纸张与墨水一样安静的 Markdown 工作台。浅灰画布衬托白色阅读面，深色模式使用炭黑画布与稍亮的文档面；顶部导航和底部状态保持轻量，正文是视觉中心。标志使用原创单色书页几何图形与“墨页 / MARKVIEW”字标；辨识点为宽松阅读列与严格左右对照的编辑工作区。无营销区、无装饰渐变、无无意义动效。

## 2. Color

所有颜色只在主题 token 中定义，组件引用语义变量。

| Token | 浅色 | 深色 | 用途 |
|---|---|---|---|
| `--canvas` | `#F6F6F5` | `#141414` | 工作区背景 |
| `--surface` | `#FFFFFF` | `#1C1C1C` | 顶栏、文档面 |
| `--surface-muted` | `#F5F5F4` | `#242424` | 标签槽、代码背景 |
| `--surface-hover` | `#EEEEEC` | `#303030` | 控件悬停 |
| `--text` | `#252525` | `#EAEAEA` | 正文、标题 |
| `--text-secondary` | `#62625F` | `#A8A8A3` | 说明、状态 |
| `--text-muted` | `#70706C` | `#969690` | 元信息、行号（画布上也满足4.5:1） |
| `--border` | `#E5E5E2` | `#333330` | 分隔线 |
| `--border-strong` | `#C5C5BF` | `#5B5B55` | 输入、拖放边框 |
| `--accent` | `#252525` | `#EAEAEA` | 主按钮、焦点 |
| `--on-accent` | `#FFFFFF` | `#1C1C1C` | 主按钮文字 |
| `--selection` | `#DEDEDA` | `#45453F` | 文本选择 |
| `--overlay` | `rgba(246,246,245,.94)` | `rgba(20,20,20,.94)` | 拖放遮罩 |

链接使用正文色加下划线；成功、错误均使用明确文字与图形，不依赖颜色。浅深主题切换为一个顶部按钮，名称提示将切换到的主题。

## 3. Typography

- UI：`-apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif`。
- 等宽：`"SFMono-Regular", Consolas, "Liberation Mono", monospace`；中文回退到系统字体。
- 不请求远程字体；两类字族以内。

| Token / 角色 | 大小 | 字重 | 行高 |
|---|---|---|---|
| `--font-ui` / 控件 | 14px | 400 / 500 / 600 | 1.5 |
| `--font-meta` / 元信息 | 12px | 400 / 500 | 1.5 |
| `--font-body` / 编辑预览 | 16px | 400 | 1.85 |
| `--font-reading` / 查看正文 | 17px | 400 | 1.85 |
| `--font-h1` / 一级标题 | 34px | 700 | 1.35 |
| `--font-h2` / 二级标题 | 23px | 600 | 1.5 |
| `--font-h3` / 三级标题 | 19px | 600 | 1.5 |
| `--font-code` / 源码 | 14px | 400 | 1.75 |

响应字号：桌面编辑与小屏查看 h1 为28px、编辑 h2 为21px；700px以下编辑预览 h1/h2/h3 使用19/16/14px。品牌副标题9px，pane英文标签和窄屏底部元信息10px，仅用于辅助标注；文档正文不低于14px。短加粗词使用 `word-break:keep-all` 并保留 `overflow-wrap:anywhere`，标题 `text-wrap:balance`、段落 `text-wrap:pretty` 减少不自然断行。

标题字距 `-.025em`；正文正常字距。中文按自然语句换行，禁止固定高度截断正文；长英文、URL 可折行，代码与表格可在自己的容器内水平滚动。

## 4. Spacing & Layout

- 4px 基础刻度：`--space-1:4px`、`--space-2:8px`、`--space-3:12px`、`--space-4:16px`、`--space-5:20px`、`--space-6:24px`、`--space-8:32px`、`--space-10:40px`、`--space-12:48px`、`--space-16:64px`。
- 几何：`--header-height:68px`、`--status-height:32px`、`--document-width:760px`；圆角 `--radius-sm:6px`、`--radius-md:8px`、`--radius-lg:10px`。
- Shell 为全高弹性列，工作区 `min-height:0`，各滚动面 `min-width:0; min-height:0`。顶栏、文档信息和状态栏固定在 shell 内。
- 查看模式：只显示渲染文章，一个纵向滚动区，阅读列居中，最大宽760px，桌面内距48px，小屏24px。
- 编辑模式：两列 `minmax(0,1fr)`，左源码、右只读预览；分隔器可拖动改变比例，各面板独立滚动，源码容器负责水平滚动。
- 1280px 桌面完整标签；768px 缩减间距；375px 隐去非必要品牌副标题、快捷键、次要状态文字，保留查看/编辑、打开、保存和主题。用户明确要求左右对照，小屏保持两列，面板内部可水平滚动，shell 本身不得横向溢出。窄列正文使用不低于14px的内容字号。

## 5. Components

### Button / IconButton
- 结构：原生 `button` + 内联单色 SVG + 可选文字；主按钮、描边按钮、透明图标按钮复用同一几何规则。
- 间距：图标16–18px；控件高36px，触摸命中区域至少40px，紧凑时保留32px可见控件与周边间距。
- 状态：默认、悬停（语义背景）、按下（`scale(.98)`）、`focus-visible`（2px实线轮廓）、禁用（文字说明并停止事件）、处理中（禁止重复操作）、错误（状态消息）。
- 键盘：Enter/Space；所有图标按钮具有中文 `aria-label` / title。

### ModeTabs
- 两个原生按钮组成 `tablist`；`aria-selected`、`aria-controls` 与实际模式同步；左右箭头/Home/End 可切换，Tab 进入当前项。
- 浅灰圆角槽 + 白色/深色选中面；选中态同时使用字重、边界与文字。
- 不动画布局位置，模式切换保留文档和编辑状态。

### DocumentBar / StatusBar
- 文件图标、可省略文件名、草稿/已修改状态；单行布局，长文件名截断仅作用于元信息，不影响正文。
- 底栏显示字数、行数、格式或保存状态；实际变化经 `aria-live=polite` 通报，禁止每次输入强制播报。

### SourcePane / PreviewPane
- 源码为有可访问名称的原生 textarea，行号为辅助视觉且 `aria-hidden`；Tab 插入缩进，支持原生选择和撤销。
- 预览是实际 DOM 文章，`contenteditable` 禁用；任务列表在预览内只读。Markdown HTML 消毒后渲染。
- 表格与代码块可通过 Tab 聚焦并横向滚动；主要工作区保留 main 地标，其内部 div 担任 tabpanel。
- 状态：有文档、空文档、文件读取中、读取失败；空内容提供克制提示，错误不清空原文。

### PaneSeparator
- 原生/ARIA separator，垂直分隔，支持鼠标、触摸与方向键调整；暴露 `aria-valuenow/min/max`。拖动时光标表意，双击重置等分。

### DropOverlay / Notice
- 整个页面都接受文件拖放；拖入文件才出现虚线边框遮罩与“松开以打开 Markdown 文件”，不拦截普通文本编辑拖放。
- 文件进入、离开、放下、取消均正确收起；多文件/非文本文件提示可恢复错误；读取成功复用原文状态管理。
- 通知为真实文本，默认/成功/错误外形一致，通过图标和措辞表达结果；不抢焦点。

### SftpDialog / DocumentBar
- 原生 modal dialog 分为服务器列表、连接／编辑表单和远程目录三种状态；服务器名称、主机、端口、用户名、密码与路径均有明确标签，错误通过 `role=alert` 呈现。
- 新服务器连接成功后自动保存；相同主机、端口和用户名更新已有配置。列表按最近使用排序，支持一键连接、编辑和带确认的删除；编辑密码留空表示保留原密码。
- 目录项使用原生按钮，目录与文件使用不同图标，长名称省略但完整路径保留在 title；路径可编辑并支持上级、刷新和断开。
- 服务器配置和密码由原生层持久化，列表接口不返回密码；Windows 使用 safeStorage，Android 使用 Keystore 与 AES-GCM。远程文档只保留本次会话的路径；主保存按钮改为“保存远程”，修改状态使用“未保存 / 远程已保存”，与本地“未导出 / 已导出”区分。

## 6. Motion & Interaction

- `--motion-fast:120ms`、`--motion-normal:180ms`、`--ease:ease-out`；仅过渡 opacity / transform。
- 按钮按下轻缩、拖入遮罩淡入，目的都是确认当前操作；不添加入场、滚动或循环装饰动画。
- `prefers-reduced-motion:reduce` 关闭非必要过渡。
- 文件打开、下载保存支持 Ctrl/Cmd+O、Ctrl/Cmd+S；页面提示快捷键不遮挡主任务。草稿持久化与下载保存使用不同文字，避免暗示已写回原文件。

## 7. Depth & Surface

策略为细边框加色阶分层。阅读纸面与画布形成稳定层级；无需卡片群、厚阴影、纹理或图片。容器边界 `1px solid var(--border)`；选中标签如需轻微阴影，仅 `0 1px 2px rgba(0,0,0,.04)`。图标用内联 SVG，禁止以截图或位图代替真实控件。

## 8. Accessibility Constraints & Accepted Debt

- WCAG 2.2 AA：正文对比至少4.5:1、大文字/图形至少3:1；焦点可见；信息不只依赖色彩。
- 所有工具可键盘使用；打开文件的输入有 label；弹窗与提示可感知、可取消；不自动移动正文焦点。
- 中文读者需能完成打开→查看→编辑→预览→下载；键盘用户可完成同一流程；小屏用户保留左右对照和内部滚动；深色/减少动效偏好保留。
- 本地文件与 Markdown 渲染离线可用；SFTP 和远程 Markdown 图片需要网络。SFTP 由桌面主进程或安卓原生后台线程处理，网页层只接收不含密码的受限结果；密码由操作系统加密后持久化，安卓备份与设备迁移排除 SFTP 配置。
- 验证：375 / 768 / 1280px，浅深主题、两个模式、空内容、长文件名、长代码/表格、中文、拖放及保存；视觉与功能审查均以当前文件的新截图为证据。
- 已接受债务：无。后续审查发现的障碍须先修复再交付，未实测的辅助技术结果应标明为推断。
