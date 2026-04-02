# com.openjiuwen.core.retrieval.common.TqdmCallback

## class TqdmCallback

```java
public class TqdmCallback extends BaseCallback
```

Lightweight progress callback aligned with Python's TqdmCallback.

## Fields

| Field | Type | Description |
| --- | --- | --- |
| `length` | `final int` | length. |
| `desc` | `final String` | desc. |

## Constructors

| Signature | Description |
| --- | --- |
| `public TqdmCallback(Collection<?> sequence)` | Create a new `TqdmCallback` instance. |
| `public TqdmCallback(Collection<?> sequence, String desc)` | Create a new `TqdmCallback` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public void onBatch(int startIdx, int endIdx, List<String> batch)` | Execute `onBatch`. |
| `public int length()` | Execute `length`. |
| `public String getDesc()` | Return the desc. |

## Notes

- Related tests: `APIEmbeddingTest.java`.
