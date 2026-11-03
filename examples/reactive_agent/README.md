# 响应式 Agent 接口参考示例

演示如何使用 agent-core 的 Reactor API，并在应用侧用 Spring WebFlux 暴露 JSON / SSE HTTP 接口。

对齐文档：`documents/zh/2.开发指南/高阶用法/响应式接口（Reactor）.md`。

## 定位

- agent-core 不提供 Spring Starter、自动装配或内置 HTTP 端点
- 本目录只提供示例代码，不包含 `pom.xml`，也不是 Maven 子工程
- Spring WebFlux 依赖由业务应用自行提供

## 文件

| 文件 | 说明 |
|---|---|
| `ReactiveAgentExample.java` | 命令行示例：`ReactiveAdapters` + `BaseAgent.*Async` + `Runner.*Async`（无 Spring） |
| `SimpleReactiveAgentExample.java` | 无大模型依赖的示例 Agent |
| `WebFluxAgentExample.java` | Spring Boot 启动类，注册 `demo-agent` |
| `WebFluxControllerExample.java` | 暴露 `POST /api/agent/invoke` 与 `POST /api/agent/stream` |

## 场景一览（CLI）

运行 `ReactiveAgentExample` 会依次验证：

1. `ReactiveAdapters.fromCallable`
2. `ReactiveAdapters.fromRunnable`
3. `fromIterator` / `fromCallableIterator`
4. `fromAutoCloseableIterator`（取消时 `close()`）
5. `BaseAgent.invokeAsync` → `Mono`
6. `BaseAgent.streamAsync` → `Flux`
7. `Runner.runAgentAsync` → `Mono`
8. `Runner.runAgentStreamingAsync` → `Flux`

## 本地运行 CLI（推荐先验证）

在仓库根目录执行（PowerShell）：

```powershell
mvn -DskipTests compile
mvn dependency:build-classpath "-Dmdep.outputFile=target/reactive_agent.classpath"
New-Item -ItemType Directory -Force -Path target/reactive_agent_classes | Out-Null
javac -encoding UTF-8 `
  -cp "target/classes;$(Get-Content target/reactive_agent.classpath -Raw)" `
  -d target/reactive_agent_classes `
  examples/reactive_agent/SimpleReactiveAgentExample.java `
  examples/reactive_agent/ReactiveAgentExample.java
java -cp "target/classes;target/reactive_agent_classes;$(Get-Content target/reactive_agent.classpath -Raw)" `
  examples.reactive_agent.ReactiveAgentExample
```

## WebFlux HTTP 示例

应用侧依赖（复制到你的 Spring Boot WebFlux 工程）：

```xml
<dependency>
  <groupId>com.openjiuwen</groupId>
  <artifactId>agent-core-java</artifactId>
  <version><!-- 与本地安装版本一致 --></version>
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

复制示例类到业务工程：

```bash
cp examples/reactive_agent/*.java <your-app>/src/main/java/examples/reactive_agent/
```

启动 `WebFluxAgentExample` 后验证：

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
- CLI 示例不依赖真实 LLM；`Model` / `A2AClient` / `RemoteClient` 的 `*Async` 用法见文档与对应单元测试
