# com.openjiuwen.core.memory.manage.mem_model.ScopeUserMappingManager

## class ScopeUserMappingManager

```java
public class ScopeUserMappingManager
```

Manages scope-user mapping records in the SQL database.

## Fields

| Field | Type | Description |
| --- | --- | --- |
| `sqlDb` | `SqlDbStore` | sql db. |
| `META_TABLE` | `String` | meta table. |

## Constructors

| Signature | Description |
| --- | --- |
| `public ScopeUserMappingManager(SqlDbStore sqlDb)` | Create a new `ScopeUserMappingManager` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public void add(String userId, String scopeId)` | Execute `add`. |
| `public boolean deleteByScopeId(String scopeId)` | Execute `deleteByScopeId`. |
| `public List<Map<String, Object>> getByScopeId(String scopeId)` | Execute `getByScopeId`. |
