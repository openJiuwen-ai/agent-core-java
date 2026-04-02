# com.openjiuwen.core.foundation.store.vector_fields.MilvusSCANN

## class MilvusSCANN

```java
public class MilvusSCANN extends MilvusVectorField
```

SCANN (Scalable Nearest Neighbors) index configuration for Milvus.

## Fields

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `nlist` | `int` | `128` | Nlist. |
| `nprobe` | `int` | `8` | Nprobe. |
| `withRawData` | `boolean` | `true` | With raw data. |
| `reorderK` | `Integer` | `-` | Reorder k. |

## Methods

| Signature | Description |
| --- | --- |
| `public String getIndexType()` | Return the index type. |
| `public int getNlist()` | Return the nlist. |
| `public void setNlist(int nlist)` | Set the nlist. |
| `public int getNprobe()` | Return the nprobe. |
| `public void setNprobe(int nprobe)` | Set the nprobe. |
| `public boolean isWithRawData()` | Return whether the with raw data is enabled. |
| `public void setWithRawData(boolean withRawData)` | Set the with raw data. |
| `public Integer getReorderK()` | Return the reorder k. |
| `public void setReorderK(Integer reorderK)` | Set the reorder k. |
| `public Map<String, Object> toDict(String stage)` | Execute `toDict`. |
