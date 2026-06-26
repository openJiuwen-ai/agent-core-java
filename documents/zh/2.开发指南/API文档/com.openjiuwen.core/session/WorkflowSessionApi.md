# com.openjiuwen.core.session.WorkflowSessionApi

## 类 WorkflowSessionApi

```java
public class WorkflowSessionApi
```

面向工作流执行的对外会话门面，负责聚合会话标识、环境变量、回调管理器与工作流卡片。

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public WorkflowSessionApi(BaseSession parent, String sessionId, Map<String, Object> envs)` | 基于父级 `BaseSession` 创建工作流会话；父级存在时优先继承其环境变量。 |
| `public WorkflowSessionApi(BaseSession parent, String sessionId)` | - |
| `public WorkflowSessionApi(String sessionId)` | - |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public CallbackManager getCallbackManager()` | 返回当前工作流会话自带的 `CallbackManager`。 |
| `public String getSessionId()` | 返回工作流会话 ID。 |
| `public Map<String, Object> getEnvs()` | 返回当前环境变量快照。 |
| `public BaseSession getParent()` | 返回父级 `BaseSession`；无父级时为 `null`。 |
| `public void setWorkflowCard(Object card)` | 设置当前工作流卡片对象。 |
| `public Object getWorkflowCard()` | 返回当前工作流卡片对象。 |
| `public static WorkflowSessionApi create(BaseSession parent, String sessionId, Map<String, Object> envs)` | 以静态工厂方式创建工作流会话。 |

## 说明

- 当 `parent != null` 时，源码会优先使用 `parent.config().getEnvs()` 作为环境变量来源；只有无父级时才直接采用传入的 `envs`。
