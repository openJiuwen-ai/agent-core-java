# com.openjiuwen.core.retrieval.common.RRFRankConfig

## class RRFRankConfig

```java
public class RRFRankConfig extends BaseRankConfig
```

RRF ranker configuration.

## Fields

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `k` | `int` | `40` | k. |
| `denseName` | `boolean` | `true` | dense name. |
| `denseContent` | `boolean` | `true` | dense content. |
| `sparseContent` | `boolean` | `true` | sparse content. |

## Constructors

| Signature | Description |
| --- | --- |
| `public RRFRankConfig()` | Create a new `RRFRankConfig` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public RankerArguments getArgs()` | Return the args. |
| `public List<Integer> isActive()` | Return whether active. |
| `public int getK()` | Return the k. |
| `public void setK(int k)` | Update the k. |
| `public void setDenseName(boolean denseName)` | Update the dense name. |
| `public boolean isDenseContent()` | Return whether dense content. |
| `public void setDenseContent(boolean denseContent)` | Update the dense content. |
| `public boolean isSparseContent()` | Return whether sparse content. |
| `public void setSparseContent(boolean sparseContent)` | Update the sparse content. |

## Notes

- Related tests: `ConfigTest.java`, `PGVectorStoreTest.java`.
