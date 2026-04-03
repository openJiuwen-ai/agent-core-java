# com.openjiuwen.core.session.tracer.TraceAgentHandler

## 类 TraceAgentHandler

```java
public class TraceAgentHandler extends TraceBaseHandler
```

`TraceAgentHandler` 处理 agent 侧的调用事件，并把开始时间、输入输出、错误和耗时写回 `TraceAgentSpan`。

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public TraceAgentHandler(Object owner, StreamWriterManager streamWriterManager, SpanManager spanManager)` | 使用 owner、trace writer 管理器和 `SpanManager` 创建 handler。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public String eventName()` | 返回 handler 事件名 `tracer_agent`。 |
| `public TraceAgentSpan getTracerAgentSpan(String invokeId)` | 读取已有 agent span；若不存在则基于最后一个 span 创建新 span。 |
| `public void onChainStart(TraceAgentSpan span, Object inputs, Map<String, Object> instanceInfo)` | 记录 chain 开始事件并发送快照。 |
| `public void onChainEnd(TraceAgentSpan span, Object outputs)` | 记录 chain 结束事件并发送快照。 |
| `public void onChainError(TraceAgentSpan span, Object error)` | 记录 chain 错误事件并发送快照。 |
| `public void onLlmStart(TraceAgentSpan span, Object inputs, Map<String, Object> instanceInfo)` | 记录 LLM 开始事件并发送快照。 |
| `public void onLlmRequest(TraceAgentSpan span, Map<String, Object> kwargs)` | 追加一次 LLM 运行中数据并发送快照。 |
| `public void onLlmEnd(TraceAgentSpan span, Object outputs)` | 记录 LLM 结束事件并发送快照。 |
| `public void onLlmError(TraceAgentSpan span, Object error)` | 记录 LLM 错误事件并发送快照。 |
| `public void onPromptStart(TraceAgentSpan span, Object inputs, Map<String, Object> instanceInfo)` | 记录 prompt 开始事件并发送快照。 |
| `public void onPromptEnd(TraceAgentSpan span, Object outputs)` | 记录 prompt 结束事件并发送快照。 |
| `public void onPromptError(TraceAgentSpan span, Object error)` | 记录 prompt 错误事件并发送快照。 |
| `public void onPluginStart(TraceAgentSpan span, Object inputs, Map<String, Object> instanceInfo)` | 记录 plugin 开始事件并发送快照。 |
| `public void onPluginEnd(TraceAgentSpan span, Object outputs)` | 记录 plugin 结束事件并发送快照。 |
| `public void onPluginError(TraceAgentSpan span, Object error)` | 记录 plugin 错误事件并发送快照。 |
| `public void onRetrieverStart(TraceAgentSpan span, Object inputs, Map<String, Object> instanceInfo)` | 记录 retriever 开始事件并发送快照。 |
| `public void onRetrieverEnd(TraceAgentSpan span, Object outputs)` | 记录 retriever 结束事件并发送快照。 |
| `public void onRetrieverError(TraceAgentSpan span, Object error)` | 记录 retriever 错误事件并发送快照。 |
| `public void onEvaluatorStart(TraceAgentSpan span, Object inputs, Map<String, Object> instanceInfo)` | 记录 evaluator 开始事件并发送快照。 |
| `public void onEvaluatorEnd(TraceAgentSpan span, Object outputs)` | 记录 evaluator 结束事件并发送快照。 |
| `public void onEvaluatorError(TraceAgentSpan span, Object error)` | 记录 evaluator 错误事件并发送快照。 |
| `public void onWorkflowStart(TraceAgentSpan span, Object inputs, Map<String, Object> instanceInfo)` | 记录 workflow 开始事件并发送快照。 |
| `public void onWorkflowEnd(TraceAgentSpan span, Object outputs)` | 记录 workflow 结束事件并发送快照。 |
| `public void onWorkflowError(TraceAgentSpan span, Object error)` | 记录 workflow 错误事件并发送快照。 |

## 说明

- 开始类事件会写入 `start_time`、`invoke_type`、`inputs` 与实例元数据。
- 结束类事件会写入 `end_time`、`outputs` 与耗时字符串。
- 错误类事件会把异常整理成 `error.message` 并写入 span。
