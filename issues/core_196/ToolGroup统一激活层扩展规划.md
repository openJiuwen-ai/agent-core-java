# ToolGroup 统一激活层扩展规划

## 1. 结论

可以借鉴 agentscope 的思路扩展，但不建议直接照搬它的对象持有模型。`agent-core-java` 的 `ToolGroup` 目前是一个不可变注册快照和过滤器，更适合让组持有稳定引用：

- `TOOL`：已注册工具名；
- `SKILL`：已加载 Skill 的引用，先兼容 `skillName`，后续演进为稳定 `skillId`；

存储层三层 Skill 仓库保持不变。ToolGroup 只作为运行时激活层，负责决定哪些 Skill prompt、Tool schema 进入当前 session。

## 2. 现状澄清

### 2.1 不需要新增 `active_group_ids`

当前代码已经有会话级激活组状态：

```java
public static final String ACTIVE_TOOL_GROUP_NAMES_STATE_KEY = "active_tool_group_names";
```

位置：`core/src/main/java/com/openjiuwen/core/singleagent/BaseAgent.java:73`。

组名已经是激活键，`ToolGroupManager` 内部按组名注册和查找。之前提到的 `active_group_ids` 是面向“Skill 资产稳定 id”的方案，不是本次激活层扩展的必要字段。本次应继续使用：

```text
active_tool_group_names
```

原因：

1. 显式 ToolGroup 名称已经要求唯一；
2. session 状态已经按该字段启停；
3. Skill 是组成员，不是组本身；
4. 如果组本身未来要重命名或跨环境迁移，再考虑给 ToolGroup 增加独立 id，而不是现在新增一套激活键。

### 2.2 现有 ToolGroup 能力

当前定义：

```java
public record ToolGroup(String name, String description, Collection<String> toolNames)
```

位置：`core/src/main/java/com/openjiuwen/core/operator/toolgroup/ToolGroup.java:20`。

它已经支持：

1. 注册显式工具组；
2. 注册 `skill:<skillName>` 隐式组；
3. 按 `active_tool_group_names` 过滤可见工具；
4. 过滤工具执行；

### 2.3 Skill 当前缺口

`active_skill_names` 只影响 Skill 绑定工具的可见性和执行检查：

位置：`core/src/main/java/com/openjiuwen/core/singleagent/BaseAgent.java:222`。

但 Skill prompt 仍然渲染全部已加载 Skill：

位置：`core/src/main/java/com/openjiuwen/core/singleagent/skills/SkillUtil.java:171`。

也就是说，当前缺少的是“激活一个组后，组内 Skill 才进入 prompt，同时组内 Skill 绑定工具可见可执行”的统一语义。

## 3. 推荐模型

### 3.1 组成员引用

建议新增：

```java
public enum GroupMemberType {
    TOOL,
    SKILL
}

public record GroupMemberRef(GroupMemberType type, String id) {
}
```

`GroupMemberRef` 是组成员的唯一权威表示。`TOOL / SKILL` 三类成员都统一保存在 `members` 中，不建议让 `toolNames` 和 `members` 同时成为两份独立配置：

```java
public record ToolGroup(
        String name,
        String description,
        Collection<GroupMemberRef> members
) {
}
```

兼容构造器继续接受旧参数 `toolNames`，但只负责把它转换成 `TOOL` 类型成员：

```java
public ToolGroup(String name, String description, Collection<String> toolNames) {
    this(name, description, toToolMembers(toolNames));
}
```

为了兼容现有调用和旧文档 API，可以保留一个派生读取方法：

```java
public Collection<String> toolNames() {
    return members.stream()
            .filter(member -> member.type() == GroupMemberType.TOOL)
            .map(GroupMemberRef::id)
            .toList();
}
```

注意：这个 `toolNames()` 只是兼容视图，不再参与持久化配置，也不应该被 `ToolGroupManager` 当作独立数据源。运行时统一读取 `members()`。

兼容策略（ToolGroup 在 0.1.17 已发布，需保证向后兼容）：

1. 同一版本保留旧构造器和派生 `toolNames()`，确保 0.1.17 的下游消费者升级到 0.1.19 后编译不失败；
2. 新增重载构造器接受 `Collection<GroupMemberRef>`，Java 方法重载机制根据参数类型自动路由；
3. 旧构造器和 `toolNames()` 标记 `@Deprecated(since = "0.1.19")`，引导新代码使用 `GroupMemberRef`；
4. 后续主版本升级时删除兼容层，删除前先把仓库内的 examples、tests 和 BaseAgent 迁移到新 API。
### 3.2 不直接持有 Java 对象

agentscope 的 `ToolGroup` 直接持有 `ToolBase`、`Skill`、`SkillLoaderBase`、 等对象。Java 侧不建议这样做，原因：

1. 现有 `ToolGroupManager` 是无状态过滤注册表；
2. `Skill` 由 `SkillManager` 加载和热更新；
3. ToolGroup 保存对象会导致生命周期、热更新和并发持有关系变复杂。

因此 Java 侧采用“引用 + 运行时解析”。

## 3.3 成员引用与激活过滤全链路

### 3.3.1 注册层（启动时一次性完成）

```
┌─────────────────────────────────────────────────────────────────────┐
│                        注册阶段（Agent 构建/初始化）                    │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  AbilityManager                     SkillManager                    │
│  ┌──────────────────┐              ┌──────────────────────────┐    │
│  │ Map<name,        │              │ Map<name, Skill>          │    │
│  │   ToolCard>      │              │                           │    │
│  │                  │              │ ← CompositeSkillRepository│    │
│  │ "bash" → ToolCard│              │   (classpath < fs < ws)   │    │
│  │ "edit" → ToolCard│              │                           │    │
│  │ "read" → ToolCard│              │ "git-helper" → Skill      │    │
│  └──────────────────┘              │ "search"     → Skill      │    │
│                                    └──────────────────────────┘    │
│  SkillToolRegistry                                                  │
│  ┌──────────────────┐              ToolGroupManager                │
│  │ Map<skillName,   │              ┌──────────────────────────┐    │
│  │   List<Tool>>    │              │ Map<groupName, ToolGroup> │    │
│  │                  │              │                           │    │
│  │ "git-helper" →   │              │ "research" → ToolGroup    │    │
│  │   [bash, read]   │              │   members: [              │    │
│  │                  │              │     TOOL("bash"),         │    │
│  └──────────────────┘              │     TOOL("grep"),         │    │
│                                    │     SKILL("git-helper")   │    │
│                                    │   ]                       │    │
│                                    │ "skill:git-helper" → ...  │    │
│                                    └──────────────────────────┘    │
└─────────────────────────────────────────────────────────────────────┘


GroupMemberType（枚举）      GroupMemberRef（record）
┌──────────┐              ┌────────────────────────┐
│ TOOL     │              │ type: GroupMemberType  │
│ SKILL    │              │ id: String（工具名或skill名）│
└──────────┘              └────────────────────────┘
                                    │
                                    │ 被 ToolGroup 持有
                                    ▼
ToolGroup（record）
┌─────────────────────────────────────┐
│ name: String（组名，唯一）             │
│ description: String                 │
│ members: Collection<GroupMemberRef> │  ← 从原来的 toolNames 扩展而来
└─────────────────────────────────────┘
                │
                │ 被 ToolGroupManager 持有
                ▼
ToolGroupManager
┌─────────────────────────────────────────────────┐
│ Map<groupName, ToolGroup>                        │
│                                                 │
│ 职责：                                           │
│ 1. 注册/反注册组                                  │
│ 2. 过滤：哪些工具名在当前激活组中可见               │
│ 3. 判定：某个工具名是否可执行                      │
└─────────────────────────────────────────────────┘
```

**注册要点：**
- `AbilityManager` 以 tool `name` 为主键注册 ToolCard，同名 fail-fast；
- `SkillManager` 以 skill `name` 为主键注册 Skill（经由三层仓库合并后加载）；
- `SkillToolBinding` 声明某个 Skill 拥有哪些 Tool（代码级绑定），由 `SkillToolRegistry` 索引；
- `ToolGroupManager` 注册显式 ToolGroup，成员为 `GroupMemberRef(TOOL|SKILL, id)` 引用；
- Skill 激活时框架自动注册 `skill:<skillName>` 隐式组（保留前缀，不可显式占用）。

### 3.3.2 激活层（session 运行时）

```
┌─────────────────────────────────────────────────────────────────────┐
│                     Session 状态（per-session 隔离）                  │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  active_tool_group_names: ["research"]      ← activateToolGroup()   │
│  active_skill_names: []                     ← activateSkill() 旧 API│
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

激活组只写 `active_tool_group_names`，不隐式写 `active_skill_names`，避免两个状态源互相污染。

### 3.3.3 每次 invoke 的解析流程（核心链路）

```text
[Session 状态]
 读取: active_tool_group_names + active_skill_names
  说明: 每个 session 独立维护，互不影响
    |
    | 先判断：是否注册了 ToolGroup？
    v

[BaseAgent.listEffectiveToolInfo(session)]
 判断: toolGroupManager.getGroups() 是否为空
  说明: 当前路径的入口分支（Issue 196 已实现）
        无组 → 短路返回合并结果；有组 → 合并 + ToolGroup 过滤

    +--> [当前路径分支 1：getGroups() 为空]
    |     listEffectiveToolInfo(activeSkillNames)
    |       只做合并（全局工具 + active_skill_names 绑定工具）
    |       说明: Issue 196 之前就存在的合并逻辑，
    |             没有 ToolGroup 过滤，只有 skill 激活
    |
    +--> [当前路径分支 2：getGroups() 非空]
    |     listEffectiveToolInfoWithGroups(...)
    |       步骤 ①: 内部先复用分支 1 的合并逻辑
    |       步骤 ②: 再叠加 ToolGroupManager.getActiveToolNames() 过滤
    |       说明: 这是 Issue 196 的现状，只有 TOOL 类型分组
    |
    +--> [目标路径：本次规划要改造成的形态]
          ToolGroup.members 同时持有 TOOL 和 SKILL 引用
          effectiveSkills 由 active 组的 SKILL 成员 + ungrouped skill 派生
          skill prompt 过滤和 tool 过滤共享同一份 effectiveSkills
          说明: 以下步骤 ①~⑥ 描述的是目标路径的完整流程
          |
          | ① 计算 effectiveSkills（先算 skill 集合）
          |   合并三个来源：
          |     active_skill_names        ← 旧 API 直连激活
          |     ∪ active 组 SKILL refs   ← 激活组的 SKILL 成员
          |     ∪ ungrouped skills       ← 未被任何组引用的 skill
          |   说明: ungrouped skill 走 passthrough，始终可见
          |
          | ② 合并工具源（再算工具全集）
          |   globalTools = abilityManager.listToolInfo()
          |     说明: 全局注册的工具（不依赖 skill）
          |   skillTools = skillToolRegistry
          |                  .listToolsByActiveSkill(effectiveSkills)
          |     说明: effectiveSkills 中每个 skill 的绑定工具
          |   merged = globalTools ∪ skillTools（按 name 去重）
          |     说明: 同名冲突在此阶段 fail-fast
          |
          | ③ 构建活跃组名集合
          |   allActiveGroups = active_tool_group_names
          |                    ∪ {skill:<name> for each effectiveSkill}
          |   说明: skill:<name> 隐式组随 effectiveSkills 自动激活
          |
          | ④ 工具过滤（ToolGroupManager 统一入口）
          |   ToolGroupManager.getActiveToolNames(allActiveGroups, merged)
          |     对 merged 中每个 tool name：
          |       查 owningGroups（一个工具可属多个组 → 并集语义）
          |       任一所属组激活 → 保留
          |       未分组（owningGroups 为空）→ 保留（passthrough）
          |       所有所属组均未激活 → 移除
          |   说明: 移除的工具 schema 不会出现在发给 LLM 的请求中
          |
          | ⑤ Skill prompt 过滤
          |   SkillUtil.getSkillPrompt(effectiveSkillRefs)
          |     effectiveSkillRefs 非空 → 只渲染命中的 Skill
          |     effectiveSkillRefs 为空 → 渲染全部（兼容旧行为）
          |     说明: 与步骤④使用同一份 effectiveSkills，保证一致性
          |
          | ⑥ 组装模型请求
          |   system prompt = base prompt + filtered skill prompt
          |   tools = filtered ToolInfo schemas
          |   说明: 两者一起发送给 LLM
          v
```

### 3.3.4 Tool 与 Skill 的分组路径差异

| 维度 | TOOL 成员 | SKILL 成员 |
|---|---|---|
| 注册表示 | `GroupMemberRef(TOOL, "bash")` | `GroupMemberRef(SKILL, "git-helper")` |
| 激活后影响 | 该 tool schema 进入 `tools` 数组 | 该 skill metadata 进入 prompt + 其绑定工具进入 `tools` |
| 过滤入口 | `ToolGroupManager.getActiveToolNames()` | `SkillUtil.getSkillPrompt(effectiveSkillRefs)` |
| 隐式联动 | 无 | 额外注册 `skill:<name>` 隐式组，使绑定工具通过工具过滤路径同样生效 |
| 未激活表现 | schema 不发给 LLM，执行被 checker 拒绝 | prompt 中无该 skill 描述，模型不知道它存在 |

**SKILL 成员的"双重过滤"：**

一个 Skill 被放入 ToolGroup 后，激活该组会同时触发两条过滤路径：

1. **Prompt 路径**：`effectiveSkills` 包含该 skill → `getSkillPrompt()` 渲染其元数据进 system prompt → 模型知道该 skill 的存在和用法。
2. **Tool 路径**：`skill:<skillName>` 隐式组被加入 `allActiveGroups` → `SkillToolRegistry.listToolsByActiveSkill()` 返回该 skill 绑定的工具 → 合并进 `merged` → 通过 `getActiveToolNames()` 过滤后进入 schema。

两条路径缺一不可：只有 prompt 没有工具，模型知道 skill 但无法执行；只有工具没有 prompt，模型看到了 schema 但不知道何时使用。


### 3.3.5 Ungrouped 成员与 passthrough 语义

`ToolGroupManager` 的核心计算逻辑不因成员模型扩展而改变——仍然按 `isToolEligible` 的并集语义过滤，`owningGroups` 为空则 passthrough：

```java
// ToolGroupManager.java:217 — 对 TOOL 和 SKILL 成员均适用
if (owningGroups == null || owningGroups.isEmpty()) {
    return true;  // 未分组：始终可用
}
```

#### Ungrouped Tool

不属于任何 ToolGroup 的工具，无论组激活状态如何，始终可见、可执行。这是现有行为，扩展后不变。

#### Ungrouped Skill

未被任何 ToolGroup 的 SKILL 成员引用、也未通过 `activateSkill()` 直连激活的 skill，同样始终可见。effectiveSkills 的完整计算公式：

```text
effectiveSkills =
    active_skill_names                          （旧 API 直连激活）
  ∪ SKILL refs from active groups               （激活组的 SKILL 成员）
  ∪ ungrouped skills                            （未被任何已注册组引用）
```

这保证了与 Tool 侧一致的行为：**注册了 ToolGroup 但不激活任何组时，所有 skill 仍然可见**（等价于旧路径全量透传）。只有当某个组被激活时，该组 SKILL 成员引用的 skill 才通过组路径生效，而 ungrouped skill 不受影响。

#### 端到端链路总览（含 ungrouped 路径）

```text
[注册层 — 启动时一次性完成]
 AbilityManager        → Map<name, ToolCard>          全局工具注册表
 SkillManager          → Map<name, Skill>             技能注册表（三层仓库合并后）
 SkillToolRegistry     → Map<skillName, List<Tool>>   skill→tool 绑定索引
 ToolGroupManager      → Map<groupName, ToolGroup>    分组注册表
  说明: 以上四个模块在注册阶段各司其职，运行时只读

[Session 状态 — per-session 隔离]
 active_tool_group_names                    ← activateToolGroup() 写入
 active_skill_names                         ← activateSkill() 写入（旧 API）
  说明: 两个状态源独立，激活组不隐式写 skill names

[每次 invoke() 解析]
 输入: session 状态 + 四个注册表
 计算: 见 §3.3.3 步骤 ①~⑥

 输出:
    +--> [system prompt]
    |     base prompt + filtered skill prompt
    |     说明: effectiveSkills 中的 skill 元数据 + ungrouped skill
    |
    +--> [tools schema]
    |     filtered ToolInfo 数组
    |     说明: 全局工具 + 激活 skill 绑定工具 + ungrouped 工具
    |
    +--> 发送给 LLM
          说明: 模型看到的工具和 prompt 完全一致

[模型返回 tool_call 后]
 AbilityManager.isToolEligibleForExecution()
  说明: 与 schema 过滤同一套 isToolEligible 逻辑
    |
    +--> eligible   → 放行执行
    +--> not eligible → 拒绝 + 审计日志
```

**Ungrouped 判定的实现要点：**

| 成员类型 | 判定方式 | passthrough 行为 |
|---|---|---|
| Ungrouped Tool | 遍历 `toolGroupsByName`，收集该 tool name 的 owning groups；为空 → passthrough | 始终进入 schema、始终可执行 |
| Ungrouped Skill | 遍历 `toolGroupsByName`，收集所有组的 SKILL 引用；该 skill name 不在任何组的 SKILL 引用中 → passthrough | 始终进入 prompt、始终可加载 |

#### Prompt 过滤的具体逻辑

`SkillUtil.getSkillPrompt(effectiveSkillRefs)` 的判定树：

```text
先判断: effectiveSkillRefs 是否为空
  |
  +--> 为空（没有任何组激活且 active_skill_names 也为空）
  |      → 渲染全部 Skill（旧行为，向后兼容）
  |
  +--> 非空
         → 遍历 SkillManager 中所有已注册 skill:
             先判断: skill.name ∈ effectiveSkillRefs?
               +--> 是 → 渲染
               +--> 否 → 再判断: 该 skill 是否属于任何已注册组的 SKILL 引用?
                           +--> 不属于任何组 → 渲染（ungrouped passthrough）
                           +--> 属于某组但该组未激活 → 跳过
```


### 3.3.6 执行校验（模型返回 tool call 后）

```text
[模型返回 tool_call]
 输入: tool name（如 "bash"）
  说明: LLM 只传 name，不传 id
    |
    v

[AbilityManager → ToolAvailabilityChecker]
 判断: ToolGroupManager.isToolEligibleForExecution(name, activeGroups)
  说明: 与 schema 过滤使用同一套 isToolEligible 逻辑
    |
    +--> eligible（任一所属组激活 或 未分组）
    |      → 放行，执行 Tool
    |      说明: 保证"看得见的工具一定能调"
    |
    +--> not eligible（所属组均未激活）
           → 拒绝，返回错误信息
           说明: 保证"看不见的工具一定调不了"
           附带审计日志记录
```

执行校验与 schema 过滤使用同一套 `isToolEligible` 逻辑，保证"看不见的工具一定调不了，看得见的工具一定能调"。


## 4. 激活语义


### 4.1 统一激活键

继续使用：

```text
active_tool_group_names
```

激活组：

```java
agent.activateToolGroup("research", session);
```

当前 session 生效内容：

1. 组内 `TOOL` 引用对应的工具 schema 可见、可执行；
2. 组内 `SKILL` 引用对应的 Skill metadata 进入 prompt；
3. 组内 Skill 绑定工具可见、可执行；

### 4.2 `active_skill_names` 兼容策略

`active_skill_names` 保留，不删除。

它继续表示旧 API 直接激活的 Skill。真正的生效 Skill 集合改为派生：

```text
effectiveSkills =
    active_skill_names 直接激活的 Skill
  + active_tool_group_names 中 SKILL 成员引用的 Skill
```

不要让 `activateToolGroup()` 隐式写入 `active_skill_names`，否则会出现两个状态源互相污染的问题。

### 4.3 Skill prompt 过滤

新增或重载：

```java
public String getSkillPrompt(Collection<String> effectiveSkillRefs)
```

过滤规则：

1. `effectiveSkillRefs` 为空时，保持旧行为：渲染全部 Skill；
2. 非空时，只渲染命中的 Skill；
3. 先按 `skillId` 匹配；
4. `skillId` 缺失时按 `skillName` 兼容匹配；
5. 同名多 Skill 场景下，引用必须能解析到唯一 Skill，否则注册或激活时报错。

`ReActAgent` 在 `invoke()` 内已有 session，但当前 `updateSkillPromptBuilderSection()` 没有把 session 传给 `getSkillPrompt()`。需要把这个链路打通。

## 5. 实施阶段

### Step 1：新增 GroupMemberType 和 GroupMemberRef

**改动文件：**

| 文件 | 操作 | 内容 |
|---|---|---|
| `GroupMemberType.java` | 新增 | 枚举 `TOOL`, `SKILL` |
| `GroupMemberRef.java` | 新增 | record `(GroupMemberType type, String id)`，校验 id 非空 |

**验收标准：**

- [ ] 两个类编译通过，有单元测试覆盖构造校验（null type / blank id 抛异常）
- [ ] 不影响任何现有代码

**预估规模：** 40–60 行（含测试）

---

### Step 2：扩展 ToolGroup record（兼容构造器 + 派生方法）

**改动文件：**

| 文件 | 操作 | 内容 |
|---|---|---|
| `ToolGroup.java` | 修改 | record 第三组件 `toolNames` → `members`；新增兼容构造器（String 版委托转换）；新增派生 `toolNames()`；新增 `skills()` 派生方法；`members` 校验（非空 id、类型合法、去重保序） |

**关键改动：**

```java
public record ToolGroup(String name, String description, Collection<GroupMemberRef> members) {

    // 兼容构造器：0.1.17 已发布，保留旧签名
    public ToolGroup(String name, String description, Collection<String> toolNames) {
        this(name, description, toToolMembers(toolNames));
    }

    // 派生视图：只返回 TOOL 类型成员的 id
    public Collection<String> toolNames() {
        return members.stream()
                .filter(m -> m.type() == GroupMemberType.TOOL)
                .map(GroupMemberRef::id)
                .toList();
    }

    // 派生视图：只返回 SKILL 类型成员的 id
    public Collection<String> skillNames() {
        return members.stream()
                .filter(m -> m.type() == GroupMemberType.SKILL)
                .map(GroupMemberRef::id)
                .toList();
    }
}
```

**验收标准：**

- [ ] 旧 `new ToolGroup(name, desc, List.of("bash"))` 编译通过且行为不变
- [ ] `group.toolNames()` 返回 TOOL 类型成员的 id 列表
- [ ] `group.skillNames()` 返回 SKILL 类型成员的 id 列表
- [ ] 含 SKILL 引用的组，`toolNames()` 不包含 SKILL 成员
- [ ] 显式组仍禁止 `skill:` 前缀
- [ ] 空组（无 TOOL 无 SKILL）允许注册但打 warning

**预估规模：** 80–120 行（含测试）

---

### Step 3：迁移 ToolGroupManager 读取逻辑

**改动文件：**

| 文件 | 操作 | 内容 |
|---|---|---|
| `ToolGroupManager.java` | 修改 | `findOwningGroupsByToolName()` / `isToolEligible()` / `getActiveToolNames()` 改为读 `members()`；新增 `findOwningGroupsBySkillName()` 支持按 skill 查所属组 |

**关键改动：**

- `toolGroup.toolNames()` → `toolGroup.members()` 过滤 TOOL 类型
- 新增 `getActiveSkillNames(activeGroupNames)` 方法：返回所有激活组中 SKILL 成员引用的 skill name 集合
- `isToolEligibleForExecution()` 逻辑不变（并集语义）

**验收标准：**

- [ ] 现有 ToolGroupManagerTest 全部通过（不改测试用例，只改内部实现）
- [ ] `getActiveSkillNames(["research"])` 返回 research 组中 SKILL 引用的 skill 名
- [ ] 未注册组时 `getActiveSkillNames()` 返回空集
- [ ] ToolGroupManager 内部不再直接读取 `toolNames` 字段（已改为派生视图）

**预估规模：** 60–100 行（含测试）

---

### Step 4：BaseAgent 集成 effectiveSkills

**改动文件：**

| 文件 | 操作 | 内容 |
|---|---|---|
| `BaseAgent.java` | 修改 | `listEffectiveToolInfoWithGroups()` 中计算 effectiveSkills；`registerImplicitSkillGroups()` 用新构造器注册隐式组 |

**关键改动：**

```java
// BaseAgent.listEffectiveToolInfoWithGroups() 中新增：
// ① 从激活组中提取 SKILL 成员引用的 skill names
List<String> groupSkillNames = toolGroupManager.getActiveSkillNames(activeToolGroupNames);

// ② effectiveSkills = active_skill_names ∪ groupSkillNames ∪ ungrouped skills
//    ungrouped 判定：遍历所有已注册组的 SKILL 成员，不在其中的 skill 为 ungrouped
List<String> effectiveSkills = mergeSkillSources(
    activeSkillNames, groupSkillNames, toolGroupManager);

// ③ 用 effectiveSkills 查询 skill 绑定工具（替代原来的 registeredSkillNames）
List<ToolInfo> merged = listEffectiveToolInfo(effectiveSkills);

// ④ 构建活跃组名集合（不变）
allActiveGroupNames = activeToolGroupNames
    ∪ {skill:<name> for each effectiveSkills}
```

**验收标准：**

- [x] 注册 ToolGroup 含 SKILL("git-helper") + 激活该组 → `getSkillManager().has("git-helper")` 且 `skillToolRegistry` 返回其绑定工具
- [x] 不激活任何组 → effectiveSkills 只含 ungrouped skills（等价于旧行为）
- [x] `activateSkill("x")` + ToolGroup 含 SKILL("x") → effectiveSkills 去重后只含一个 "x"
- [x] 现有 BaseAgentToolGroupTest 全部通过

**预估规模：** 80–120 行（含测试）

---

### Step 5：SkillUtil prompt 过滤

**改动文件：**

| 文件 | 操作 | 内容 |
|---|---|---|
| `SkillUtil.java` | 修改 | `getSkillPrompt()` 新增 filtered 重载；保留无参版本兼容 |

**关键改动：**

```java
// 新增重载：只渲染 effectiveSkillRefs 中的 skill + ungrouped skill
public String getSkillPrompt(Collection<String> effectiveSkillRefs) {
    // ① effectiveSkillRefs 为空 → 渲染全部（旧行为）
    // ② 非空 → 遍历 skillManager.getAll():
    //       skill.name ∈ effectiveSkillRefs → 渲染
    //       skill 不属于任何已注册组的 SKILL 引用 → 渲染（ungrouped passthrough）
    //       skill 属于某组但该组未激活 → 跳过
}

// 保留无参版本（向后兼容）
public String getSkillPrompt() {
    // 委托给 getSkillPrompt(null) 或直接调旧逻辑
}
```

**验收标准：**

- [x] 无参 `getSkillPrompt()` 行为不变（渲染全部）
- [x] `getSkillPrompt(["git-helper"])` 只渲染 git-helper 的元数据
- [x] `getSkillPrompt(empty)` 渲染全部
- [x] prompt 中 skill 顺序稳定（按注册顺序）

**预估规模：** 40–80 行（含测试）

---

### Step 6：ReActAgent 串联 prompt 过滤

**改动文件：**

| 文件 | 操作 | 内容 |
|---|---|---|
| `ReActAgent.java` | 修改 | `updateSkillPromptBuilderSection()` 增加 session 参数，内部计算 effectiveSkillRefs 并传入 filtered 重载 |

**关键改动：**

```java
// 旧签名（保留，委托到新签名）
public void updateSkillPromptBuilderSection(String renderedSystemPrompt) { ... }

// 新签名：传入 session 以获取 active groups
public void updateSkillPromptBuilderSection(
        String renderedSystemPrompt, AgentSessionApi session) {
    List<String> effectiveSkillRefs = computeEffectiveSkillRefs(session);
    addPersistentPromptBuilderSection(
        SKILLS_SECTION,
        skillUtil.getSkillPrompt(effectiveSkillRefs),
        SKILLS_SECTION_PRIORITY);
}
```

**验收标准：**

- [x] 激活组含 SKILL 成员后，prompt 中只出现该 skill（其他被组覆盖的 skill 隐藏）
- [x] 停用组后，prompt 中该 skill 消失
- [x] 不同 session 的 prompt 互不影响
- [x] 无组模式下 prompt 渲染全部 skill（向后兼容）

**预估规模：** 40–60 行（含测试）

---

### Step 7：端到端集成验证

**改动文件：**

| 文件 | 操作 | 内容 |
|---|---|---|
| `ToolGroupSkillMemberIntegrationTest.java` | 新增 | 覆盖以下验收场景 |

**验收场景清单：**

| # | 场景 | 预期 |
|---|---|---|
| 1 | 注册组 `research`（TOOL:"bash" + SKILL:"git-helper"），激活 `research` | prompt 含 git-helper；tools 含 bash + git-helper 绑定工具 |
| 2 | 场景 1 基础上停用 `research` | prompt 无 git-helper；tools 无 bash（如果 bash 只属于 research） |
| 3 | 场景 1 基础上 `activateSkill("git-helper")` 再 `deactivateToolGroup("research")` | git-helper 仍可见（active_skill_names 兜底） |
| 4 | 注册组 `coding`（TOOL:"bash"），注册 skill `search`（有绑定工具但不在任何组） | search 是 ungrouped → prompt 和绑定工具始终可见 |
| 5 | 两个 session：session A 激活 research，session B 不激活 | A 可见，B 不可见；互不干扰 |
| 6 | 未注册任何 ToolGroup | 行为与 0.1.17 完全一致（回归验证） |
| 7 | 旧构造器 `new ToolGroup(name, desc, toolNames)` 创建组并激活 | tool 过滤正常，skill 集为空 |

**预估规模：** 150–250 行

---

### Step 8：迁移存量调用方 + 清理

**改动文件：**

| 文件 | 操作 | 内容 |
|---|---|---|
| `ToolGroupSkillExample.java` | 修改 | 4 处 `new ToolGroup(name, desc, List.of(...))` 保持不变（兼容构造器自动处理） |
| `BaseAgent.java` | 修改 | 隐式组注册改为使用新构造器（TOOL 类型 GroupMemberRef） |
| 各测试文件 | 可选 | 逐步迁移到新构造器（非阻塞，兼容构造器已保证编译） |

**验收标准：**

- [ ] 全量编译通过（`mvn clean compile`）
- [ ] 全量测试通过（`mvn clean test`）
- [ ] `@Deprecated` 警告数量 = 0（仓库内无旧 API 直接调用）
- [ ] 文档更新（`documents/zh/2.开发指南/.../ToolGroup.md`）

---

### 开发节奏建议

```text
Step 1–2（模型层）：一个 commit，纯新增 + record 扩展，风险最低
Step 3（过滤层）：一个 commit，ToolGroupManager 内部改造，回归测试保障
Step 4–5（集成层）：一个 commit，BaseAgent + SkillUtil 联动，行为变更核心
Step 6（串联层）：一个 commit，ReActAgent 串联 prompt 过滤
Step 7（验证层）：一个 commit，集成测试
Step 8（清理层）：一个 commit，迁移 + 文档

每个 Step 完成后跑一次增量测试（mvn test -pl core），
全部完成后跑全量回归（mvn clean test）。
```

## 6. 总体工作量判断

Phase 1–2 是 MVP 完整闭环，预计：

```text
Java 主代码：250–400 行
测试代码：200–350 行
示例和文档：80–150 行
总改动：500–900 行
```

如果只做最小可用能力，可以先做 Phase 1 + Phase 2：

```text
总改动：500–700 行
```

该范围可控，不需要重构三层 Skill 仓库，也不需要把 Skill 改成 Tool 的子类，不需要改动 MCP 集成层。

## 7. 主要风险

| 风险 | 处理 |
| --- | --- |
| Skill 引用歧义 | Phase 2 先兼容 name；Phase 3 强制唯一解析 |
| Prompt 陈旧 | `invoke()` 内按当前 session 的 active groups 重建 Skill prompt |
| 状态污染 | `activateToolGroup()` 不写 `active_skill_names` |
| 工具名冲突 | 继续沿用现有重复 ToolInfo 检测 |
| 并发注册 | `ToolGroupManager` 现有同步结构保留，新增字段不可变 |

## 8. 建议 MVP 范围

第一版建议只做：

1. `ToolGroup` 增加 Skill 成员引用；
2. `active_tool_group_names` 保持唯一激活键；
3. 激活组后过滤 Skill prompt；
4. 激活组后合并 Skill 绑定工具；
5. 保留旧 API 和旧 session state 完全兼容。

不放入第一版：

1. 不新增 `active_group_ids`；
2. 不重写 Skill 三层仓库；
3. 不让 ToolGroup 直接持有 Skill / Tool 对象；
4. 不实现模型自主调用 `ResetTools`，可作为 Phase 4 增强。




