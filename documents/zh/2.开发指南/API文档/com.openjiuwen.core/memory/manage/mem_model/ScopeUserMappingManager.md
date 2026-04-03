# com.openjiuwen.core.memory.manage.mem_model.ScopeUserMappingManager

## 类 ScopeUserMappingManager

```java
public class ScopeUserMappingManager
```

`ScopeUserMappingManager` 是 `com.openjiuwen.core.memory.manage.mem_model` 包下的公开类型，文档按 Java 源码列出其公开成员与签名。

## 字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `sqlDb` | `SqlDbStore` | 字段 `sqlDb`。 |
| `META_TABLE` | `String` | 字段 `META_TABLE`。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public ScopeUserMappingManager(SqlDbStore sqlDb)` | 创建 `ScopeUserMappingManager` 实例。 |

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public void add(String userId, String scopeId)` | 执行 `add` 写入流程。 |
| `public boolean deleteByScopeId(String scopeId)` | 执行 `deleteByScopeId` 删除流程。 |
| `public List<Map<String, Object>> getByScopeId(String scopeId)` | 返回 `getByScopeId` 的执行结果。 |
