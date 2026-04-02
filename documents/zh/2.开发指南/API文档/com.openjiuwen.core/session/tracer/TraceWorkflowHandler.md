# com.openjiuwen.core.session.tracer.TraceWorkflowHandler

## 类 TraceWorkflowHandler

```java
public class TraceWorkflowHandler extends TraceBaseHandler
```

`TraceWorkflowHandler` 处理 workflow 与组件生命周期事件，把输入输出、交互、流式数据和异常写入 `TraceWorkflowSpan`。

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public TraceWorkflowHandler(Object owner, StreamWriterManager streamWriterManager, SpanManager spanManager)` | 使用 owner、trace writer 管理器和 `SpanManager` 创建 handler。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public String eventName()` | 返回 handler 事件名 `tracer_workflow`。 |
| `public TraceWorkflowSpan getTracerWorkflowSpan(String invokeId)` | 读取已有 workflow span；若不存在则创建新 span。 |
| `public void onCallStart(String invokeId, Map<String, Object> metadata, Object inputs, boolean needSend, List<String> sourceIds)` | 初始化一次 workflow / 组件调用的 span，并按需发送快照。 |
| `public void onPreInvoke(String invokeId, Object inputs, Map<String, Object> componentMetadata, boolean needSend)` | 在组件真正执行前更新输入与组件元数据。 |
| `public void onPreStream(String invokeId, Object chunk, boolean needSend)` | 记录流式输入块；只有 `chunk` 为 `Map` 时才追加到 `streamInputs`。 |
| `public void onInvoke(String invokeId, Map<String, Object> onInvokeData, Exception exception)` | 记录运行中事件、异常或中断状态，并发送快照。 |
| `public void onInteract(String invokeId, Object inputs, Map<String, Object> componentMetadata, boolean needSend)` | 记录交互输入及组件元数据。 |
| `public void onPostStream(String invokeId, Object chunk)` | 追加流式输出块。 |
| `public void onPostInvoke(String invokeId, Object outputs, Object inputs)` | 更新最终输出。 |
| `public void onCallDone(String invokeId, Object outputs)` | 标记调用结束、写入耗时，并发送最终快照。 |

## 说明

- 若 `onInvoke()` 接收到 `GraphInterrupt`，span 状态会被设置为 `interrupted`。
- `formatData()` 会在非中断状态下根据 span 内容自动推导节点状态。
