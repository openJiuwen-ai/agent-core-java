# com.openjiuwen.core.memory.migration.migrator.VectorMigrator

## 类 VectorMigrator

```java
public class VectorMigrator
```

`VectorMigrator` 对向量集合执行 schema 迁移，并在成功后同步集合元数据中的版本号。

## 字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `MEMORY_LOGGER` | `LoggerProtocol` | 向量迁移流程使用的日志记录器。 |
| `semanticStore` | `SemanticStore` | 提供集合列表、元数据查询、schema 更新与元数据写回能力的向量存储封装。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public VectorMigrator(SemanticStore semanticStore)` | 使用指定的 `SemanticStore` 创建迁移器。 |

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public boolean tryMigrate(String entityKey, List<BaseOperation> operations)` | 查找匹配实体键的集合，对版本更高的操作执行 `updateSchema(...)`，并把最大版本号写回元数据。 |

## 行为说明

- 实体键支持带 `vector_` 前缀或直接使用记忆类型值，最终会按后缀匹配集合名。
- 若记忆类型不在 `SupportMemoryType` 枚举中，会抛出 `MEMORY_MIGRATE_MEMORY_EXECUTION_ERROR`。
- 若当前向量存储不支持 schema 更新，方法会记录错误并返回 `false`。
