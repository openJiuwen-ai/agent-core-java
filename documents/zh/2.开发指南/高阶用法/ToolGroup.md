# ToolGroup

`ToolGroup` 用于把一组工具收拢成具名作用域。工具卡片仍然注册在 `AbilityManager`，`ToolGroupManager` 只保存“组名 -> 工具名”的不可变映射，并做无状态过滤。

## 1. 注册与启停

```java
ReActAgent agent = new ReActAgent(agentCard);
agent.registerToolGroup(new ToolGroup(
        "search_tools",
        "Search tools for this session",
        List.of("web_search", "vector_search")
));

AgentSessionApi session = sessionProvider.current();
agent.activateToolGroup("search_tools", session);
agent.deactivateToolGroup("search_tools", session);
```

`activateToolGroup(...)` / `deactivateToolGroup(...)` 只修改当前 session state，key 是 `active_tool_group_names`。不同 session 的状态互不污染。

## 2. 过滤与执行规则

- 没有注册任何组时，所有工具保持原有行为。
- 未分组工具始终可见、可执行。
- 属于显式组的工具，只有至少一个所属组在当前 session 激活时才进入 prompt。
- 停用组中的工具即使被直连调用，也会在执行层返回不可用。
- Skill 通过保留组名 `skill:<skill-name>` 联动；它不能绕过显式组的停用规则。

## 3. 主要 API

| 对象 | API | 说明 |
| --- | --- | --- |
| `ToolGroup` | `new ToolGroup(name, description, toolNames)` | 定义不可变工具组 |
| `ToolGroupManager` | `registerToolGroup` / `unregisterToolGroup` | 管理组定义 |
| `ToolGroupManager` | `getActiveToolNames` / `isToolEligibleForExecution` | 无状态 prompt/执行判定 |
| `BaseAgent` | `activateToolGroup` / `deactivateToolGroup` | session 级启停 |
| `BaseAgent` | `getActiveToolGroupNames` | 读取当前 session 的组状态 |

## 4. 推荐用法

按业务功能划分粗粒度组，例如 `search_tools`、`file_tools`、`admin_tools`。避免为单个工具建组；真正只受权限控制的全局工具可以保持未分组。

## 5. ToolGroup 与 Skill 绑定示例

完整可编译示例见 [`examples/toolgroup/README.md`](../../../../examples/toolgroup/README.md) 和 [`examples/toolgroup/ToolGroupSkillExample.java`](../../../../examples/toolgroup/ToolGroupSkillExample.java)。示例要点：

```java
agent.registerToolGroup(new ToolGroup("research", "Research tools", List.of("skill_search")));
agent.registerSkillTools(SkillToolBinding.builder()
        .skillName("web_research")
        .tools(List.of(searchTool))
        .build())
        .toCompletableFuture().join();

agent.activateToolGroup("research", session);
agent.activateSkill("web_research", session);
agent.deactivateToolGroup("research", session);
```

`web_research` 仍处于激活态时，`skill:web_research` 隐式组不会恢复已被显式停用的 `research` 组中的同名工具 `skill_search`。对停用工具的直连调用会在执行层返回 `not active`。

## 6. 兼容规则和已知限制

- 未注册任何显式组时，所有工具保持原有 passthrough 行为。
- 显式组和 Skill 隐式组都使用 session state，不引入全局开关。
- 同名工具跨组采用并集语义；只要一个所属组激活即可见。
- 本能力只做 prompt 过滤和执行入口拒绝，不提供组级 REST API，也不替代沙箱隔离。
- `skill:` 前缀保留给 Skill 隐式组，不能用于手工创建显式组。
