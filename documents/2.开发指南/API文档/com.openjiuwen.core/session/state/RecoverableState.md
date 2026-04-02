# com.openjiuwen.core.session.state.RecoverableState

## 接口 RecoverableState

```java
public interface RecoverableState
```

支持完整快照导出与恢复的状态接口。

## 方法

| 签名 | 说明 |
| --- | --- |
| `Map<String, Object> getState()` | 导出完整状态快照。 |
| `void setState(Map<String, Object> state)` | 从完整快照恢复状态。 |
