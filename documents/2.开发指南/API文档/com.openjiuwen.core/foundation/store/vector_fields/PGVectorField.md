# com.openjiuwen.core.foundation.store.vector_fields.PGVectorField

## class PGVectorField

```java
public class PGVectorField extends VectorField
```

Index configuration for PGVector database.

## Fields

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `indexType` | `String` | `"hnsw"` | Index type. |
| `m` | `int` | `16` | M. |
| `efConstruction` | `int` | `64` | Ef construction. |
| `efSearch` | `int` | `40` | Ef search. |
| `lists` | `int` | `100` | Lists. |
| `probes` | `int` | `1` | Probes. |
| `extraSearch` | `Map<String, Object>` | `new HashMap<>()` | Extra search. |

## Methods

| Signature | Description |
| --- | --- |
| `public String getDatabaseType()` | Return the database type. |
| `public String getIndexType()` | Return the index type. |
| `public void setIndexType(String indexType)` | Set the index type. |
| `public int getM()` | Return the m. |
| `public void setM(int m)` | Set the m. |
| `public int getEfConstruction()` | Return the ef construction. |
| `public void setEfConstruction(int efConstruction)` | Set the ef construction. |
| `public int getEfSearch()` | Return the ef search. |
| `public void setEfSearch(int efSearch)` | Set the ef search. |
| `public int getLists()` | Return the lists. |
| `public void setLists(int lists)` | Set the lists. |
| `public int getProbes()` | Return the probes. |
| `public void setProbes(int probes)` | Set the probes. |
| `public Map<String, Object> getExtraSearch()` | Return the extra search. |
| `public void setExtraSearch(Map<String, Object> extraSearch)` | Set the extra search. |
| `public Map<String, Object> toDict(String stage)` | Execute `toDict`. |
