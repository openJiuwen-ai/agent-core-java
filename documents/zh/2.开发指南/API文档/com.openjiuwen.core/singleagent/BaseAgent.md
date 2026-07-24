# com.openjiuwen.core.singleagent.BaseAgent

## 抽象类 BaseAgent

```java
public abstract class BaseAgent implements AgentCallbackFirer
```

单智能体实现的抽象基类。

## 方法

| 签名 | 说明 |
|---|---|
| `public abstract BaseAgent configure(Object config)` | 设置运行配置并返回自身。 |
| `public abstract Object getConfig()` | 返回当前配置对象。 |
| `public AgentCard getCard()` | 返回当前 agent 的 `AgentCard`。 |
| `public AbilityManager getAbilityManager()` | 返回能力管理器。 |
| `public AgentCallbackManager getAgentCallbackManager()` | 返回回调管理器。 |
| `public SkillUtil getSkillUtil()` | 返回技能工具对象。 |
| `public void registerSkill(Object skillPath)` | 从本地路径注册技能。 |
| `public void registerSkill(Object skillPath, Path skillsRoot)` | 校验真实路径位于受信技能根目录后注册技能。 |
| `public void registerRemoteSkills(String skillsDir, GitHubTree githubTree, String token)` | 根据 `GitHubTree` 注册远端技能。 |
| `public BaseAgent registerCallback(AgentCallbackEvent event, Consumer<AgentCallbackContext> callback, int priority)` | 为指定事件注册回调。 |
| `public BaseAgent registerRail(AgentRail rail)` | 注册 rail 实例。 |
| `public BaseAgent unregisterRail(AgentRail rail)` | 注销 rail 实例。 |
| `@Override public void fireCallbackEvent(AgentCallbackEvent event, AgentCallbackContext ctx)` | 触发指定事件的全部回调。 |
| `public abstract Object invoke(Object inputs, Session session)` | 执行一次非流式调用。 |
| `public abstract Iterator<Object> stream(Object inputs, Session session, List<StreamMode> streamModes)` | 执行流式调用并返回结果迭代器。 |

## 说明

- 相关测试：`ReActAgentEvolveTest`、`ReActAgentTest`、`BaseAgentTest`、`ControllerAgentTest`。
- 设计上要求 `AgentCard` 必填、配置对象可选，并约定配置接口支持链式调用。
