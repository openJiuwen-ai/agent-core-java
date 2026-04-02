# com.openjiuwen.core.foundation.store.vector_fields.MilvusVectorField

## class MilvusVectorField

```java
public abstract class MilvusVectorField extends VectorField
```

Base class for Milvus vector field configurations.

## Methods

| Signature | Description |
| --- | --- |
| `public String getDatabaseType()` | Return the database type. |
| `protected static String validateSqConstruct(Map<String, Object> extraConstruct)` | Validate scalar quantization (SQ) options for index construction. |
| `protected static String validateSqSearch(Map<String, Object> extraSearch)` | Validate scalar quantization (SQ) options for search stage. |
| `protected static String validatePqConstruct(Map<String, Object> extraConstruct)` | Validate product quantization (PQ) options for index construction. |
