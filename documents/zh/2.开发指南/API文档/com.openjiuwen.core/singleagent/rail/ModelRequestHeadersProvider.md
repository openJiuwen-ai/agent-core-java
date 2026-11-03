# com.openjiuwen.core.singleagent.rail.ModelRequestHeadersProvider

## 接口 ModelRequestHeadersProvider

```java
@FunctionalInterface
public interface ModelRequestHeadersProvider {
    CompletionStage<Map<String, String>> provide(AgentCallbackContext context);
}
```

在一次 ReActAgent 模型调用的 `BEFORE_MODEL_CALL` 阶段，根据当前 `AgentCallbackContext` 解析请求级 transport headers。同步实现可以返回 `CompletableFuture.completedFuture(...)`；异步实现可以返回尚未完成的 `CompletionStage`，`ModelRequestHeadersRail` 会在模型调用前等待结果。

## 返回约束

- 返回 Map 的 value 是完整 header value。例如 `Authorization` 可为 `Bearer ...`、`Basic ...` 或网关自定义方案，SDK 不添加认证前缀。
- Provider 不应使用全局可变 token；应从 `AgentCallbackContext`、session 或使用方注入的凭证服务解析当前调用的数据。
- Provider 不得返回 `null` stage、异常 stage、`null` Map、空 Map 或空白 `Authorization`。这些结果都会由 Rail 转换为不包含敏感值的 `AbortError`，终止本次模型调用。
- 如需备用凭证，Provider 必须在自己的解析逻辑中显式返回备用 header；SDK 不会因解析失败自动回退到共享静态凭证。
- Rail 会复制 Provider 返回的 Map，不持有原始引用。Provider 仍不应记录或复用包含凭证明文的可变 Map。

Agent 级 retry 的每个 attempt 都会再次调用 `provide(...)`；单个 attempt 进入客户端 HTTP retry 后则复用已形成的请求头快照。
