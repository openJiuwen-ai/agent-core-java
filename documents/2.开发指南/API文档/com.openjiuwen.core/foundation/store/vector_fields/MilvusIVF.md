# com.openjiuwen.core.foundation.store.vector_fields.MilvusIVF

## class MilvusIVF

```java
public class MilvusIVF extends MilvusVectorField
```

Inverted File (IVF) index configuration for Milvus.

## Fields

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `nlist` | `int` | `128` | Nlist. |
| `nprobe` | `int` | `8` | Nprobe. |
| `variant` | `String` | `"FLAT"` | Variant. |
| `extraConstruct` | `Map<String, Object>` | `new HashMap<>()` | Extra construct. |
| `extraSearch` | `Map<String, Object>` | `new HashMap<>()` | Extra search. |

## Methods

| Signature | Description |
| --- | --- |
| `public String getIndexType()` | Return the index type. |
| `public int getNlist()` | Return the nlist. |
| `public void setNlist(int nlist)` | Set the nlist. |
| `public int getNprobe()` | Return the nprobe. |
| `public void setNprobe(int nprobe)` | Set the nprobe. |
| `public String getVariant()` | Return the variant. |
| `public void setVariant(String variant)` | Set the variant. |
| `public Map<String, Object> getExtraConstruct()` | Return the extra construct. |
| `public void setExtraConstruct(Map<String, Object> extraConstruct)` | Set the extra construct. |
| `public Map<String, Object> getExtraSearch()` | Return the extra search. |
| `public void setExtraSearch(Map<String, Object> extraSearch)` | Set the extra search. |
| `public void validate()` | Validate extra_construct and extra_search parameters based on variant. |
| `public Map<String, Object> toDict(String stage)` | Execute `toDict`. |
