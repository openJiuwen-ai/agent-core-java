# com.openjiuwen.core.retrieval.common.LoggingCallback

## class LoggingCallback

```java
public class LoggingCallback extends BaseCallback
```

Simple SLF4J-backed callback for batch progress.

## Fields

| Field | Type | Description |
| --- | --- | --- |
| `total` | `final int` | total. |
| `desc` | `final String` | desc. |

## Constructors

| Signature | Description |
| --- | --- |
| `public LoggingCallback(int total, String desc)` | Create a new `LoggingCallback` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public void onBatch(int startIdx, int endIdx, List<String> batch)` | Execute `onBatch`. |
