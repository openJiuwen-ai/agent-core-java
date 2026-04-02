# com.openjiuwen.core.foundation.store.vector_fields.VectorField

## class VectorField

```java
public abstract class VectorField
```

Base class for configuring Approximate Nearest Neighbor (ANN) search in vector databases.

## Fields

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `STAGE_SEARCH` | `static final String` | `"search"` | Stage search. |
| `STAGE_CONSTRUCT` | `static final String` | `"construct"` | Stage construct. |
| `vectorField` | `String` | `"embedding"` | Vector field. |

## Constructors

| Signature | Description |
| --- | --- |
| `protected VectorField()` | Create a new `VectorField` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public String getVectorField()` | Return the vector field. |
| `public void setVectorField(String vectorField)` | Set the vector field. |
| `public abstract String getDatabaseType()` | Return the database type. |
| `public abstract String getIndexType()` | Return the index type. |
| `public abstract Map<String, Object> toDict(String stage)` | Convert the vector field configuration to a dictionary for a specific stage. |
| `protected Map<String, Object> finalizeDict(Map<String, Object> result, String stage)` | Merge extra params into the result map and remove internal keys. |
