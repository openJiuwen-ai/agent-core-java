# com.openjiuwen.core.controller.legacy.reasoner.IntentDetector

## interface IntentDetector

```java
public interface IntentDetector
```

`IntentDetector` 是旧版意图检测接口。

## 方法

### `IntentDetectionController.Intent detect(Event event, Session session, ReasonerConfig config)`

根据事件、会话和 reasoner 配置返回一个旧版 `Intent`。

## 说明

- `AgentReasoner.detect()` 和 `DefaultIntentDetector` 都围绕该接口工作。
