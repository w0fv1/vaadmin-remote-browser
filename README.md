# Vaadmin Remote Browser

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

将真实 Chrome / Edge 的页面嵌入 Spring Boot + Vaadin 应用，通过 DevTools 控制浏览器，通过 WebSocket 传递画面与输入。支持 Linux headless、持久化与私有会话、直连代理策略、纯页面交互和浏览器资源读取。

这是独立的 Java 库与 Spring Boot 自动配置模块，不依赖 Nfirco、Userver 或其他平台业务。仓库包含最小 [示例应用](sample)。

## 快速开始

使用 JDK 25 构建和运行示例，准备 Chrome / Chromium / Edge 可执行文件。依赖版本以 [gradle.properties](gradle.properties) 为准。

```sh
git clone https://github.com/w0fv1/vaadmin-remote-browser.git
cd vaadmin-remote-browser
./gradlew :sample:bootRun
```

Windows 使用 `gradlew.bat`。打开 `http://localhost:8080/?url=https://example.com/`。无桌面环境通过 `BROWSER_REMOTE_MODE=headless` 启动，浏览器路径可用 `BROWSER_REMOTE_EXECUTABLE` 指定。

在 Spring Boot + Vaadin 应用中注入 `RemoteBrowser`：

```java
RemoteBrowserViewer viewer = new RemoteBrowserViewer(browser);
viewer.setInitialUrl("https://example.com/");
add(viewer);
```

仅获取图片内容：

```java
BrowserResource image = browser.loadResource(url, "image/", BrowserProxyMode.DIRECT);
byte[] bytes = image.content();
```

## 接入与分发

库具有独立版本和 Maven publication，坐标为 `dev.w0fv1:vaadmin-remote-browser`，版本以 [构建文件](build.gradle.kts) 为准。这里不假定该版本已发布到 Maven Central。

本地安装：

```sh
./gradlew publishToMavenLocal
```

宿主添加 `mavenLocal()`，按构建文件中的版本引入依赖；也可以作为 Gradle 子项目引用：

```kotlin
implementation(project(":vaadmin-remote-browser"))
```

自动配置与前端资源随 JAR 提供。浏览器二进制由宿主提供，具体见运行文档。

## 文档

- [API 与调用示例](docs/api.md)：会话、代理、onlyPage、脚本执行、图片内容。
- [运行与配置](docs/runtime.md)：Linux、环境画像、配置与实现索引。
- [贡献与验证](CONTRIBUTING.md)：本地构建、真实浏览器测试和 PR。
- [安全边界与问题报告](SECURITY.md)。

## 许可证

[MIT License](LICENSE)。浏览器及其他第三方依赖遵循各自许可证，不属于本仓库的 MIT 授权范围。
