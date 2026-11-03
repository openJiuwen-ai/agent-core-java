# com.openjiuwen.core.single_agent.rail.RetryRequest

## 类 RetryRequest

```java
public class RetryRequest
```

由异常 rail 产生的重试指令。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `delaySeconds` | `double` | `0.0` | 下一次重试前需要等待的秒数。 |

## 说明

- 相关测试：`DataClassCoverageTest`、`AgentCallbackContextTest`、`RailDataClassesTest`。
