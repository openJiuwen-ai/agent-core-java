# 响应式接口（Reactor）

agent-core-java 提供了一套基于 Project Reactor 的响应式 API，与原有同步接口并存。在 Spring WebFlux 服务、SSE 流式响应、高并发长连接等场景下，你可以直接基于 `Mono` / `Flux` 接入，无需自行将同步调用或 Iterator 适配为响应式。

## 使用说明

1. 响应式方法与同步方法**并存**，使用 `invokeAsync` / `streamAsync` 不影响现有 `invoke` / `stream`。
2. 框架内部统一使用 `Schedulers.boundedElastic()` 执行阻塞操作，**不会阻塞 Netty event loop**，订阅者无需额外指定调度器。
3. Flux 取消时，框架会对 `AutoCloseable` Iterator 调用 `close()`；`Runner` 流式入口还会执行 `postRun` 清理，避免泄漏网络资源和会话资源。

## 核心 API 一览

| 类 | 响应式方法 | 返回类型 | 对应同步方法 |
|---|---|---|---|
| `BaseAgent`（及其子类） | `invokeAsync(inputs, session)` | `Mono<Object>` | `invoke` |
| `BaseAgent`（及其子类） | `streamAsync(inputs, session, streamModes)` | `Flux<Object>` | `stream` |
| `Model` | `invokeAsync(messages, ...)` | `Mono<AssistantMessage>` | `invoke` |
| `Model` | `streamAsync(messages, ...)` | `Flux<AssistantMessageChunk>` | `stream` |
| `Runner`（静态） | `runAgentAsync(agent, inputs, session, ctx, envs)` | `Mono<Object>` | `runAgent` |
| `Runner`（静态） | `runAgentStreamingAsync(agent, inputs, session, ctx, modes, envs)` | `Flux<Object>` | `runAgentStreaming` |
| `RemoteClient` | `invokeAsync(inputs, timeoutSeconds)` | `Mono<Object>` | `invoke` |
| `RemoteClient` | `streamAsync(inputs, timeoutSeconds)` | `Flux<Object>` | `stream` |
| `A2AClient` | `invokeAsync(inputs, timeoutSeconds)` | `Mono<AgentResult>` | `invoke` |
| `A2AClient` | `streamAsync(inputs, timeoutSeconds)` | `Flux<Object>` | `stream` |
| `LLMCall` | `invokeAsync(inputs, ...)` | `Mono<AssistantMessage>` | `invoke` |
| `LLMCall` | `streamAsync(inputs, ...)` | `Flux<AssistantMessageChunk>` | `stream` |

> `WorkflowAgent` 继承自 `BaseAgent`，自动具备上述两个方法，无需额外适配。

## 使用示例

### Agent 单次调用（Mono）

```java
ReActAgent agent = new ReActAgent(config);

Mono<Object> result = agent.invokeAsync(Map.of("query", "你好"), null);

// 非阻塞订阅
result.subscribe(
    output -> System.out.println("结果: " + output),
    err    -> System.err.println("异常: " + err.getMessage())
);

// 在 WebFlux controller 中直接返回，Spring 框架负责订阅
@PostMapping("/invoke")
public Mono<String> invoke(@RequestBody Map<String, Object> body) {
    return agent.invokeAsync(body, null).map(Object::toString);
}
```

### Agent 流式调用（Flux）

```java
// SSE 场景：每个 chunk 包装为 ServerSentEvent
@PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ServerSentEvent<Object>> stream(@RequestBody Map<String, Object> body) {
    return agent.streamAsync(body, null, List.of(StreamMode.OUTPUT))
        .map(chunk -> ServerSentEvent.<Object>builder()
            .event("chunk").data(chunk).build());
}
```

### Model 流式调用

```java
Model model = Model.get("your-model-id");

model.streamAsync(
        /* messages */        "你好",
        /* tools */           null,
        /* temperature */     null,
        /* topP */            null,
        /* modelOverride */   null,
        /* maxTokens */       null,
        /* stop */            null,
        /* outputParser */    null,
        /* timeout */         null,
        /* extraParams */     null)
    .subscribe(chunk -> System.out.print(chunk.getContent()));
```

### Runner 静态入口

```java
// 单次
Mono<Object> result = Runner.runAgentAsync(agent, inputs, null, null, null);

// 流式
Flux<Object> stream = Runner.runAgentStreamingAsync(
    agent, inputs, null, null, List.of(StreamMode.OUTPUT), null);
```

### A2A / RemoteClient 调用

```java
A2AClient client = new A2AClient("http://remote-agent-host:8080");

// 单次
client.invokeAsync(Map.of("query", "hello"), 30.0)
    .subscribe(result -> System.out.println(result.getOutput()));

// 流式
client.streamAsync(Map.of("query", "stream"), 30.0)
    .subscribe(chunk -> System.out.println(chunk));
```

## 自定义响应式包装（ReactiveAdapters）

如果你需要将自己的阻塞调用或 Iterator 接入响应式链，可以使用 `ReactiveAdapters` 工具类（`com.openjiuwen.core.common.reactive.ReactiveAdapters`）。它是框架所有 `xxxAsync` 方法的底层实现，可以直接复用。

| 方法 | 用途 |
|---|---|
| `fromCallable(callable)` | 将阻塞的单次调用包装为 `Mono` |
| `fromRunnable(runnable)` | 将无返回值的阻塞操作包装为 `Mono<Void>` |
| `fromIterator(iterator, cleanup)` | 将阻塞 Iterator 包装为 `Flux`，支持自定义清理回调 |
| `fromCallableIterator(source, cleanup)` | Iterator 本身也需要延迟构建时使用，prep 工作同样跑在 boundedElastic 上 |
| `fromAutoCloseableIterator(source)` | SSE / 流式 HTTP 场景专用：取消时自动调用 `iterator.close()` 关闭底层连接 |
| `fromAutoCloseableIterator(source, cleanup)` | 在关闭 `AutoCloseable` Iterator 后额外执行清理回调，适合同时释放连接和会话资源 |

**包装自定义阻塞调用：**

```java
Mono<String> result = ReactiveAdapters.fromCallable(() -> myBlockingService.call());
```

**包装返回 Iterator 的流式接口（SSE 场景）：**

```java
// iterator.close() 在 Flux 取消时自动触发，关闭底层 socket
Flux<String> stream = ReactiveAdapters.fromAutoCloseableIterator(
    () -> myStreamingClient.openStream());
```

## Spring WebFlux 接入边界

agent-core-java 不提供 Spring Starter、自动装配类或内置 HTTP 端点。服务化接口属于应用侧关切：如果你的服务基于 Spring Boot 3 + WebFlux，需要在自己的应用工程中引入 Spring WebFlux，并基于 `Runner.runAgentAsync` / `Runner.runAgentStreamingAsync` 自行编写 Controller。

仓库提供了一个参考 demo：`examples/reactive_agent`。该目录不包含 `pom.xml`，不在父 pom `<modules>` 内，不作为独立 Maven 子工程，也不随 agent-core 框架发布。

```bash
# 将示例类复制到你的 Spring Boot WebFlux 应用工程中
# 由业务工程提供 spring-boot-starter-webflux 依赖并负责启动服务
cp examples/reactive_agent/*.java <your-app>/src/main/java/<your/package>/
```

完整本地运行方式见 `examples/reactive_agent/README.md`。

demo 暴露的端点仅用于展示应用侧接入方式：

| 端点 | 路径 | 说明 |
|---|---|---|
| 单次调用 | `POST /api/agent/invoke` | 应用侧 Controller 将 `Mono` 映射为 JSON |
| 流式输出 | `POST /api/agent/stream` | 应用侧 Controller 将 `Flux` 映射为 SSE |

如果你要在自己的服务里接入 WebFlux，建议直接复制 demo 中的 Controller 写法，并按业务需要补充鉴权、限流、审计和错误信封。不要期待 agent-core 提供可配置的 HTTP 服务层。

## 注意事项

- **不要在响应式链中直接调用同步阻塞方法**（如 `agent.invoke(...)`），否则会阻塞 event loop 线程。如有需要，使用 `ReactiveAdapters.fromCallable(() -> agent.invoke(...))` 包装后再接入响应式链。
- `streamAsync` 返回的 `Flux` 是冷流，只有订阅后才会开始执行 Agent，多次订阅会多次执行。
- 使用 `ReactiveAdapters` 自行包装流式调用时，取消订阅（`dispose()`）不会自动释放资源，除非你的 iterator 实现了 `AutoCloseable`（使用 `fromAutoCloseableIterator`）或显式传入了 cleanup 函数（`fromIterator` / `fromCallableIterator` / `fromAutoCloseableIterator` 的第二个参数）。`invokeAsync` 返回的是 `Mono`，无此问题。
