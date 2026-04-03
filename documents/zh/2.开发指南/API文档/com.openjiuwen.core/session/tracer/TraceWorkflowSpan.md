# com.openjiuwen.core.session.tracer.TraceWorkflowSpan

## 类 TraceWorkflowSpan

```java
public class TraceWorkflowSpan extends Span
```

`TraceWorkflowSpan` 在基础 `Span` 上补充了 workflow / 组件元数据、交互输入以及流式输入输出。

## 主要属性

| 属性 | 说明 |
| --- | --- |
| `executionId` | 当前执行 ID；构造时默认等于 `traceId`。 |
| `sourceIds` | 触发当前组件的来源节点 ID 列表。 |
| `workflowId` / `workflowVersion` / `workflowName` | workflow 基本信息。 |
| `componentId` / `componentName` / `componentType` | 当前组件标识与类型信息。 |
| `loopNodeId` / `loopIndex` | loop 节点与循环索引。 |
| `llmInvokeData` | LLM 调用附加数据。 |
| `parentNodeId` | 父节点 ID。 |
| `interactiveInputs` | 交互输入快照。 |
| `streamInputs` / `streamOutputs` | 流式输入输出块集合。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public TraceWorkflowSpan()` | 创建空 workflow span。 |
| `public TraceWorkflowSpan(String traceId, String invokeId, String parentInvokeId, String parentNodeId)` | 使用 trace / 调用标识和父节点 ID 创建 workflow span。 |

## 主要方法

| 签名 | 说明 |
| --- | --- |
| `public void appendStreamOutput(Object chunk)` | 追加一个流式输出块。 |
| `public void appendStreamInput(Object chunk)` | 追加一个流式输入块。 |
| `public TraceWorkflowSpan snapshot()` | 生成当前 workflow span 的深拷贝快照。 |

## 说明

- 相关测试：`TracerTest`。
- 该类为上述属性提供标准 getter / setter。
- 内部字段更新同时兼容 `snake_case` 与 `camelCase` 键名。
