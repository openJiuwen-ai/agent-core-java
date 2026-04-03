# com.openjiuwen.core.memory.migration.operation.OperationRegistry

## 类 OperationRegistry

```java
public class OperationRegistry
```

`OperationRegistry` 按实体键维护迁移操作链，并保证同一实体下的版本号严格递增。

## 字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `operations` | `Map<String, List<BaseOperation>>` | 按实体键保存迁移操作列表的有序映射。 |

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public void register(String entityKey, BaseOperation op)` | 向指定实体追加一个新操作；若版本号不大于当前最大值则抛出 `BaseError`。 |
| `public List<BaseOperation> getOperations(String entityKey, int fromVersion, int toVersion)` | 返回给定版本闭区间内的操作列表；若 `fromVersion > toVersion` 则返回空列表。 |
| `public List<BaseOperation> getOperations(String entityKey)` | 返回指定实体的全部操作。 |
| `public int getCurrentVersion(String entityKey)` | 返回指定实体当前已注册的最大版本号；若无记录则返回 `0`。 |
| `public List<String> getAllEntities()` | 返回当前注册表中所有实体键。 |
| `public Map<String, List<BaseOperation>> getAllOperations()` | 返回当前操作映射的浅拷贝。 |
| `public void clear()` | 清空全部实体与操作。 |
| `public void setOperations(Map<String, List<BaseOperation>> ops)` | 用外部提供的映射整体替换当前注册表内容。 |

## 使用说明

- `MigrationPlanTest` 覆盖了 `register(...)` 的重复/降级版本校验，以及 `clear()`、`setOperations(...)` 的快照恢复流程。
