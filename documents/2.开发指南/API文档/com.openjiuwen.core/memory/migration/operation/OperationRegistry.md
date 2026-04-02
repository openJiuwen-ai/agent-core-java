# com.openjiuwen.core.memory.migration.operation.OperationRegistry

## class OperationRegistry

```java
public class OperationRegistry
```

Registry that manages chained upgrade operations by entity_key.

## Fields

| Field | Type | Description |
| --- | --- | --- |
| `operations` | `Map<String, List<BaseOperation>>` | operations. |

## Methods

| Signature | Description |
| --- | --- |
| `public void register(String entityKey, BaseOperation op)` | Execute `register`. |
| `public List<BaseOperation> getOperations(String entityKey, int fromVersion, int toVersion)` | Execute `getOperations`. |
| `public List<BaseOperation> getOperations(String entityKey)` | Execute `getOperations`. |
| `public int getCurrentVersion(String entityKey)` | Execute `getCurrentVersion`. |
| `public List<String> getAllEntities()` | Execute `getAllEntities`. |
| `public Map<String, List<BaseOperation>> getAllOperations()` | Execute `getAllOperations`. |
| `public void clear()` | Execute `clear`. |
| `public void setOperations(Map<String, List<BaseOperation>> ops)` | Execute `setOperations`. |
