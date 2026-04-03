# com.openjiuwen.core.singleagent.AbilityManager

## 类 AbilityManager

```java
public class AbilityManager implements ToolRegistry
```

管理工具、工作流、agent 与 MCP 服务能力卡片，并负责把能力调用分发到对应执行路径。

## 方法

| 签名 | 说明 |
|---|---|
| `public void add(Object ability)` | 注册单个能力对象或能力对象列表。 |
| `public Object remove(String name)` | 按名称移除能力；若目标为 MCP 服务，还会清理其缓存工具。 |
| `public List<Object> remove(List<String> names)` | 按名称列表批量移除能力。 |
| `public Object get(String name)` | 按名称读取能力卡片或 MCP 配置。 |
| `public List<Object> list()` | 返回当前登记的全部能力对象。 |
| `public List<ToolInfo> listToolInfo()` | 返回全部可暴露给 LLM 的 `ToolInfo` 列表。 |
| `public List<ToolInfo> listToolInfo(List<String> names, String mcpServerName)` | 按能力名称或 MCP 服务名过滤 `ToolInfo` 列表。 |
| `@Override public void setToolDescription(String toolName, String description)` | 更新已登记 `ToolCard` 的描述。 |
| `public ToolExecutionResult executeAsToolExecutor(Object toolCallObj, Session session)` | 将单个 `ToolCall` 以 `ToolExecutor` 形式执行。 |
| `public List<ToolExecutionEntry> execute( AgentCallbackContext ctx, Object toolCall, Session session, String tag )` | 在 rail 生命周期钩子下执行一个或多个能力调用。 |
| `public ToolExecutionEntry executeSingleToolCall(ToolCall toolCall, Session session, String tag)` | 按工具、工作流、agent 或 MCP 名称分派单次调用。 |

## 说明

- 相关测试：`AbilityManagerSupplementTest`、`AbilityManagerTest`。
- 该类型负责保存能力元数据、提供增删查接口、输出 `ToolInfo`，并在执行阶段委托 `Runner` / `resourceMgr` 解析具体实例。
