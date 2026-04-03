# com.openjiuwen.core.session.tracer.TracerWorkflowUtils

## 类 TracerWorkflowUtils

```java
public final class TracerWorkflowUtils
```

`TracerWorkflowUtils` 把 workflow 与节点执行过程转换为一系列 tracer 事件，并负责补齐 workflow / component 元数据。

## 方法

| 签名 | 说明 |
| --- | --- |
| `public static void traceWorkflowStart(BaseSession session, Object inputs)` | 上报 workflow 开始事件。 |
| `public static void traceComponentBegin(BaseSession session, java.util.List<String> sourceIds)` | 上报组件开始事件，并附带来源节点列表。 |
| `public static void traceComponentInputs(BaseSession session, Map<String, Object> inputs, boolean send)` | 上报组件输入。 |
| `public static void traceComponentStreamInput(BaseSession session, Object chunk, boolean send)` | 上报组件流式输入；`chunk` 为 `String` 时直接忽略。 |
| `public static void traceComponentOutputs(BaseSession session, Object outputs)` | 上报组件输出。 |
| `public static void traceComponentStreamOutput(BaseSession session, Object chunk)` | 上报组件流式输出；`chunk` 为 `String` 时直接忽略。 |
| `public static void traceWorkflowDone(BaseSession session, Object outputs)` | 上报 workflow 完成事件。 |
| `public static void traceComponentDone(BaseSession session)` | 上报组件完成事件；若当前处于 loop，还会弹出对应 workflow span。 |
| `public static void trace(BaseSession session, Map<String, Object> data)` | 上报通用 `on_invoke_data`。 |
| `public static void traceError(BaseSession session, Exception error)` | 上报错误；`error` 为 `null` 时抛出 `IllegalArgumentException`。 |
| `public static void traceComponentInteractiveInputs(BaseSession session, Object inputs, boolean send)` | 上报组件交互输入。 |
| `public static void registerWorkflowSpanManager(BaseSession session)` | 为当前执行作用域注册专用 workflow `SpanManager`。 |

## 说明

- workflow 元数据会尽量从 `WorkflowConfig.getCard()` 补齐 `workflow_version` 和 `workflow_name`。
- 组件元数据会在 `NodeSession` 上补齐 `component_id`、`component_type` 以及 loop 相关字段。
