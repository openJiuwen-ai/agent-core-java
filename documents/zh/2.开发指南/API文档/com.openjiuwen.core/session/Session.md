# com.openjiuwen.core.session.Session

## 接口 Session

```java
public interface Session
```

`ContextEngine` 等调用方依赖的最小会话接口，只保留会话标识、状态读写和当前 operator 标识能力。

## 方法

| 签名 | 说明 |
| --- | --- |
| `String getSessionId()` | 返回唯一会话标识。 |
| `Object getState(String key)` | 按键读取状态值。 |
| `void updateState(Map<String, Object> state)` | 把给定状态片段合并回会话存储。 |
| `default void setCurrentOperatorId(String operatorId)` | 设置当前 operator 标识；默认实现为空。 |
| `default String getCurrentOperatorId()` | 返回当前 operator 标识；默认实现返回 `null`。 |

## 说明

- 相关测试：`AgentSessionApiTest`、`SessionBasicTest`、`SessionTest`、`SessionUtilsTest`、`WorkflowInteractionTest`。
- 源码中的默认方法说明了该接口只约束最小能力，更完整的 session 子系统由具体实现类扩展提供。
