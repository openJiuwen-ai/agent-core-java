# ToolGroup

`ToolGroup` 是面向 session 的能力分组单元。一个组由有序且去重的 `GroupMemberRef` 成员组成，成员类型包括：

- `TOOL`：引用注册在 `AbilityManager` 中的普通工具名；
- `SKILL`：引用注册在 `SkillManager` 中的 Skill 名称。

`ToolGroupManager` 只保存组定义和做无状态过滤；工具实例、Skill 内容和 Skill 绑定工具仍由原来的注册中心管理。

## 1. 注册与启停

```java
ReActAgent agent = new ReActAgent(agentCard);
agent.registerToolGroup(new ToolGroup(
        "research",
        "Research capabilities",
        List.of(
                new GroupMemberRef(GroupMemberType.TOOL, "web_search"),
                new GroupMemberRef(GroupMemberType.SKILL, "web_research"))));

AgentSessionApi session = sessionProvider.current();
agent.activateToolGroup("research", session);
agent.deactivateToolGroup("research", session);
```

`activateToolGroup(...)` / `deactivateToolGroup(...)` 只修改当前 session state，不同 session 互不污染。

## 2. 激活与过滤规则

计算 session 可用能力时按以下顺序合并：

1. 读取当前 session 激活的显式组；
2. 激活组中的 `TOOL` 成员参与工具 schema 过滤；
3. 激活组中的 `SKILL` 成员进入 Skill prompt；
4. Skill 绑定工具通过对应的 `skill:<skill-name>` 隐式组继续参与工具过滤；
5. 不属于任何显式组的 Skill 保持旧语义，默认进入 prompt；
6. 不属于任何显式组的普通工具保持旧语义，始终可见、可执行。

如果某个工具被多个显式组引用，采用并集语义：任意一个所属组激活即可见。工具只在显式组内且没有任何组激活时，prompt 过滤和执行入口都会拒绝。

## 3. 主要 API

| 对象 | API | 说明 |
| --- | --- | --- |
| `GroupMemberRef` | `new GroupMemberRef(GroupMemberType.TOOL, toolName)` | 引用一个工具名 |
| `GroupMemberRef` | `new GroupMemberRef(GroupMemberType.SKILL, skillName)` | 引用一个 Skill 名 |
| `ToolGroup` | `new ToolGroup(name, description, members)` | 定义不可变成员分组 |
| `ToolGroup` | `members()` | 读取规范成员视图 |
| `ToolGroupManager` | `registerToolGroup` / `unregisterToolGroup` | 注册组定义 / 删除组分类 |
| `ToolGroupManager` | `getActiveToolNames` / `isToolEligibleForExecution` | 无状态 prompt/执行判定 |
| `ToolGroupManager` | `getActiveSkillNames` / `getUngroupedSkillNames` | 计算激活 Skill 和未分组 Skill |
| `BaseAgent` | `activateToolGroup` / `deactivateToolGroup` | session 级启停 |
| `BaseAgent` | `getActiveToolGroupNames` | 读取当前 session 的组状态 |

`ToolGroup.ofToolNames(...)` 和 `toolNames()` 是过渡兼容 API。新代码优先使用 `GroupMemberRef` 和 `members()`。

## 4. Skill 绑定工具示例

```java
agent.registerToolGroup(new ToolGroup(
        "research",
        "Research capabilities",
        List.of(
                new GroupMemberRef(GroupMemberType.TOOL, "web_search"),
                new GroupMemberRef(GroupMemberType.SKILL, "web_research"))));

agent.registerSkillTools(SkillToolBinding.builder()
        .skillName("web_research")
        .tools(List.of(searchTool))
        .build())
        .toCompletableFuture().join();

agent.activateToolGroup("research", session);
```

激活 `research` 后，`web_search` 的工具 schema、`web_research` 的 Skill prompt 和其绑定工具会一起生效。直接调用 `agent.activateSkill("web_research", session)` 也能激活 Skill 及其隐式组；但如果 `web_search` 同时属于显式组 `research`，显式组停用仍然会阻止该工具。

## 5. 兼容规则和边界

- 未注册任何显式组时，所有工具保持原有 passthrough 行为。
- 显式组和 Skill 隐式组都使用 session state，不引入全局开关。
- `unregisterToolGroup(...)` 只删除组定义，不撤销工具授权；不再属于任何组的工具恢复未分组行为。
- `skill:` 前缀保留给 Skill 隐式组，不能用于手工创建显式组。
- 本能力只做 prompt 过滤和执行入口拒绝，不提供组级 REST API，也不替代沙箱隔离。
- 需要彻底收回工具时，应从 `AbilityManager` 移除或禁用工具。
