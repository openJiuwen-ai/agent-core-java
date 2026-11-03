# com.openjiuwen.core.singleagent.rail.ModelRequestHeadersRail

## 类 ModelRequestHeadersRail

```java
public class ModelRequestHeadersRail extends AgentRail
```

在每次模型调用前执行 `ModelRequestHeadersProvider`，校验结果并合并到当前 `ModelCallInputs`。

## 构造方法

```java
public ModelRequestHeadersRail(ModelRequestHeadersProvider provider)
```

`provider` 不能为 `null`；传 `null` 时构造方法抛出 `NullPointerException`。

## 方法

```java
@Override
public CompletionStage<Void> beforeModelCall(AgentCallbackContext context)
```

执行顺序如下：

1. 确认 context 的 inputs 是 `ModelCallInputs`。
2. 调用 Provider；等待其 `CompletionStage<Map<String, String>>` 完成。
3. 复制返回 Map，确认 Map 非空，并拒绝大小写任意形式的空白 `Authorization`。
4. 调用 `ModelCallInputs.mergeRequestHeaders(...)` 合并，然后返回已完成的 `CompletionStage<Void>`。

Provider 同步抛出异常、返回 `null` stage、stage 异常完成、返回 `null` / 空 Map，以及复制、校验或合并失败时，方法同步抛出 `AbortError`。错误 reason 只描述失败阶段，不保留原异常 cause，也不包含 header value；这保证普通 callback 异常会被吞掉的运行框架中仍保持 fail-closed。

普通 header 的 name/value 与 JDK 保护名称由实际 ModelClient 在 transport 边界继续校验。Rail 只负责请求级解析、基本 Authorization 校验和合并，不添加 `Bearer`、不读取静态 `apiKey`，也不自动选择备用凭证。

## 合并、顺序与清理

- 多个 Rail 写入不同名称时正常合并；同名 header 按大小写不敏感方式匹配，后执行 Rail 覆盖先执行 Rail。Rail 按 priority 数值从大到小执行；相同 priority 时保持注册顺序，先注册的先执行。因此覆盖 Rail 应设置更低的 priority，或者在相同 priority 下后注册。
- `ReActAgent` 在调用 Model 前通过 `consumeRequestHeaders()` 将 headers 移入 `ModelInvokeOptions` 并清空 inputs。
- `Rails.run(...)` 在 before-model 异常或取消时清理，并在 finally 中于 after callback 前再次确保清理。Agent retry 会重新执行本 Rail，不复用上一 attempt 的结果。
- 每次模型调用使用独立 `ModelCallInputs` 和复制后的 Map，不修改共享 `ModelClientConfig` 或 `Model`，从而隔离并发调用。
