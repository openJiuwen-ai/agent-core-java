# 响应式 Agent 接口参考示例

演示如何使用 agent-core 的 Reactor API，并在应用侧用 Spring WebFlux 暴露 JSON / SSE HTTP 接口。

## 定位

- agent-core 不提供 Spring Starter、自动装配或内置 HTTP 端点
- 本目录只提供示例代码，不包含 `pom.xml`，也不是 Maven 子工程
- Spring WebFlux 依赖由业务应用自行提供

## 文件

- `ReactiveAgentExample.java`: 命令行示例，演示 `ReactiveAdapters` 与 Runner 响应式接口
- `SimpleReactiveAgentExample.java`: 无大模型依赖的示例 Agent
- `WebFluxAgentExample.java`: Spring Boot 启动类，注册 `demo-agent`
- `WebFluxControllerExample.java`: 暴露 `/api/agent/invoke` 和 `/api/agent/stream`
- `PRESENTATION.md`: PR 演示说明

## 应用侧依赖

在你的 Spring Boot WebFlux 应用中引入：

```xml
<dependency>
  <groupId>com.openjiuwen</groupId>
  <artifactId>agent-core-java</artifactId>
  <version>0.1.12</version>
</dependency>

<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>
```

如果本地 Maven 仓库还没有当前版本，先在 agent-core-java 根目录执行：

```bash
mvn -DskipTests install
```

## 使用方式

复制示例类到你的 Spring Boot WebFlux 应用工程：

```bash
cp examples/reactive_agent/*.java <your-app>/src/main/java/examples/reactive_agent/
```

如果修改包名，需要同步修改 Java 文件顶部的 `package`。如果保留 `examples.reactive_agent` 包名，直接启动
`WebFluxAgentExample` 即可。

启动后验证：

```bash
curl -s -X POST http://localhost:8080/api/agent/invoke \
  -H 'Content-Type: application/json' \
  -d '{"agent_id":"demo-agent","inputs":{"query":"你好"}}'

curl -s -N -X POST http://localhost:8080/api/agent/stream \
  -H 'Content-Type: application/json' \
  -d '{"agent_id":"demo-agent","inputs":{"query":"你好"}}'
```

## 注意事项

- 不要在本目录新增真实 `pom.xml`，也不要加入父工程 `<modules>`
- `WebFluxControllerExample` 只是应用侧参考实现，生产使用需补充鉴权、限流、审计和错误信封
- 端口占用时可用 `--server.port=8081` 调整业务应用端口