# com.openjiuwen.core.retrieval.common.BaseCallback

## class BaseCallback

```java
public class BaseCallback
```

Base callback for indexing and embedding progress.

## Fields

| Field | Type | Description |
| --- | --- | --- |
| `total` | `final int` | total. |

## Constructors

| Signature | Description |
| --- | --- |
| `public BaseCallback()` | Create a new `BaseCallback` instance. |
| `public BaseCallback(Collection<?> sequence)` | Create a new `BaseCallback` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public void onBatch(int startIdx, int endIdx, List<String> batch)` | Execute `onBatch`. |
| `public int getCallCounter()` | Return the call counter. |
| `public int getTotal()` | Return the total. |

## Notes

- Related tests: `InMemoryIndexerTest.java`, `MilvusIndexerTest.java`.
