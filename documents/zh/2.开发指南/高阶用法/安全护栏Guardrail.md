# 安全护栏 Guardrail

AI Agent 会调用模型、工具、记忆和外部数据源，攻击面不再只来自用户输入。Python 版文档把 Guardrail 定位为一套事件驱动的安全检测框架，用来在 LLM 调用输入、工具调用输出等关键节点检测提示词注入、越狱、敏感数据泄露等风险。Java 0.1.14 也应按这个主线理解，只是入口名称要以当前 Java 源码为准。

Java 当前实现位于 `com.openjiuwen.core.security.guardrail`，核心是：

- `BaseGuardrail` 负责监听事件、注册回调、调用检测后端和抛出阻断异常。
- `GuardrailBackend` 是抽象检测后端，通过 `analyze(GuardrailContext ctx)` 返回 `RiskAssessment`。
- `PromptInjectionGuardrail` 是当前已经落地的内置护栏，默认检测 LLM 输入和工具输出。
- `RiskLevel.CRITICAL` 会转换为 `AbortError`；其他风险级别会转换为 `GuardrailError`。

## 核心概念

| 概念 | Java 类型 | 说明 |
| --- | --- | --- |
| Guardrail | `BaseGuardrail`、`PromptInjectionGuardrail` | 监听事件并触发检测 |
| Backend | `GuardrailBackend` | 实现具体检测逻辑 |
| Context | `GuardrailContext` | 封装待检测内容、内容类型、事件名和元数据 |
| Assessment | `RiskAssessment` | 后端输出，包含是否有风险、风险等级、类型、置信度和详情 |
| Result | `GuardrailResult` | 护栏最终判定，安全则放行，不安全则阻断 |
| RiskLevel | `RiskLevel` | `SAFE`、`LOW`、`MEDIUM`、`HIGH`、`CRITICAL` |

## 事件入口

`PromptInjectionGuardrail` 默认监听两个事件：

| 默认事件 | 来源 | 检测内容 |
| --- | --- | --- |
| `LLMCallEvents.LLM_INVOKE_INPUT` | 模型调用前 | `messages` 中最后一条消息的 `content`，或完整 messages |
| `ToolCallEvents.TOOL_INVOKE_OUTPUT` | 工具调用后 | `result` 字段转成的文本 |

其他事件也可以通过构造函数传入 `events` 自定义。自定义事件会被封装成 `GuardrailContext` 的 `RAW` 内容，由后端自己解释。

## 实现检测后端

Python 版文档强调“后端负责检测逻辑，护栏负责事件接入”。Java 版也是这个分层，只是 `GuardrailBackend` 不是函数式接口，而是抽象类：

```java
GuardrailBackend backend = new GuardrailBackend() {
    @Override
    public RiskAssessment analyze(GuardrailContext ctx) {
        String text = ctx.getText().orElse("");
        boolean risky = text.toLowerCase().contains("ignore previous instructions");
        return new RiskAssessment(
                risky,
                risky ? RiskLevel.HIGH : RiskLevel.SAFE,
                risky ? "prompt_injection" : null,
                1.0d,
                Map.of("matched", risky)
        );
    }
};
```

后端只返回风险分析结果，不负责向 Runner 注册，也不负责抛异常。注册和阻断统一由 `BaseGuardrail` 处理。

## 使用内置提示词注入护栏

最小用法如下：

```java
PromptInjectionGuardrail guardrail = new PromptInjectionGuardrail();
guardrail.register(Runner.getCallbackFramework());
```

默认模式是 `rules`，也就是 `RuleBasedPromptInjectionBackend`。默认规则覆盖常见提示词注入片段，例如：

- `ignore.*previous.*instructions`
- `disregard.*prior.*commands`
- `system.*prompt`
- `you.*are.*now`
- `act.*as`
- `forget.*everything`

如果命中规则，后端返回 `prompt_injection` 风险，默认风险级别是 `RiskLevel.HIGH`。

## 配置检测模式

`PromptInjectionGuardrailConfig` 对应 Python 版的内置护栏配置，Java 当前支持三种模式：

| mode | 后端 | 必要配置 | 说明 |
| --- | --- | --- | --- |
| `rules` | `RuleBasedPromptInjectionBackend` | 无 | 默认模式，可传 `customPatterns` 和 `riskLevel` |
| `api` | `APIModelBackend` | `apiUrl`，以及 `modelType` 或 `parser` | 通过远程模型 API 检测文本 |
| `local` | `LocalModelBackend` | `modelPath`，以及 `modelType` 或 `parser` | 预留本地模型入口，当前 Java 运行时尚未真正接入本地推理 |

`modelType` 当前可选 `bert` 或 `qwen`。未显式传 `parser` 时：

- `bert` 使用 `BertBinaryParser`；
- `qwen` 使用 `QwenGuardParser`。

示例：

```java
PromptInjectionGuardrailConfig config = new PromptInjectionGuardrailConfig();
config.setMode("rules");
config.setCustomPatterns(List.of("泄露.*系统提示", "绕过.*安全策略"));
config.setRiskLevel(RiskLevel.HIGH);

PromptInjectionGuardrail guardrail = new PromptInjectionGuardrail(config);
guardrail.register(Runner.getCallbackFramework());
```

API 模式示例：

```java
PromptInjectionGuardrailConfig config = new PromptInjectionGuardrailConfig();
config.setMode("api");
config.setApiUrl("https://example.com/guardrail");
config.setApiKey(System.getenv("GUARDRAIL_API_KEY"));
config.setModelType("qwen");

PromptInjectionGuardrail guardrail = new PromptInjectionGuardrail(config);
guardrail.register(Runner.getCallbackFramework());
```

## 自定义事件和后端

如果你要检测自定义业务事件，可以直接传事件列表和后端：

```java
PromptInjectionGuardrail guardrail = new PromptInjectionGuardrail(
        List.of("order.review.before_submit"),
        backend,
        true
);
guardrail.register(Runner.getCallbackFramework());
```

自定义事件的 `kwargs` 会进入 `GuardrailContext`，后端可以按业务字段提取文本或结构化数据。

## 阻断行为

当后端返回风险：

1. `BaseGuardrail.detect(...)` 把 `RiskAssessment` 转成 `GuardrailResult`。
2. 注册到回调框架的检测回调检查 `result.isSafe()`。
3. 安全则返回 `null`，不干扰原事件。
4. `RiskLevel.CRITICAL` 抛 `AbortError`，用于终止当前回调链。
5. 其他风险级别抛 `GuardrailError(StatusCode.GUARDRAIL_BLOCKED, ...)`。

因此，业务侧不需要在每个工具或模型调用点手工判断风险，只要相关事件会触发 Runner 回调框架，Guardrail 就可以统一接入。

## 当前 Java 边界

- 当前源码没有 `UserInputGuardrail.java`；旧 API 文档里引用该类型是过期内容。
- `GuardrailBackend` 当前是抽象类，不是 `@FunctionalInterface`。
- `PromptInjectionGuardrail` 已经存在，不能再写成“Java 没有内置提示词注入护栏”。
- `LocalModelBackend` 保留本地模型接口，但 `loadModel()` 和 `inference(...)` 当前会抛 `UnsupportedOperationException`，需要应用侧接入具体推理运行时。
- `LLMPromptInjectionBackend` 当前保留 LLM 检测形态，但实际分析会回退到规则后端。
- API 模式会发 HTTP POST，payload 为 `{ "text": ... }`，远端响应由配置的 parser 解析。

## 参考入口

- [API 文档：guardrail 总览](../API文档/com.openjiuwen.core/security/guardrail.README.md)
- [API 文档：PromptInjectionGuardrail](../API文档/com.openjiuwen.core/security/guardrail/PromptInjectionGuardrail.md)
- [API 文档：PromptInjectionGuardrailConfig](../API文档/com.openjiuwen.core/security/guardrail/PromptInjectionGuardrailConfig.md)
- [API 文档：GuardrailBackend](../API文档/com.openjiuwen.core/security/guardrail/GuardrailBackend.md)
- [回调框架](异步回调框架.md)
- [执行器 Runner](执行器Runner.md)
