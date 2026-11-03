# com.openjiuwen.core.foundation.llm.ModelInvokeOptions

## 类 ModelInvokeOptions

```java
@Value
@Builder(toBuilder = true)
public class ModelInvokeOptions
```

描述一次 `Model.invoke(...)` 或 `Model.stream(...)` 的可选参数。使用 `ModelInvokeOptions.builder()` 创建实例；`toBuilder()` 可基于现有实例生成 builder。

## 字段与 getter

| 字段 | getter 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `tools` | `List<?> getTools()` | `null` | 本次调用可用的工具定义。 |
| `temperature` | `Float getTemperature()` | `null` | 覆盖默认 temperature。 |
| `topP` | `Float getTopP()` | `null` | 覆盖默认 top-p。 |
| `maxTokens` | `Integer getMaxTokens()` | `null` | 覆盖默认最大输出 token 数。 |
| `stop` | `String getStop()` | `null` | 覆盖默认停止序列。 |
| `model` | `String getModel()` | `null` | 覆盖默认模型名。 |
| `outputParser` | `BaseOutputParser getOutputParser()` | `null` | 本次调用的输出解析器。 |
| `timeout` | `Float getTimeout()` | `null` | 本次调用超时，单位秒。 |
| `retryListener` | `ModelRetryListener getRetryListener()` | `null` | OpenAI HTTP 重试事件监听器。 |
| `requestHeaders` | `Map<String, String> getRequestHeaders()` | 空 Map | 只用于本次调用的正式 transport headers。 |
| `extraFields` | `Map<String, Object> getExtraFields()` | 空 `LinkedHashMap` | provider 私有扩展参数；保持既有兼容语义。 |

## builder 与复制语义

相关公开方法的签名如下：

```java
public static ModelInvokeOptionsBuilder builder()
public ModelInvokeOptionsBuilder toBuilder()

public ModelInvokeOptionsBuilder requestHeaders(Map<String, String> requestHeaders)
public ModelInvokeOptions build()

public Map<String, String> getRequestHeaders()
```

- `requestHeaders(null)` 按空 Map 处理。builder 接收非空 Map 时立即复制，构造函数再次复制，`getRequestHeaders()` 每次也返回新的 `LinkedHashMap`；修改调用方 Map 或 getter 返回值都不会改变已构造的 options。
- `toBuilder()` 保留当前请求头内容，后续构建仍执行复制。`requestHeaders` 本身不会被加入普通模型 callback kwargs、模型业务参数或 tracer 参数。
- `ModelInvokeOptions.toString()`、builder 的 `toString()` 以及 `toBuilder().toString()` 都不输出正式 `requestHeaders`。该脱敏只针对正式字段；legacy 凭证不要放入会被应用自行打印的普通对象中，也不要记录完整 options 或 headers。

## 客户端契约

客户端收到非空 `requestHeaders` 后必须满足以下二选一契约：

1. 把 headers 作为当前调用的 transport 请求头发送。
2. 明确抛出“不支持请求级 headers”的异常。

内置 `BaseModelClient` 默认选择明确失败；OpenAI / OpenRouter 兼容客户端支持发送。自定义 `Model.ModelClient` 不得静默忽略，也不得把正式 headers 当成模型请求体字段。

OpenAI 兼容客户端中，正式 `requestHeaders` 的同名 header 优先于 legacy `extraFields.custom_headers` / `customHeaders`，legacy 路径再优先于静态 `apiKey`。`Authorization` value 必须是完整值，不自动添加 `Bearer`。正式 header 的 name/value、JDK 保护名称或 `Authorization` 校验失败时，本次调用直接失败，不回退到较低优先级凭证。
