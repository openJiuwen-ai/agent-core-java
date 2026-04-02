# com.openjiuwen.core.session.config.Config

## 类 Config

```java
public class Config
```

会话配置对象，负责加载环境变量、保存工作流配置和 agent 配置，并维护回调元数据。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `WORKFLOW_SESSION_VARS` | `ThreadLocal<Map<String, Object>>` | `ThreadLocal.withInitial(HashMap::new)` | 工作流级线程本地环境变量覆盖表；若存在同名键，会覆盖系统环境变量读取结果。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public Config()` | 创建配置对象并立即加载内置环境配置。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public void setEnvs(Map<String, Object> envs)` | 批量写入环境变量映射。 |
| `public Object getEnv(String key, Object defaultValue)` | 按键读取环境变量；缺失时返回 `defaultValue`。 |
| `public Object getEnv(String key)` | 按键读取环境变量；缺失时返回 `null`。 |
| `public Map<String, Object> getEnvs()` | 返回环境变量副本。 |
| `public Object getWorkflowConfig(String workflowId)` | 按工作流 ID 读取工作流配置；`workflowId = null` 时抛出异常。 |
| `public Object getAgentConfig()` | 返回 agent 配置对象。 |
| `public void setAgentConfig(Object agentConfig)` | 覆盖 agent 配置对象。 |
| `public void addWorkflowConfig(String workflowId, Object workflowConfig)` | 注册一条工作流配置；参数为空时抛出异常。 |
| `public Map<String, MetadataLike> getCallbackMetadata()` | 返回回调元数据映射。 |

## 嵌套类型

| 签名 | 说明 |
| --- | --- |
| `public static class MetadataLike` | 用于回调注册的元数据结构。 |

## 说明

- 相关测试：`SessionBasicTest`。
- 构造时会先装入内置默认值，再尝试读取系统环境变量和 `WORKFLOW_SESSION_VARS` 中的同名覆盖项。
- `MetadataLike` 提供三组公开 getter/setter，用来承载回调元数据中的标识、名称和事件名。
