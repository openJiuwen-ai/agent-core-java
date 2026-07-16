# 安全护栏 Guardrail

Java 版安全护栏当前公开在 `com.openjiuwen.core.security.guardrail` 子包中。它不是一套独立的“安全平台”，而是一层建立在 `CallbackFramework` 之上的事件检测和阻断机制：某个运行时事件发生时，guardrail 读取事件数据，交给 `GuardrailBackend` 分析，再按结果决定放行还是抛出 `GuardrailError`。

这里讨论的范围是 Java 当前真正公开的能力：`BaseGuardrail`、`GuardrailBackend`、`RiskAssessment`、`GuardrailResult`、`RiskLevel` 和 `UserInputGuardrail`。仓库里没有公开的 `PromptInjectionGuardrail` 一类内置策略，因此使用时应以当前已落地的策略类型为准。

## 能力定位

- Guardrail 的核心职责是在回调事件点上做风险检测和阻断。
- 风险分析逻辑由 `GuardrailBackend` 提供，guardrail 本身只负责注册、调度和抛错。
- 当前默认现成实现是 `UserInputGuardrail`，默认监听 `user_input` 事件。
- 如果你想保护其他事件，同样可以基于 `BaseGuardrail` 自定义事件列表和检测逻辑。

## 核心类型

| 类型 | 作用 | 何时使用 |
| --- | --- | --- |
| `BaseGuardrail` | 护栏抽象基类，负责事件注册、回调封装和风险阻断 | 自定义 guardrail 或理解通用注册逻辑时 |
| `GuardrailBackend` | 风险分析函数式接口 | 需要实现自己的检测逻辑时 |
| `RiskAssessment` | backend 的原始分析结果 | 返回 `hasRisk`、`riskLevel`、`riskType` 等信息时 |
| `GuardrailResult` | guardrail 的最终判定结果 | 需要显式返回放行或阻断结果时 |
| `RiskLevel` | 风险级别枚举 | 标注风险严重程度时 |
| `UserInputGuardrail` | 默认用户输入护栏 | 需要在 `user_input` 事件上做基础防护时 |

## 接入步骤

### 1. 先实现一个 `GuardrailBackend`

Java 当前的推荐起点不是“挑一个现成策略类”，而是先写 `GuardrailBackend`：

```java
GuardrailBackend backend = data -> {
    String text = String.valueOf(data.getOrDefault("text", ""));
    boolean risky = text.contains("ignore previous instructions");

    return RiskAssessment.builder()
            .hasRisk(risky)
            .riskLevel(risky ? RiskLevel.HIGH : RiskLevel.SAFE)
            .riskType(risky ? "prompt_injection" : null)
            .details(Map.of("matched", risky))
            .build();
};
```

`GuardrailBackend` 是 `@FunctionalInterface`，因此用 lambda 就能完成最小实现。输入数据至少会包含：

- `event`
- `args`
- 触发方透传进来的关键字段

### 2. 选择现成 guardrail，或基于 `BaseGuardrail` 自定义

如果你的入口就是用户文本，先用 `UserInputGuardrail`：

```java
UserInputGuardrail guardrail = new UserInputGuardrail(backend, null, true);
```

它的默认行为有三个值得直接记住：

- 默认监听事件是 `user_input`
- `kwargs["text"]` 不存在或为空字符串时直接放行
- `backend == null` 时也直接放行，不会抛 `IllegalStateException`

如果你需要监听的不是 `user_input`，可以：

- 调 `withEvents(...)` 覆盖事件列表
- 或继承 `BaseGuardrail` 自己实现 `defaultEvents()`，必要时覆写 `detect(...)`

### 3. 把 guardrail 注册到回调框架

当前接入点就是 `CallbackFramework`。如果你已经在用全局 Runner，可以直接挂到：

```java
guardrail.register(Runner.callbackFramework());
```

也可以注册到你自己持有的 `CallbackFramework` 实例。`BaseGuardrail.register(...)` 会为每个监听事件做两件事：

1. 先加一个 `HookType.ERROR` hook，确保异常能重新抛出
2. 再注册真正的检测回调

这意味着 guardrail 不只是“给你一个判断结果”，而是真的参与运行时控制流。

### 4. 让不安全结果变成统一阻断

`BaseGuardrail.detect()` 的默认流程是：

1. 把 `event`、`args` 和 `kwargs` 整理成分析输入
2. 调 `GuardrailBackend.analyze(...)`
3. `hasRisk = false` 或返回 `null` 时生成 `GuardrailResult.pass(...)`
4. 有风险时生成 `GuardrailResult.block(...)`
5. 注册回调层再把它转成 `GuardrailError(StatusCode.GUARDRAIL_BLOCKED, params)`

也就是说，backend 负责“分析”，guardrail 负责“阻断”。如果你想要统一的错误码和事件参数，应该让这条链路保持原样，而不是在 backend 里直接抛自己的异常。

### 5. 用完后显式注销

```java
guardrail.unregister();
```

`unregister()` 会从最近一次注册的 `CallbackFramework` 中移除先前登记的回调。对测试场景、临时工具链和多次重建 Runner 的流程来说，这一步最好显式做掉。

## 示例入口

Java 当前没有单独的 `examples/guardrail` 目录，因此这里的“最短示例入口”就是公开源码和回调框架入口本身：

- 护栏抽象入口：`../../../../src/main/java/com/openjiuwen/core/security/guardrail/BaseGuardrail.java`
- 默认用户输入护栏：`../../../../src/main/java/com/openjiuwen/core/security/guardrail/UserInputGuardrail.java`
- 全局回调框架入口：`../../../../src/main/java/com/openjiuwen/core/runner/Runner.java`
- 回调框架包说明：`../API文档/com.openjiuwen.core/runner/callback.README.md`

如果你要自己补一个最小可运行示例，推荐就从 `UserInputGuardrail + Runner.callbackFramework()` 开始，而不是先尝试寻找仓库里并不存在的内置策略 demo。

## 当前实现边界

- Java 当前公开包的主线是你自己实现 `GuardrailBackend`；默认现成 guardrail 主要是 `UserInputGuardrail`。
- 默认事件当前是 `user_input`；LLM / tool 输出护栏和多模式 detector 并未作为内置能力公开。
- Java 护栏明显依赖 `CallbackFramework` 这条事件链路，注册和阻断语义都以 `BaseGuardrail.register(...)` 的当前实现为准。
- 这里以 `security.guardrail` 子包当前已公开能力为准，不延伸到仓库外或未实现的策略类型。

## 参考入口

- [API 文档：guardrail 根包](../API文档/com.openjiuwen.core/security/guardrail.README.md)
- [API 文档：BaseGuardrail](../API文档/com.openjiuwen.core/security/guardrail/BaseGuardrail.md)
- [API 文档：UserInputGuardrail](../API文档/com.openjiuwen.core/security/guardrail/UserInputGuardrail.md)
- [API 文档：GuardrailBackend](../API文档/com.openjiuwen.core/security/guardrail/GuardrailBackend.md)
- [API 文档：RiskAssessment](../API文档/com.openjiuwen.core/security/guardrail/RiskAssessment.md)
- [API 文档：GuardrailResult](../API文档/com.openjiuwen.core/security/guardrail/GuardrailResult.md)
