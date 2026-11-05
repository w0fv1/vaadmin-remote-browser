# Vaadmin Remote Browser

Vaadmin 系列的远程浏览器组件：把本机真实 Chrome 嵌进 Vaadin 应用，服务端托管一个持久化浏览器进程，页面通过 WebSocket 接收实时画面并回传鼠标、键盘、滚轮与标签页操作。以库的形式发布，附带 Spring Boot Starter 自动配置。

与 [vaadmin](../vaadmin) 同一命名体系与发布坐标（`dev.w0fv1.vaadmin.*`），但保持独立仓库与独立版本，不并入 vaadmin 的构建。

## 引入

仓库内源码引用（与 `vaadmin` 一致）：

```kotlin
// Nfirco settings.gradle.kts
include(":app:_sub:vaadmin-remote-browser")

// Nfirco build.gradle.kts
implementation(project(":app:_sub:vaadmin-remote-browser"))
```

Maven 坐标：`dev.w0fv1:vaadmin-remote-browser`。库自带 `META-INF/frontend` 与自动配置，宿主应用只需是 Spring Boot + Vaadin 应用。

## 用法

```java
// 直接嵌画面（自带配置选择、启动按钮、状态与画布）
add(new RemoteBrowserViewer(remoteBrowser));

// 固定首个标签页地址
RemoteBrowserViewer viewer = new RemoteBrowserViewer(remoteBrowser);
viewer.setInitialUrl("https://example.com/");
add(viewer);
```

```java
// 只用生命周期，画面由自己布局承载
try (RemoteBrowserSession session = remoteBrowser.open(BrowserProfileId.DEFAULT, "https://example.com/")) {
    stream.setStreamPath(remoteBrowser.streamPath());
    stream.setSessionId(session.sessionId());
}
```

## API

| 类型 | 职责 |
| --- | --- |
| [`RemoteBrowser`](src/main/java/dev/w0fv1/vaadmin/remotebrowser/RemoteBrowser.java) | 门面：配置列表、画像读写、开启会话、查询状态、流地址 |
| [`RemoteBrowserSession`](src/main/java/dev/w0fv1/vaadmin/remotebrowser/RemoteBrowserSession.java) | 会话句柄：`sessionId()`、`profileId()`、`environmentMode()`、`close()` |
| [`BrowserProxyMode`](src/main/java/dev/w0fv1/vaadmin/remotebrowser/BrowserProxyMode.java) | 私有会话的系统代理或直连策略，通过 `openPrivate` 指定 |
| [`RemoteBrowserViewer`](src/main/java/dev/w0fv1/vaadmin/remotebrowser/RemoteBrowserViewer.java) | 开箱即用的 Vaadin 组件，内含 `BrowserStreamComponent` |
| [`BrowserStreamComponent`](src/main/java/dev/w0fv1/vaadmin/remotebrowser/BrowserStreamComponent.java) | 只负责画面与输入的元素，供自定义布局使用 |

会话按 Profile 共享：同一 Profile 的多个 `open` 复用同一个浏览器进程，最后一个句柄关闭才退出。同一 Profile 同时只允许一个活动会话，避免多个 Chrome 争用同一目录。

### 普通窗口与隐私窗口

- `open(profileId, url)`：使用该 Profile 的持久化目录，Cookie、登录态、历史记录长期保留。
- `openPrivate(templateProfile, url)`：以模板 Profile 的环境画像新建一次性目录（`<profile-root>/private/private-<uuid>`），每次打开都是全新状态；句柄关闭后目录连同全部状态删除。

示例组件 `RemoteBrowserViewer` 提供「启动浏览器」与「隐私窗口」两个入口。

## Spring Boot Starter

`browser.remote.enabled=true` 时自动注册 `RemoteBrowser`（含 `BrowserRuntime`、`BrowserEnvironmentStore`、`BrowserSessionManager`）与 WebSocket 端点 `RemoteBrowserWebSocketConfigurer`；所有 Bean 都是 `@ConditionalOnMissingBean`，可以直接覆盖。自动配置入口见 [`RemoteBrowserAutoConfiguration`](src/main/java/dev/w0fv1/vaadmin/remotebrowser/RemoteBrowserAutoConfiguration.java) 与 [`RemoteBrowserWebSocketAutoConfiguration`](src/main/java/dev/w0fv1/vaadmin/remotebrowser/RemoteBrowserWebSocketAutoConfiguration.java)。

WebSocket 默认只接受同源连接；需要跨源时显式配置 `browser.remote.allowed-origin-patterns`。

## 配置

| 配置 | 环境变量 | 默认值 |
| --- | --- | --- |
| `browser.remote.enabled` | `BROWSER_REMOTE_ENABLED` | `true` |
| `browser.remote.sandbox` | `BROWSER_REMOTE_SANDBOX` | `true` |
| `browser.remote.mode` | `BROWSER_REMOTE_MODE` | `headed` |
| `browser.remote.channel` | `BROWSER_REMOTE_CHANNEL` | `chrome` |
| `browser.remote.profile-root` | `BROWSER_REMOTE_PROFILE_ROOT` | `~/.vaadmin-remote-browser/profiles` |
| `browser.remote.executable` | `BROWSER_REMOTE_EXECUTABLE` | 按通道自动定位 |
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

- 安装 Chrome/Chromium（自动定位 `/usr/bin/google-chrome`、`/usr/bin/chromium`、`/opt/google/chrome/chrome` 等；其它位置用 `browser.remote.executable` 指定）
- 中文字体（如 `fonts-noto-cjk`）与常见运行库，否则页面文字与视频会缺字
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
- `permissions` 使用 [`BrowserPermission.java`](src/main/java/dev/w0fv1/vaadmin/remotebrowser/BrowserPermission.java) 中的枚举名，按 CDP 权限类型授权。

画像在 Profile 租约创建时读取，运行中不热更新；修改后新建会话生效。视口尺寸由页面 `viewport.resize` 流程管理，不写入画像。

## 反自动化检测不变量

由 [`BrowserStealthTest.java`](src/test/java/dev/w0fv1/vaadmin/remotebrowser/BrowserStealthTest.java) 守护，改动时不要绕过：

- 只启动本机安装的原生 Chrome/Edge，不引入 Playwright、Puppeteer、Selenium 等自动化启动器。
- 启动参数只有 [`BrowserRuntime#arguments`](src/main/java/dev/w0fv1/vaadmin/remotebrowser/BrowserRuntime.java) 一个来源；其中不出现 `--enable-automation`，并用 `--disable-blink-features=AutomationControlled` 保持 `navigator.webdriver === false`。
- 不开启 `Runtime.enable`、不使用 `Page.addScriptToEvaluateOnNewDocument`、不注入任何页面脚本；标签页地址与标题取自 `Target.getTargetInfo`。
- 页面环境只用 Chrome 启动参数与 `Emulation` 域的浏览器原生覆盖实现，见 [`BrowserPageEnvironment.java`](src/main/java/dev/w0fv1/vaadmin/remotebrowser/BrowserPageEnvironment.java)，保持原型链与函数 `toString` 原样输出。
- 输入按真实鼠标、键盘语义下发：修饰键位掩码、点击计数、`code`/`keyCode`/`location`，不伪造事件时间戳。
- 关闭浏览器先发 `Browser.close` 并等待进程自行退出，保证 profile 落盘。

## 结构

| 职责 | 入口 |
| --- | --- |
| 对外门面 | [`RemoteBrowser.java`](src/main/java/dev/w0fv1/vaadmin/remotebrowser/RemoteBrowser.java)、[`RemoteBrowserSession.java`](src/main/java/dev/w0fv1/vaadmin/remotebrowser/RemoteBrowserSession.java) |
| Vaadin 组件 | [`RemoteBrowserViewer.java`](src/main/java/dev/w0fv1/vaadmin/remotebrowser/RemoteBrowserViewer.java)、[`BrowserStreamComponent.java`](src/main/java/dev/w0fv1/vaadmin/remotebrowser/BrowserStreamComponent.java)、[`browser-stream.js`](src/main/resources/META-INF/frontend/browser-stream.js) |
| 自动配置 | [`RemoteBrowserAutoConfiguration.java`](src/main/java/dev/w0fv1/vaadmin/remotebrowser/RemoteBrowserAutoConfiguration.java)、[`RemoteBrowserWebSocketAutoConfiguration.java`](src/main/java/dev/w0fv1/vaadmin/remotebrowser/RemoteBrowserWebSocketAutoConfiguration.java) |
| 运行配置 | [`BrowserRuntimeProperties.java`](src/main/java/dev/w0fv1/vaadmin/remotebrowser/BrowserRuntimeProperties.java) |
| 浏览器进程与启动参数 | [`BrowserRuntime.java`](src/main/java/dev/w0fv1/vaadmin/remotebrowser/BrowserRuntime.java)、[`ChromeProcess.java`](src/main/java/dev/w0fv1/vaadmin/remotebrowser/ChromeProcess.java)、[`ChromeBrowser.java`](src/main/java/dev/w0fv1/vaadmin/remotebrowser/ChromeBrowser.java) |
| 可执行文件定位 | [`BrowserChannel.java`](src/main/java/dev/w0fv1/vaadmin/remotebrowser/BrowserChannel.java) |
| DevTools 协议传输 | [`CdpConnection.java`](src/main/java/dev/w0fv1/vaadmin/remotebrowser/CdpConnection.java)、[`CdpSession.java`](src/main/java/dev/w0fv1/vaadmin/remotebrowser/CdpSession.java) |
| Profile 目录与租约 | [`BrowserProfileRegistry.java`](src/main/java/dev/w0fv1/vaadmin/remotebrowser/BrowserProfileRegistry.java) |
| 环境画像与校验 | [`BrowserEnvironmentProfile.java`](src/main/java/dev/w0fv1/vaadmin/remotebrowser/BrowserEnvironmentProfile.java)、[`BrowserEnvironmentStore.java`](src/main/java/dev/w0fv1/vaadmin/remotebrowser/BrowserEnvironmentStore.java) |
| 页面环境覆盖 | [`BrowserPageEnvironment.java`](src/main/java/dev/w0fv1/vaadmin/remotebrowser/BrowserPageEnvironment.java) |
| 会话、输入与失败状态 | [`BrowserSession.java`](src/main/java/dev/w0fv1/vaadmin/remotebrowser/BrowserSession.java)、[`BrowserSessionManager.java`](src/main/java/dev/w0fv1/vaadmin/remotebrowser/BrowserSessionManager.java) |
| 输入协议 | [`BrowserInput.java`](src/main/java/dev/w0fv1/vaadmin/remotebrowser/BrowserInput.java)、[`BrowserKeyInput.java`](src/main/java/dev/w0fv1/vaadmin/remotebrowser/BrowserKeyInput.java) |
| 标签页与采帧 | [`BrowserTab.java`](src/main/java/dev/w0fv1/vaadmin/remotebrowser/BrowserTab.java)、[`BrowserViewport.java`](src/main/java/dev/w0fv1/vaadmin/remotebrowser/BrowserViewport.java) |
| WebSocket 协议与遥测 | [`BrowserStreamHandler.java`](src/main/java/dev/w0fv1/vaadmin/remotebrowser/BrowserStreamHandler.java) |

前端到服务端使用 `viewport.resize`、`tab.*`、`stream.configure`、指针消息（`move`/`down`/`up`/`cancel`/`context`/`wheel`）、键盘消息（`key`）与输入法提交（`text`）；服务端返回带 12 字节帧头的 JPEG 分片、会话状态和 `telemetry` 消息。

## 示例应用

[`sample/`](sample) 是一个最小 Vaadin 应用，只通过上面的公开 API 嵌入画面，用来验证 starter 在真实应用里可用（示例只做组件扫描自己的包，自动配置由 `AutoConfiguration.imports` 生效）：

```powershell
.\gradlew.bat :sample:bootRun
```

打开 `http://localhost:8080/?url=https://example.com/`。示例同样受 `browser.remote.*` 配置控制。

## 构建与测试

```powershell
.\gradlew.bat test          # 库的单元与真实 Chrome 集成测试
.\gradlew.bat headedTest    # 需要图形桌面的有头模式测试
```

[`BrowserSessionContractTest.java`](src/test/java/dev/w0fv1/vaadmin/remotebrowser/BrowserSessionContractTest.java) 使用本机真实 Chrome 与回环 HTTP 页面，在两种模式下执行同一组功能契约。

## 安全

库本身不含身份认证、会话归属、目标地址白名单与配额。**不要把它直接暴露到公网。** 对外部署前至少需要：

- 登录态与 WebSocket 鉴权
- 会话与用户绑定
- 目标 URL / scheme 策略
- 会话数量、标签页数量与带宽配额

## License

MIT
