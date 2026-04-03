# com.openjiuwen.core.runner.mq.LocalMessageQueue

## 类 LocalMessageQueue

```java
public class LocalMessageQueue
```

`LocalMessageQueue` 是本地消息队列占位实现；`start()` 与 `stop()` 均直接返回 `true`，不提供额外队列能力。

## 方法

| 签名 | 说明 |
| --- | --- |
| `public boolean start()` | - |
| `public boolean stop()` | - |

## 相关测试

- `RunnerTest`
