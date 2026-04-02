# com.openjiuwen.core.retrieval.common.WeightedRankConfig

## class WeightedRankConfig

```java
public class WeightedRankConfig extends BaseRankConfig
```

Weighted ranker configuration for dense/sparse fusion.

## Fields

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `denseName` | `double` | `0.15` | dense name. |
| `denseContent` | `double` | `0.6` | dense content. |
| `sparseContent` | `double` | `0.25` | sparse content. |

## Constructors

| Signature | Description |
| --- | --- |
| `public WeightedRankConfig()` | Create a new `WeightedRankConfig` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public RankerArguments getArgs()` | Return the args. |
| `public void setDenseName(double denseName)` | Update the dense name. |
| `public double getDenseContent()` | Return the dense content. |
| `public void setDenseContent(double denseContent)` | Update the dense content. |
| `public double getSparseContent()` | Return the sparse content. |
| `public void setSparseContent(double sparseContent)` | Update the sparse content. |

## Notes

- Related tests: `ConfigTest.java`, `PGVectorStoreTest.java`.
