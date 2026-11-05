# 运行与配置

[文档首页](../README.md) · [API](api.md)

默认使用随 JAR 携带的 Chrome for Testing，默认模式为 headless。版本、平台、下载地址和 SHA-256 统一见 [browser-lock.json](../runtime/browser-lock.json)。支持 Windows x64 与 Debian 12 基线的 Linux x64。

构建时通过 `-PbrowserPlatform=linux64` 选择 Linux 包；Linux 构建需要 Podman 来打包动态库和字体，也可用 `-PbrowserRuntimeDirectory=/path/to/runtime` 指定准备好的资源目录。构建入口见 [prepare.py](../runtime/prepare.py) 和 [Gradle](../build.gradle.kts)。Windows 默认构建 win64 包，跨平台发布必须明确选择目标平台。

运行时不下载，也不安装系统软件。首次使用校验摘要并解包至 `~/.vaadmin-remote-browser/runtime/<sha256>`，同一内容后续启动直接复用；不同内容使用独立目录。文件锁与原子移动保护并发解包。实现与测试见 [BundledBrowser](../src/main/java/dev/w0fv1/vaadmin/remotebrowser/BundledBrowser.java)、[BundledBrowserTest](../src/test/java/dev/w0fv1/vaadmin/remotebrowser/BundledBrowserTest.java)。

显式配置 `browser.remote.executable` 可使用宿主提供的可执行文件，Edge 通道使用本地查找。Linux 包携带动态库与字体，不要求在生产机安装 Chromium；宿主仍需满足上述 Linux 基线及浏览器沙箱运行条件。

## Spring Boot Starter

`browser.remote.enabled=true` 时自动注册 `RemoteBrowser`（含 `BrowserRuntime`、`BrowserEnvironmentStore`、`BrowserSessionManager`）与 WebSocket 端点 `RemoteBrowserWebSocketConfigurer`；所有 Bean 都是 `@ConditionalOnMissingBean`，可以直接覆盖。自动配置入口见 [`RemoteBrowserAutoConfiguration`](../src/main/java/dev/w0fv1/vaadmin/remotebrowser/RemoteBrowserAutoConfiguration.java) 与 [`RemoteBrowserWebSocketAutoConfiguration`](../src/main/java/dev/w0fv1/vaadmin/remotebrowser/RemoteBrowserWebSocketAutoConfiguration.java)。

WebSocket 默认只接受同源连接；需要跨源时显式配置 `browser.remote.allowed-origin-patterns`。

## 配置

| 配置 | 环境变量 | 默认值 |
| --- | --- | --- |
| `browser.remote.enabled` | `BROWSER_REMOTE_ENABLED` | `true` |
| `browser.remote.sandbox` | `BROWSER_REMOTE_SANDBOX` | `true` |
| `browser.remote.mode` | `BROWSER_REMOTE_MODE` | `headless` |
| `browser.remote.channel` | `BROWSER_REMOTE_CHANNEL` | `chrome` |
| `browser.remote.profile-root` | `BROWSER_REMOTE_PROFILE_ROOT` | `~/.vaadmin-remote-browser/profiles` |
| `browser.remote.executable` | `BROWSER_REMOTE_EXECUTABLE` | 随包 Chrome；Edge 按通道定位 |
| `browser.remote.websocket-path` | `BROWSER_REMOTE_WEBSOCKET_PATH` | `/browser-stream` |
| `browser.remote.allowed-origin-patterns` | `BROWSER_REMOTE_ALLOWED_ORIGIN_PATTERNS` | 空（仅同源） |
| `browser.remote.media-policy` | `BROWSER_REMOTE_MEDIA_POLICY` | `BLOCK` |

通道只接受 `chrome` 与 `msedge`，运行模式只接受 `headed` 与 `headless`。无头模式没有真实操作系统窗口，GPU、媒体解码、字体与窗口外观仍取决于运行环境；需要完整 headed 行为时应在隔离的虚拟显示环境运行 headed，而不是在 headless 上伪装。

## 托管行为

- 媒体策略：`ALLOW` 原样播放；`MUTE` 追加 `--mute-audio`；`BLOCK` 再禁止自动播放并拦截 `HTMLMediaElement.play`，页面既不出声也播不起来。
- 光标：页面把指针下的 `cursor` 样式上报，客户端画布同步显示（`BrowserTab.CURSOR_BINDING`）。
- 剪贴板：客户端 `Ctrl+C` / `Ctrl+X` 读回远端剪贴板写入本地，本地 `Ctrl+V` 直接注入远端输入（`clipboard.read` 与 `text` 消息）。
- 静帧：连续两帧相同时抓帧间隔回落到 `BrowserStreamHandler.IDLE_INTERVAL_MILLIS`，画面一变化立刻恢复。
- 启动参数固定关闭扩展、同步、组件更新与后台联网，削减无关内存与磁盘占用（`BrowserRuntime.HOSTED_FLAGS`）。
- 关闭常规标签页会关闭对应的真实页面；关闭最后一个标签页时改为把该标签重置为 `about:blank`，因为 Chrome 在最后一个页面关闭后会退出整个浏览器进程（`BrowserSession.closeTab`）。


## Linux 与容器

控制面只用 DevTools 协议，headed 模式的窗口操作走 `Browser` 域，因此在无图形界面的 Linux 上以 `headless` 运行即可，不需要 X11。部署时只需要满足：

- 发布目标平台对应的浏览器运行包，或通过 `browser.remote.executable` 显式指定可执行文件
- 随包运行时包含动态库与中英文字体；使用外部可执行文件时由宿主准备依赖
- 以 root 或容器内运行时 Chrome 会拒绝启动，此时显式设置 `browser.remote.sandbox=false`（等价 `--no-sandbox`，会削弱进程隔离，只在这种环境用）
- 进程目录可写：`browser.remote.profile-root` 需要有足够空间放 Chrome 的用户数据与隐私窗口的一次性目录
- `gradlew` 以 `100755` 入库；权限若在解压、复制或跨平台中转时丢失，用 `chmod +x gradlew` 恢复，否则第一条命令就是 `Permission denied`

## 浏览器环境画像

每个 Profile 的 Chrome 数据目录下可直接放置 `environment.json`：

```text
<profile-root>/<profile-id>/
├── environment.json
└── Chrome 配置、Cookie 与本地存储
```

没有该文件时使用 `NATIVE` 模式，完整继承真实 Chrome 与操作系统环境。固定环境时写入：

```json
{
  "profileId": "demo",
  "mode": "MANAGED",
  "locale": "fr-CA",
  "timezone": "America/Toronto",
  "colorScheme": "DARK",
  "deviceScaleFactor": 1.25,
  "permissions": ["GEOLOCATION", "CLIPBOARD_READ"]
}
```

- `profileId` 必须与目录名一致，只允许字母、数字、下划线和连字符，最长 64 个字符。
- `mode` 为 `NATIVE` 或 `MANAGED`；`NATIVE` 不允许覆盖其他环境字段。
- `locale` 使用 BCP 47 标签，同时写入 Chrome 的 `--accept-lang` 与页面 locale 覆盖，保证 `navigator.language`、`navigator.languages` 与请求头一致。
- `timezone` 使用 IANA 时区标识，`colorScheme` 为 `LIGHT`、`DARK` 或 `NO_PREFERENCE`。
- `deviceScaleFactor` 范围为 `0.5` 至 `4.0`，通过 Chrome 的设备像素比启动参数应用。
- `permissions` 使用 [`BrowserPermission.java`](../src/main/java/dev/w0fv1/vaadmin/remotebrowser/BrowserPermission.java) 中的枚举名，按 CDP 权限类型授权。

画像在 Profile 租约创建时读取，运行中不热更新；修改后新建会话生效。视口尺寸由页面 `viewport.resize` 流程管理，不写入画像。

## 浏览器行为验证

启动参数与原生环境行为的契约见 [BrowserStealthTest](../src/test/java/dev/w0fv1/vaadmin/remotebrowser/BrowserStealthTest.java)。脚本执行是显式公开能力，调用方式见 [API](api.md)。

## 结构

| 职责 | 入口 |
| --- | --- |
| 对外门面 | [`RemoteBrowser.java`](../src/main/java/dev/w0fv1/vaadmin/remotebrowser/RemoteBrowser.java)、[`RemoteBrowserSession.java`](../src/main/java/dev/w0fv1/vaadmin/remotebrowser/RemoteBrowserSession.java) |
| Vaadin 组件 | [`RemoteBrowserViewer.java`](../src/main/java/dev/w0fv1/vaadmin/remotebrowser/RemoteBrowserViewer.java)、[`BrowserStreamComponent.java`](../src/main/java/dev/w0fv1/vaadmin/remotebrowser/BrowserStreamComponent.java)、[`browser-stream.js`](../src/main/resources/META-INF/frontend/browser-stream.js) |
| 自动配置 | [`RemoteBrowserAutoConfiguration.java`](../src/main/java/dev/w0fv1/vaadmin/remotebrowser/RemoteBrowserAutoConfiguration.java)、[`RemoteBrowserWebSocketAutoConfiguration.java`](../src/main/java/dev/w0fv1/vaadmin/remotebrowser/RemoteBrowserWebSocketAutoConfiguration.java) |
| 运行配置 | [`BrowserRuntimeProperties.java`](../src/main/java/dev/w0fv1/vaadmin/remotebrowser/BrowserRuntimeProperties.java) |
| 浏览器进程与启动参数 | [`BrowserRuntime.java`](../src/main/java/dev/w0fv1/vaadmin/remotebrowser/BrowserRuntime.java)、[`ChromeProcess.java`](../src/main/java/dev/w0fv1/vaadmin/remotebrowser/ChromeProcess.java)、[`ChromeBrowser.java`](../src/main/java/dev/w0fv1/vaadmin/remotebrowser/ChromeBrowser.java) |
| 可执行文件定位 | [`BrowserChannel.java`](../src/main/java/dev/w0fv1/vaadmin/remotebrowser/BrowserChannel.java) |
| DevTools 协议传输 | [`CdpConnection.java`](../src/main/java/dev/w0fv1/vaadmin/remotebrowser/CdpConnection.java)、[`CdpSession.java`](../src/main/java/dev/w0fv1/vaadmin/remotebrowser/CdpSession.java) |
| Profile 目录与租约 | [`BrowserProfileRegistry.java`](../src/main/java/dev/w0fv1/vaadmin/remotebrowser/BrowserProfileRegistry.java) |
| 环境画像与校验 | [`BrowserEnvironmentProfile.java`](../src/main/java/dev/w0fv1/vaadmin/remotebrowser/BrowserEnvironmentProfile.java)、[`BrowserEnvironmentStore.java`](../src/main/java/dev/w0fv1/vaadmin/remotebrowser/BrowserEnvironmentStore.java) |
| 页面环境覆盖 | [`BrowserPageEnvironment.java`](../src/main/java/dev/w0fv1/vaadmin/remotebrowser/BrowserPageEnvironment.java) |
| 会话、输入与失败状态 | [`BrowserSession.java`](../src/main/java/dev/w0fv1/vaadmin/remotebrowser/BrowserSession.java)、[`BrowserSessionManager.java`](../src/main/java/dev/w0fv1/vaadmin/remotebrowser/BrowserSessionManager.java) |
| 输入协议 | [`BrowserInput.java`](../src/main/java/dev/w0fv1/vaadmin/remotebrowser/BrowserInput.java)、[`BrowserKeyInput.java`](../src/main/java/dev/w0fv1/vaadmin/remotebrowser/BrowserKeyInput.java) |
| 标签页与采帧 | [`BrowserTab.java`](../src/main/java/dev/w0fv1/vaadmin/remotebrowser/BrowserTab.java)、[`BrowserViewport.java`](../src/main/java/dev/w0fv1/vaadmin/remotebrowser/BrowserViewport.java) |
| WebSocket 协议与遥测 | [`BrowserStreamHandler.java`](../src/main/java/dev/w0fv1/vaadmin/remotebrowser/BrowserStreamHandler.java) |

前端到服务端使用 `viewport.resize`、`tab.*`、`stream.configure`、指针消息（`move`/`down`/`up`/`cancel`/`context`/`wheel`）、键盘消息（`key`）与输入法提交（`text`）；服务端帧协议与视口尺寸见 [`BrowserFrame`](../src/main/java/dev/w0fv1/vaadmin/remotebrowser/BrowserFrame.java) 和 [`BrowserStreamHandler`](../src/main/java/dev/w0fv1/vaadmin/remotebrowser/BrowserStreamHandler.java)。


## 发布验证

[BrowserRuntimeProbe](../src/main/java/dev/w0fv1/vaadmin/remotebrowser/BrowserRuntimeProbe.java) 提供命令行探针，使用随包浏览器创建直连 headless 私有会话并执行脚本，成功输出 `BROWSER_RUNTIME_OK`，失败以非零退出。将库及其依赖置于 classpath 后运行：

```sh
java -cp 'lib/*' dev.w0fv1.vaadmin.remotebrowser.BrowserRuntimeProbe
```

探针保留正常沙箱设置，关闭会话并清理私有目录，不下载或安装系统软件。应在目标机器切流前运行，验证目标平台及运行依赖。
