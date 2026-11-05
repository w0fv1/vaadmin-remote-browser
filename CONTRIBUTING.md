# Contributing

问题与建议通过 [GitHub Issues](https://github.com/w0fv1/vaadmin-remote-browser/issues) 提交。报告问题时附上操作系统、JDK、浏览器版本、相关配置和最小复现，移除 Cookie、令牌与个人数据。安全问题见 [SECURITY.md](SECURITY.md)。

## 开发环境

使用 JDK 25 和仓库自带 Gradle Wrapper，首次构建需要下载固定版本浏览器，Linux 打包另需 Podman。示例入口见 [sample](sample)，依赖版本见 [gradle.properties](gradle.properties)。

```sh
./gradlew :test --tests '*BrowserResourceTest'
./gradlew :test
./gradlew headedTest
./gradlew :sample:bootRun
./gradlew publishToMavenLocal
```

`test` 排除需要桌面的 headed 测试；`headedTest` 需要图形环境。优先运行与改动相关的测试。`BrowserResourceTest` 默认使用回环 HTTP 服务和真实无头浏览器；设置 `BROWSER_RESOURCE_LIVE=true` 可额外运行外部站点资源测试，该测试依赖网络和站点当前状态。

## Pull request

从最新 `main` 创建分支。为行为变更增加定向测试，浏览器交互必须用真实浏览器验证。公共接口的使用说明统一维护在 [API 文档](docs/api.md)，配置与内部结构通过 [运行文档](docs/runtime.md) 索引源码，避免复制实现规则。

提交 PR 时说明用户可见行为、验证命令与结果。不要提交本机浏览器数据、下载缓存、密钥或构建产物。提交的贡献按仓库 [MIT License](LICENSE) 分发。

## 发布

Maven 坐标、版本、POM、源码与 Javadoc 产物配置以 [build.gradle.kts](build.gradle.kts) 为准。维护者验证独立构建后再发布；当前仓库未配置远程 Maven 发布仓库，不应把本地 publication 宣称为已上线的 Central 版本。
