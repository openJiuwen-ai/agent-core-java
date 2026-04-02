# com.openjiuwen.core.memory.migration.migrator.KvMigrator

## 类 KvMigrator

```java
public class KvMigrator
```

`KvMigrator` 负责执行 KV 存储迁移，并在迁移前创建备份、失败时回滚。

## 字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `KV_SCHEMA_VERSION` | `String` | KV 存储中记录全局 schema 版本号的键名。 |
| `KV_ENTITY_KEY` | `String` | 当前实现唯一支持的实体键，固定为 `kv_global`。 |
| `kvStore` | `BaseKVStore` | 实际执行读写、删除和按前缀查询的 KV 存储实例。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public KvMigrator(BaseKVStore kvStore)` | 使用给定 `BaseKVStore` 创建迁移器。 |

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public boolean tryMigrate(String entityKey, List<BaseOperation> operations)` | 校验实体键与操作顺序后执行待迁移操作；成功返回 `true`，失败时尝试从备份恢复并返回 `false`。 |

## 行为说明

- 当 `KV_SCHEMA_VERSION` 不存在且存储中也没有记忆模块数据时，`KvMigrator` 会把当前注册表版本写入存储，视为已初始化。
- 当前只支持 `UpdateKVOperation`；执行时会通过反射调用 `getUpdateFunc()` 并将 `BaseKVStore` 传给 `Consumer`。
- 备份数据使用 JSON 序列化后写入临时键，迁移成功后会清理备份键。
