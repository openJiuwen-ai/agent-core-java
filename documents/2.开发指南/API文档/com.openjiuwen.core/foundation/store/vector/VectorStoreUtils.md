# com.openjiuwen.core.foundation.store.vector.VectorStoreUtils

## class VectorStoreUtils

```java
public final class VectorStoreUtils
```

Conversion functions for Vector Store distance/similarity scores to normalized similarity [0, 1], and schema migration helpers.

## Fields

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `TYPE_MAPPING` | `static final Map<String, VectorDataType>` | `Map.ofEntries( Map.entry("string", VectorDataType.VARCHAR), Map.entry("str", VectorDataType.VARCHAR), Map.entry("varchar", VectorDataType.VARCHAR), Map.entry("int", VectorDataType.INT32), Map.entry("integer", VectorDataType.INT32), Map.entry("int32", VectorDataType.INT32), Map.entry("int64", VectorDataType.INT64), Map.entry("long", VectorDataType.INT64), Map.entry("float", VectorDataType.FLOAT), Map.entry("float32", VectorDataType.FLOAT), Map.entry("double", VectorDataType.DOUBLE), Map.entry("float64", VectorDataType.DOUBLE), Map.entry("bool", VectorDataType.BOOL), Map.entry("boolean", VectorDataType.BOOL), Map.entry("json", VectorDataType.JSON), Map.entry("vector", VectorDataType.FLOAT_VECTOR), Map.entry("float_vector", VectorDataType.FLOAT_VECTOR) )` | Type mapping. |

## Constructors

| Signature | Description |
| --- | --- |
| `private VectorStoreUtils()` | Create a new `VectorStoreUtils` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public static double convertL2Squared(double rawScore, double maxDist)` | Convert squared L2 distance to normalized similarity in [0, 1]. |
| `public static double convertL2Squared(double rawScore)` | Execute `convertL2Squared`. |
| `public static double convertCosineSimilarity(double rawScore)` | Convert cosine similarity to normalized similarity in [0, 1]. |
| `public static double convertCosineDistance(double rawScore)` | Convert cosine distance to normalized cosine similarity in [0, 1]. |
| `public static double convertIpSimilarity(double rawScore)` | Convert raw inner product to normalized similarity in [0, 1]. |
| `public static double convertIpDistance(double rawScore)` | Convert inner product distance form to normalized similarity in [0, 1]. |
| `public static VectorDataType mapStringToVectorDataType(String typeStr)` | Map a string type name to VectorDataType. |
| `public static CollectionSchema computeNewSchema(CollectionSchema oldSchema, List<?> operations)` | Compute the final schema after applying all operations. |
| `private static CollectionSchema applySchemaOperation(CollectionSchema schema, Object operation)` | Execute `applySchemaOperation`. |
| `private static CollectionSchema applyMapOperation(CollectionSchema schema, Map<String, Object> opMap)` | Execute `applyMapOperation`. |
| `private static CollectionSchema applyReflectiveOperation(CollectionSchema schema, Object operation, String className)` | Execute `applyReflectiveOperation`. |
| `private static void renameFieldInList(List<Map<String, Object>> fields, String oldName, String newName)` | Execute `renameFieldInList`. |
| `private static void updateFieldTypeInList(List<Map<String, Object>> fields, String fieldName, String newFieldType)` | Update the requested state. |
| `private static void updateVectorDimInList(List<Map<String, Object>> fields, String fieldName, int newDim)` | Update the requested state. |
| `public static Function<Map<String, Object>, Map<String, Object>> buildTransformFuncForOperations( List<?> operations)` | Build a unified transform function that applies all operations to a document. |
| `private static void applyOperationToDoc(Map<String, Object> doc, Object operation)` | Execute `applyOperationToDoc`. |
| `private static void applyMapOperationToDoc(Map<String, Object> doc, Map<String, Object> opMap)` | Execute `applyMapOperationToDoc`. |
