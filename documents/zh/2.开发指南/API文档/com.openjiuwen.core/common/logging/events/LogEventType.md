# com.openjiuwen.core.common.logging.events.LogEventType

## 枚举 LogEventType

```java
public enum LogEventType
```

`LogEventType` 定义 logging 子系统内建的事件类型键。每个枚举值都绑定一个稳定的序列化字符串，可用于 `EventClassRegistry` 映射和结构化日志输出。

## Agent 事件

| 枚举值 | 序列化值 |
| --- | --- |
| `AGENT_START` | `agent_start` |
| `AGENT_END` | `agent_end` |
| `AGENT_INVOKE` | `agent_invoke` |
| `AGENT_RESPONSE` | `agent_response` |
| `AGENT_ERROR` | `agent_error` |

## Workflow 事件

| 枚举值 | 序列化值 |
| --- | --- |
| `WORKFLOW_EXECUTE_START` | `workflow_execute_start` |
| `WORKFLOW_EXECUTE_END` | `workflow_execute_end` |
| `WORKFLOW_EXECUTE_ERROR` | `workflow_execute_error` |
| `WORKFLOW_OUTPUT_CHUNK` | `workflow_output_chunk` |
| `WORKFLOW_COMPONENT_START` | `workflow_component_start` |
| `WORKFLOW_COMPONENT_END` | `workflow_component_end` |
| `WORKFLOW_COMPONENT_ERROR` | `workflow_component_error` |
| `WORKFLOW_BRANCH` | `workflow_branch` |

## LLM 事件

| 枚举值 | 序列化值 |
| --- | --- |
| `LLM_CALL_START` | `llm_call_start` |
| `LLM_CALL_END` | `llm_call_end` |
| `LLM_CALL_ERROR` | `llm_call_error` |
| `LLM_STREAM_CHUNK` | `llm_stream_chunk` |

## Tool 事件

| 枚举值 | 序列化值 |
| --- | --- |
| `TOOL_CALL_START` | `tool_call_start` |
| `TOOL_CALL_END` | `tool_call_end` |
| `TOOL_CALL_ERROR` | `tool_call_error` |

## Store 事件

| 枚举值 | 序列化值 |
| --- | --- |
| `STORE_ADD` | `store_add` |
| `STORE_DELETE` | `store_delete` |
| `STORE_UPDATE` | `store_update` |
| `STORE_RETRIEVE` | `store_retrieve` |
| `STORE_LOAD` | `store_load` |

## Memory 事件

| 枚举值 | 序列化值 |
| --- | --- |
| `MEMORY_STORE` | `memory_store` |
| `MEMORY_INIT` | `memory_init` |
| `MEMORY_RETRIEVE` | `memory_retrieve` |
| `MEMORY_DELETE` | `memory_delete` |
| `MEMORY_UPDATE` | `memory_update` |
| `MEMORY_PROCESS` | `memory_process` |

## Session 事件

| 枚举值 | 序列化值 |
| --- | --- |
| `SESSION_CREATE` | `session_create` |
| `SESSION_UPDATE` | `session_update` |
| `SESSION_DELETE` | `session_delete` |

## Context 事件

| 枚举值 | 序列化值 |
| --- | --- |
| `CONTEXT_ADD_MESSAGE` | `context_add_message` |
| `CONTEXT_CLEAR` | `context_clear` |
| `CONTEXT_RETRIEVE` | `context_retrieve` |
| `CONTEXT_SAVE` | `context_save` |

## Retrieval 事件

| 枚举值 | 序列化值 |
| --- | --- |
| `RETRIEVAL_START` | `retrieval_start` |
| `RETRIEVAL_END` | `retrieval_end` |
| `RETRIEVAL_ERROR` | `retrieval_error` |

## Performance 事件

| 枚举值 | 序列化值 |
| --- | --- |
| `PERFORMANCE_METRIC` | `performance_metric` |

## 用户交互事件

| 枚举值 | 序列化值 |
| --- | --- |
| `USER_INPUT` | `user_input` |
| `USER_FEEDBACK` | `user_feedback` |

## System 事件

| 枚举值 | 序列化值 |
| --- | --- |
| `SYSTEM_START` | `system_start` |
| `SYSTEM_SHUTDOWN` | `system_shutdown` |
| `SYSTEM_ERROR` | `system_error` |

## SysOperation 事件

| 枚举值 | 序列化值 |
| --- | --- |
| `SYS_OP_START` | `sys_operation_start` |
| `SYS_OP_END` | `sys_operation_end` |
| `SYS_OP_ERROR` | `sys_operation_error` |
| `SYS_OP_STREAM` | `sys_operation_stream` |

## Checkpoint 事件

| 枚举值 | 序列化值 |
| --- | --- |
| `CHECKPOINT_SAVE` | `checkpoint_save` |
| `CHECKPOINT_RESTORE` | `checkpoint_restore` |
| `CHECKPOINT_CLEAR` | `checkpoint_clear` |
| `CHECKPOINT_ERROR` | `checkpoint_error` |

## Checkpointer Store 事件

| 枚举值 | 序列化值 |
| --- | --- |
| `CHECKPOINTER_STORE_ADD` | `checkpointer_store_add` |
| `CHECKPOINTER_STORE_REMOVE` | `checkpointer_store_remove` |

## Graph Stream 事件

| 枚举值 | 序列化值 |
| --- | --- |
| `GRAPH_STREAM_CHUNK` | `graph_stream_chunk` |
| `GRAPH_SEND_STREAM_CHUNK` | `graph_send_stream_chunk` |
| `GRAPH_RECEIVE_STREAM_CHUNK` | `graph_receive_stream_chunk` |

## Session Stream 事件

| 枚举值 | 序列化值 |
| --- | --- |
| `SESSION_STREAM_CHUNK` | `session_stream_chunk` |
| `SESSION_STREAM_ERROR` | `session_stream_error` |

## Graph Vertex 事件

| 枚举值 | 序列化值 |
| --- | --- |
| `GRAPH_VERTEX_INIT` | `graph_vertex_init` |
| `GRAPH_VERTEX_CALL_START` | `graph_vertex_call_start` |
| `GRAPH_VERTEX_CALL_END` | `graph_vertex_call_end` |
| `GRAPH_VERTEX_CALL_ERROR` | `graph_vertex_call_error` |
| `GRAPH_VERTEX_STREAM_ACTOR_START` | `graph_vertex_stream_actor_start` |
| `GRAPH_VERTEX_STREAM_ACTOR_SHUTDOWN` | `graph_vertex_stream_actor_shutdown` |
| `GRAPH_VERTEX_STREAM_CALL_START` | `graph_vertex_stream_call_start` |
| `GRAPH_VERTEX_STREAM_CALL_END` | `graph_vertex_stream_call_end` |
| `GRAPH_VERTEX_STREAM_CALL_ERROR` | `graph_vertex_stream_call_error` |
| `GRAPH_VERTEX_ABILITY_START` | `graph_vertex_ability_start` |
| `GRAPH_VERTEX_ABILITY_RUNNING` | `graph_vertex_ability_running` |
| `GRAPH_VERTEX_ABILITY_END` | `graph_vertex_ability_end` |
| `GRAPH_VERTEX_ABILITY_ERROR` | `graph_vertex_ability_error` |

## Graph Super Step 事件

| 枚举值 | 序列化值 |
| --- | --- |
| `GRAPH_SUPER_STEP_START` | `graph_super_step_start` |
| `GRAPH_SUPER_STEP_END` | `graph_super_step_end` |
| `GRAPH_SUPER_STEP_ERROR` | `graph_super_step_error` |

## Graph 生命周期事件

| 枚举值 | 序列化值 |
| --- | --- |
| `GRAPH_START` | `graph_start` |
| `GRAPH_END` | `graph_end` |
| `GRAPH_ERROR` | `graph_error` |

## Graph Store 事件

| 枚举值 | 序列化值 |
| --- | --- |
| `GRAPH_STORE_SAVE` | `graph_store_save` |
| `GRAPH_STORE_DELETE` | `graph_store_delete` |
| `GRAPH_STORE_GET` | `graph_store_get` |

## Runner 事件

| 枚举值 | 序列化值 |
| --- | --- |
| `RUNNER_START` | `runner_start` |
| `RUNNER_STOP` | `runner_stop` |
| `RESOURCE_MGR_ADD_RESOURCE` | `add_resource` |
| `RESOURCE_MGR_REMOVE_RESOURCE` | `remove_resource` |
| `RESOURCE_MGR_GET_RESOURCE` | `get_resource` |
| `RESOURCE_MGR_ADD_RESOURCE_SERVER` | `add_resource_server` |
| `RESOURCE_MGR_REMOVE_RESOURCE_SERVER` | `remove_resource_server` |
| `RESOURCE_MGR_REMOVE_TAG` | `remove_tag` |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public String getValue()` | 返回当前枚举对应的稳定字符串。 |
| `public static LogEventType fromValue(String value)` | 根据字符串反查枚举；未知值返回 `null`。 |

## 说明

- `EventClassRegistry` 会依据这些枚举值把事件分派到具体 `BaseLogEvent` 子类。
- `StructuredLogEventTest` 覆盖了 `fromValue("agent_start")`、`fromValue("llm_call_start")` 和未知字符串返回 `null` 的行为。
