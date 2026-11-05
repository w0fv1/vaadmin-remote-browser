# API 与调用示例

[文档首页](../README.md) · [运行配置](runtime.md)

公开 API 位于 `dev.w0fv1.vaadmin.remotebrowser`。这是 Java API；WebSocket 用于组件画面与输入，库没有提供独立的 HTTP REST 下载服务。

## 入口

| 类型 | 用途 |
| --- | --- |
| [RemoteBrowser](../src/main/java/dev/w0fv1/vaadmin/remotebrowser/RemoteBrowser.java) | 注入门面：会话、环境画像、一次性资源读取 |
| [RemoteBrowserSession](../src/main/java/dev/w0fv1/vaadmin/remotebrowser/RemoteBrowserSession.java) | AutoCloseable 会话：标识、脚本执行、资源读取、释放 |
| [RemoteBrowserViewer](../src/main/java/dev/w0fv1/vaadmin/remotebrowser/RemoteBrowserViewer.java) | 带启动与配置入口的 Vaadin 组件 |
| [BrowserStreamComponent](../src/main/java/dev/w0fv1/vaadmin/remotebrowser/BrowserStreamComponent.java) | 用于自定义布局的画面与输入组件 |
| [BrowserResource](../src/main/java/dev/w0fv1/vaadmin/remotebrowser/BrowserResource.java) | 最终 URL、MIME、原始字节 |

## 创建与释放会话

```java
RemoteBrowserSession persistent = browser.open(BrowserProfileId.DEFAULT, "https://example.com/");
RemoteBrowserSession isolated = browser.openPrivate(BrowserProfileId.DEFAULT, "https://example.com/");
```

`open` 复用 Profile 的持久化状态；同一 Profile 的句柄共享浏览器，最后一个句柄关闭才释放进程。`openPrivate` 创建独立临时目录，关闭后删除状态。两者都必须由调用方管理 `close()`，不要在交互完成前关闭会话。

`profiles()` 列举配置，`environment(profileId)` 读取环境画像，`saveEnvironment(environment)` 保存画像，`isOpen(profileId)` 查询活动状态。画像在新建会话时生效；字段定义见 [BrowserEnvironmentProfile](../src/main/java/dev/w0fv1/vaadmin/remotebrowser/BrowserEnvironmentProfile.java)。

## 关闭代理

```java
RemoteBrowserSession session = browser.openPrivate(
        BrowserProfileId.DEFAULT, "about:blank", BrowserProxyMode.DIRECT);
```

`DIRECT` 通过浏览器启动参数关闭代理；`SYSTEM` 使用浏览器默认的系统代理行为。不传第三个参数时为 `SYSTEM`。策略在私有会话创建时确定，不支持运行中切换，也没有自定义代理地址参数。UI 的 `noproxy` 勾选项可映射为 `DIRECT`，取消则为 `SYSTEM`。

## 嵌入纯页面

```java
BrowserStreamComponent stream = new BrowserStreamComponent();
stream.setStreamPath(browser.streamPath());
stream.setSessionId(session.sessionId());
stream.setOnlyPage(true);
add(stream);
```

`onlyPage` 隐藏组件的标签栏和地址导航栏，保留画面与输入。组件移除时由拥有会话的调用方释放会话。

## 执行脚本

```java
var title = session.evaluate("document.title");
```

返回 Jackson `JsonNode`，作用于当前页面。只执行调用方可信的表达式。`sessionId()`、`profileId()`、`environmentMode()` 可用于识别会话。

## 读取图片或其他资源

一次性读取会自动创建和关闭独立私有会话：

```java
BrowserResource image = browser.loadResource(url, "image/", BrowserProxyMode.DIRECT);
String finalUrl = image.url();
String mime = image.contentType();
byte[] bytes = image.content();
```

连续读取时复用会话，可以保留该会话的 Cookie，避免重复启动浏览器：

```java
try (var session = browser.openPrivate(BrowserProfileId.DEFAULT, "about:blank", BrowserProxyMode.DIRECT)) {
    BrowserResource first = session.loadResource(firstUrl, "image/");
    BrowserResource second = session.loadResource(secondUrl, "image/");
}
```

该方法导航当前页面，让真实浏览器执行页面脚本，然后读取符合 MIME 前缀的成功响应体。应使用专用资源会话，避免替换用户正在操作的页面。它是阻塞 API，适合在虚拟线程中调用；不要阻塞 Vaadin UI 锁。

支持 HTTP(S)，返回实际文件字节而非截图；不会自动完成人工验证码，也不保证所有下载或挑战页面都支持。当前等待和体积限制以 [BrowserResourceLoader](../src/main/java/dev/w0fv1/vaadmin/remotebrowser/BrowserResourceLoader.java) 为准，失败抛出异常。同一会话的资源读取串行执行。库不缓存结果，缓存策略与向前端提供下载响应由宿主负责。

行为验证入口：[BrowserResourceTest](../src/test/java/dev/w0fv1/vaadmin/remotebrowser/BrowserResourceTest.java)、[BrowserSessionContractTest](../src/test/java/dev/w0fv1/vaadmin/remotebrowser/BrowserSessionContractTest.java)、[BrowserStreamLayoutTest](../src/test/java/dev/w0fv1/vaadmin/remotebrowser/BrowserStreamLayoutTest.java)。
