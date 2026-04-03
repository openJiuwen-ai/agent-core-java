# com.openjiuwen.core.common.logging.events.ModuleType

## 枚举 ModuleType

```java
public enum ModuleType
```

`ModuleType` 表示结构化事件所属的模块类别。

## 枚举值

| 枚举值 | 序列化值 | 说明 |
| --- | --- | --- |
| `AGENT` | `agent` | Agent 模块。 |
| `WORKFLOW` | `workflow` | Workflow 模块。 |
| `WORKFLOW_COMPONENT` | `workflow_component` | Workflow 组件模块。 |
| `LLM` | `llm` | 模型调用模块。 |
| `TOOL` | `tool` | 工具调用模块。 |
| `STORE` | `store` | Store 模块。 |
| `MEMORY` | `memory` | Memory 模块。 |
| `SESSION` | `session` | Session 模块。 |
| `CONTEXT` | `context` | Context 模块。 |
| `RETRIEVAL` | `retrieval` | Retrieval 模块。 |
| `SYSTEM` | `system` | 系统级模块。 |
| `USER` | `user` | 用户交互模块。 |
| `SYS_OPERATION` | `sys_operation` | SysOperation 模块。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public String getValue()` | 返回当前枚举对应的小写字符串。 |
