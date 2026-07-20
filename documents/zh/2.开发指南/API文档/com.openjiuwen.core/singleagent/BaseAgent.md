# com.openjiuwen.core.singleagent.BaseAgent

## 抽象类 BaseAgent

```java
public abstract class BaseAgent
```

单智能体实现的抽象基类。

## 公共字段

| 签名 | 说明 |
|---|---|
| `public static final String ACTIVE_SKILL_NAMES_STATE_KEY` | Session 中保存已激活技能名称的状态键。 |

## 方法

| 签名 | 说明 |
|---|---|
| `public void lazyInitSkill()` | 根据当前配置延迟初始化或更新技能工具对象。 |
| `public abstract BaseAgent configure(Object config)` | 设置运行配置并返回自身。 |
| `public CompletionStage<Boolean> registerSkill(String skillPath)` | 从一个本地路径注册技能。 |
| `public CompletionStage<Boolean> registerSkill(String skillPath, boolean useMetadataName)` | 从一个本地路径注册技能，并指定是否使用元数据名称。 |
| `public CompletionStage<Boolean> registerSkill(List<String> skillPaths)` | 从多个本地路径注册技能。 |
| `public CompletionStage<Boolean> registerSkill(List<String> skillPaths, boolean useMetadataName)` | 从多个本地路径注册技能，并指定是否使用元数据名称。 |
| `public CompletionStage<Boolean> register_skill(List<String> skillPaths)` | `registerSkill(List<String>)` 的 snake_case 兼容入口。 |
| `public CompletionStage<Boolean> registerSkillTools(SkillToolBinding binding)` | 注册一个技能工具绑定。 |
| `public CompletionStage<Boolean> registerSkillTools(List<SkillToolBinding> bindings)` | 注册多个技能工具绑定。 |
| `public CompletionStage<Boolean> register_skill_tools(List<SkillToolBinding> bindings)` | `registerSkillTools(List<SkillToolBinding>)` 的 snake_case 兼容入口。 |
| `public CompletionStage<List<java.nio.file.Path>> registerRemoteSkills(String skillsDir, GitHubTree githubTree, String token)` | 根据 `GitHubTree` 注册远端技能，并返回落地文件路径列表。 |
| `public CompletionStage<List<java.nio.file.Path>> register_remote_skills(String skillsDir, GitHubTree githubTree, String token)` | `registerRemoteSkills(...)` 的 snake_case 兼容入口。 |
| `public CompletionStage<BaseAgent> registerCallback(AgentCallbackEvent event, AgentCallback callback, int priority)` | 为指定事件注册全局回调。 |
| `public CompletionStage<BaseAgent> register_callback(AgentCallbackEvent event, AgentCallback callback, int priority)` | `registerCallback(...)` 的 snake_case 兼容入口。 |
| `public CompletionStage<BaseAgent> registerRail(AgentRail rail)` | 注册全局 rail 实例，接入全局 Runner callback framework。 |
| `public CompletionStage<BaseAgent> register_rail(AgentRail rail)` | `registerRail(...)` 的 snake_case 兼容入口。 |
| `public CompletionStage<BaseAgent> unregisterRail(AgentRail rail)` | 注销全局 rail 实例，并从全局 Runner callback framework 移除其回调。 |
| `public CompletionStage<BaseAgent> unregister_rail(AgentRail rail)` | `unregisterRail(...)` 的 snake_case 兼容入口。 |
| `public CompletionStage<BaseAgent> registerInstanceRail(AgentRail rail)` | 注册实例级 rail，仅对当前 `BaseAgent` 对象实例生效，不进入全局 Runner。 |
| `public CompletionStage<BaseAgent> unregisterInstanceRail(AgentRail rail)` | 注销实例级 rail，仅移除当前 `BaseAgent` 对象实例上的回调并结束该 rail 在当前 Agent 上的生命周期，不影响全局 Runner。 |
| `public CompletionStage<Void> executeCallbacks(AgentCallbackEvent event, Object inputs, AgentSessionApi session, ModelContext context)` | 创建回调上下文并依次触发指定事件的全局与实例回调。 |
| `public CompletionStage<Void> _execute_callbacks(AgentCallbackEvent event, Map<String, Object> inputs, AgentSessionApi session, ModelContext context)` | `executeCallbacks(...)` 的 snake_case 兼容入口。 |
| `public CompletionStage<Object> invoke(Object inputs, AgentSessionApi session)` | 接受 `AgentSessionApi` 参数的异步非流式调用入口。 |
| `public Iterator<Object> stream(Object inputs, AgentSessionApi session, List<StreamMode> streamModes)` | 接受 `AgentSessionApi` 参数的流式调用入口，返回结果迭代器。 |
| `public CompletionStage<Object> invoke(Map<?, ?> inputs, Session session)` | 使用 Map 输入和 `Session` 执行异步非流式调用。 |
| `public CompletionStage<Object> invoke(String inputs, Session session)` | 使用字符串输入和 `Session` 执行异步非流式调用。 |
| `public Object invoke(Object inputs, Session session)` | `Session` 兼容入口；基类默认抛出 `UnsupportedOperationException`，子类可覆盖。 |
| `public Iterator<Object> stream(Object inputs, Session session, List<StreamMode> streamModes)` | `Session` 流式兼容入口；基类默认返回单次 `invoke` 结果的迭代器，子类可覆盖。 |
| `public BaseAgent activateSkill(String skillName, AgentSessionApi session)` | 在指定 Session 中激活已注册技能并返回当前对象。 |
| `public BaseAgent deactivateSkill(String skillName, AgentSessionApi session)` | 在指定 Session 中停用技能并返回当前对象。 |
| `public List<String> getActiveSkillNames(AgentSessionApi session)` | 返回指定 Session 当前激活的技能名称。 |
| `public List<ToolInfo> listEffectiveToolInfo(AgentSessionApi session)` | 返回全局能力与当前激活技能合并后的有效工具信息。 |
| `public Optional<Tool> findActiveSkillTool(String toolName, AgentSessionApi session)` | 在当前激活技能中按名称查找工具。 |
| `public Optional<String> findSkillNameByDocumentPath(String documentPath)` | 根据技能文档路径查找技能名称。 |
| `public AgentCard getCard()` | 返回当前 agent 的 `AgentCard`。 |
| `public Object getConfig()` | 返回当前配置对象。 |
| `public AbilityManager getAbilityManager()` | 返回能力管理器。 |
| `public AbilityManager get_ability_manager()` | `getAbilityManager()` 的 snake_case 兼容入口。 |
| `public void setAbilityManager(AbilityManager abilityManager)` | 设置能力管理器；传入 `null` 时使用新的空管理器。 |
| `public AgentCallbackManager getAgentCallbackManager()` | 返回回调管理器。 |
| `public AgentCallbackManager get_agent_callback_manager()` | `getAgentCallbackManager()` 的 snake_case 兼容入口。 |
| `public SkillUtil getSkillUtil()` | 返回技能工具对象。 |

## 说明

- 相关测试：`ReActAgentEvolveTest`、`ReActAgentTest`、`BaseAgentTest`、`ControllerAgentTest`。
- 设计上要求 `AgentCard` 必填、配置对象可选，并约定配置接口支持链式调用。
- `registerRail` / `unregisterRail` 保持全局 Runner 级语义；`registerInstanceRail` / `unregisterInstanceRail` 只绑定当前 Java `BaseAgent` 对象实例。即使两个 Agent 使用相同 card id，它们的实例级 rail 也互不影响。
