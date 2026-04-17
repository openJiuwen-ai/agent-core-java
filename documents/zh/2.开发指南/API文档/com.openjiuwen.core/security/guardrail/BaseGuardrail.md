# com.openjiuwen.core.security.guardrail.BaseGuardrail

## abstract class BaseGuardrail

```java
public abstract class BaseGuardrail
```

`BaseGuardrail` 是所有 guardrail 的抽象基类。它维护事件订阅列表、已注册回调和可选的 `GuardrailBackend`，并负责把不安全结果转换为回调框架可感知的 `GuardrailError`。

## 实例化说明

### `protected BaseGuardrail(GuardrailBackend backend, List<String> events, boolean enableLogging)`

供子类基于后端、监听事件和日志开关初始化护栏实例。

**参数**

- `backend`: 初始风险分析后端，可为 `null`。
- `events`: 监听事件列表；传 `null` 时改用 `defaultEvents()` 的返回值。
- `enableLogging`: 是否在注册成功后写入日志。

## 核心方法

| 方法 | 返回 | 说明 |
|---|---|---|
| `listenEvents()` | `List<String>` | 返回当前监听事件的副本，避免外部直接修改内部列表。 |
| `withEvents(List<String> events)` | `BaseGuardrail` | 清空旧事件并替换为新列表，支持链式配置。 |
| `setBackend(GuardrailBackend backend)` | `BaseGuardrail` | 更新当前使用的风险分析后端。 |
| `getBackend()` | `GuardrailBackend` | 返回当前绑定的后端实例。 |
| `isEnableLogging()` / `setEnableLogging(boolean enableLogging)` | `boolean` / `void` | 读取或更新注册日志开关。 |
| `detect(String eventName, Object[] args, Map<String, Object> kwargs)` | `GuardrailResult` | 默认检测入口；把事件数据整理为分析输入并委托给 `GuardrailBackend`。 |
| `register(CallbackFramework framework)` | `void` | 为每个监听事件注册 `HookType.ERROR` hook 和主检测回调。 |
| `unregister()` | `void` | 从最近一次注册的 `CallbackFramework` 中移除已登记回调。 |

## 扩展点

### `protected abstract List<String> defaultEvents()`

子类实现默认监听事件列表。仅在构造方法的 `events` 参数为 `null` 时使用。

## detect 处理流程

- 当 `backend` 为空且子类未覆盖 `detect()` 时，抛出 `IllegalStateException`。
- 分析输入至少包含 `event` 和 `args` 两个键；`kwargs` 中除 `_args` 以外的条目会原样并入分析数据。
- 当 `GuardrailBackend.analyze(...)` 返回 `null`，或 `RiskAssessment.hasRisk` 为 `false` 时，返回 `GuardrailResult.pass(...)`。
- 当检测到风险时，返回 `GuardrailResult.block(...)`；`modifiedData` 在默认实现中固定为 `null`。

## register 行为

- 每个监听事件都会先挂载一个 `HookType.ERROR` hook，用于把 `_error` 重写为 `_raise`，确保框架层重新抛出异常。
- 主回调会在 `GuardrailResult.isSafe` 为 `false` 时构造 `risk_type`、`risk_level`、`event` 等参数，并抛出 `GuardrailError(StatusCode.GUARDRAIL_BLOCKED, params)`。
- 非运行时异常会被包装为 `RuntimeException` 再抛出。
- 注册完成后，若 `enableLogging = true`，会通过 `Loggers.RUNNER` 记录成功日志。
