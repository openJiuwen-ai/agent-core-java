# com.openjiuwen.core.foundation.store.vector_fields.MilvusHNSW

## class MilvusHNSW

```java
public class MilvusHNSW extends MilvusVectorField
```

Hierarchical Navigable Small World (HNSW) index configuration for Milvus.

## Fields

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `m` | `int` | `30` | M. |
| `efConstruction` | `int` | `360` | Ef construction. |
| `efSearchFactor` | `Float` | `-` | Ef search factor. |
| `variant` | `String` | `-` | Variant. |
| `extraConstruct` | `Map<String, Object>` | `new HashMap<>()` | Extra construct. |
| `extraSearch` | `Map<String, Object>` | `new HashMap<>()` | Extra search. |

## Methods

| Signature | Description |
| --- | --- |
| `public String getIndexType()` | Return the index type. |
| `public int getM()` | Return the m. |
| `public void setM(int m)` | Set the m. |
| `public int getEfConstruction()` | Return the ef construction. |
| `public void setEfConstruction(int efConstruction)` | Set the ef construction. |
| `public Float getEfSearchFactor()` | Return the ef search factor. |
| `public void setEfSearchFactor(Float efSearchFactor)` | Set the ef search factor. |
| `public String getVariant()` | Return the variant. |
| `public void setVariant(String variant)` | Set the variant. |
| `public Map<String, Object> getExtraConstruct()` | Return the extra construct. |
| `public void setExtraConstruct(Map<String, Object> extraConstruct)` | Set the extra construct. |
| `public Map<String, Object> getExtraSearch()` | Return the extra search. |
| `public void setExtraSearch(Map<String, Object> extraSearch)` | Set the extra search. |
| `public void validate()` | Validate extra_construct and extra_search parameters based on variant. |
| `public Map<String, Object> toDict(String stage)` | Execute `toDict`. |
