# com.openjiuwen.core.singleagent.rail.ModelCallInputs

## 类 ModelCallInputs

```java
public class ModelCallInputs implements EventInputs
```

用于 `BEFORE_MODEL_CALL` / `AFTER_MODEL_CALL` 事件的输入载荷。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `messages` | `List<Object>` | `new ArrayList<>()` | 送入模型调用的消息列表。 |
| `tools` | `List<Object>` | `null` | 当前可供模型调用的工具描述列表。 |
| `requestHeaders` | `Map<String, String>` | 空 `LinkedHashMap` | 当前模型调用的 transport headers；字段和 getter 均标注 `@JsonIgnore`。 |
| `modelContext` | `ModelContext` | `null` | 当前模型上下文，JSON 属性名为 `model_context`。 |
| `response` | `Object` | `-` | 模型返回结果对象。 |

## 请求头方法

```java
@JsonIgnore
public Map<String, String> getRequestHeaders()

public void setRequestHeaders(Map<String, String> headers)
public void mergeRequestHeaders(Map<String, String> headers)
public Map<String, String> consumeRequestHeaders()
```

- `getRequestHeaders()` 返回副本，调用方修改返回 Map 不会改变内部状态。
- `setRequestHeaders(...)` 先清空现有 headers，再按 merge 规则写入；`null` 或空 Map 会留下空状态。
- `mergeRequestHeaders(...)` 保留不同名 header；同名判断不区分大小写，后写入值会删除并替换先前名称和值。输入 Map 的内容被复制到内部 Map，不保留原 Map 引用。
- `consumeRequestHeaders()` 返回当前内容的副本并立即清空内部 Map。ReActAgent 在构造 `ModelInvokeOptions` 时使用该方法，`Rails.run(...)` 也会在 before-model 异常、取消和 finally 路径于 exception / after callback 前清理，避免旧 headers 跨 retry attempt 保留。
- `requestHeaders` 不参与 Jackson 序列化。它只供 Rail 与当前模型调用传递 transport 数据，不应写入日志、tracer、模型请求体或可持久化上下文。

## 其他方法

`messages` 与 `tools` 的 setter 都复制传入列表；`messages` 传 `null` 时恢复为空列表，`tools` 传 `null` 时保持 `null`。`modelContext` 和 `response` 提供普通 getter / setter。

## 说明

- 相关测试：`ReActAgentTest`、`DataClassCoverageTest`、`RailDataClassesTest`、`ModelRequestHeadersRailTest`。
