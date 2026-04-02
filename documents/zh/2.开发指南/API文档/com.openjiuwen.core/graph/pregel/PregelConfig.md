# com.openjiuwen.core.graph.pregel.PregelConfig

## 类 PregelConfig

```java
public class PregelConfig
```

Pregel 图执行配置对象。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `sessionId` | `String` | `-` | 当前执行关联的会话 ID。 |
| `recursionLimit` | `int` | `-` | 允许的最大 super-step 数。 |
| `ns` | `String` | `-` | 当前执行命名空间。 |
| `parentNs` | `String` | `-` | 父级命名空间。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public PregelConfig()` | 创建默认 `PregelConfig`，其 `recursionLimit` 取 `PregelConstants.MAX_RECURSIVE_LIMIT`。 |
| `public PregelConfig(String sessionId, String ns, int recursionLimit)` | 基于会话 ID、命名空间与递归上限创建 `PregelConfig`。 |
| `public static final PregelConfig DEFAULT = new PregelConfig(null, null, PregelConstants.MAX_RECURSIVE_LIMIT)` | 默认 Pregel 配置实例。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public String getSessionId()` | 返回当前 `sessionId`。 |
| `public void setSessionId(String sessionId)` | 更新 `sessionId`。 |
| `public int getRecursionLimit()` | 返回当前 `recursionLimit`。 |
| `public void setRecursionLimit(int recursionLimit)` | 更新 `recursionLimit`。 |
| `public String getNs()` | 返回当前 `ns`。 |
| `public void setNs(String ns)` | 更新 `ns`。 |
| `public String getParentNs()` | 返回当前 `parentNs`。 |
| `public void setParentNs(String parentNs)` | 更新 `parentNs`。 |
| `public Object get(String key)` | 以字典式 key 访问配置值，兼容 `session_id`、`ns`、`parent_ns`、`recursion_limit`。 |
| `public Map<String, Object> toMap()` | 将当前配置转换为映射表示。 |
| `public static PregelConfig createInnerConfig(PregelConfig config)` | 基于输入配置构造内部执行配置，并在缺省时补齐默认值。 |

## 相关测试

- `CompiledGraphTest`
- `PregelTest`
- `TaskExecutorPoolTest`
