# com.openjiuwen.core.foundation.store.vector_fields.ChromaVectorField

## class ChromaVectorField

```java
public class ChromaVectorField extends VectorField
```

HNSW index configuration for ChromaDB vector database.

## Fields

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `maxNeighbors` | `int` | `16` | Max neighbors. |
| `efConstruction` | `int` | `100` | Ef construction. |
| `efSearch` | `float` | `100` | Ef search. |
| `extraSearch` | `Map<String, Object>` | `new HashMap<>()` | Extra search. |

## Constructors

| Signature | Description |
| --- | --- |
| `public ChromaVectorField()` | Create a new `ChromaVectorField` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public String getDatabaseType()` | Return the database type. |
| `public String getIndexType()` | Return the index type. |
| `public int getMaxNeighbors()` | Return the max neighbors. |
| `public void setMaxNeighbors(int maxNeighbors)` | Set the max neighbors. |
| `public int getEfConstruction()` | Return the ef construction. |
| `public void setEfConstruction(int efConstruction)` | Set the ef construction. |
| `public float getEfSearch()` | Return the ef search. |
| `public void setEfSearch(float efSearch)` | Set the ef search. |
| `public Map<String, Object> getExtraSearch()` | Return the extra search. |
| `public void setExtraSearch(Map<String, Object> extraSearch)` | Set the extra search. |
| `private void validateExtraSearch(Map<String, Object> searchDict)` | Execute `validateExtraSearch`. |
| `public Map<String, Object> toDict(String stage)` | Execute `toDict`. |
