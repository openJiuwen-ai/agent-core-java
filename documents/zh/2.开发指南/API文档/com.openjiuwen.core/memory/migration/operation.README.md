# operation

`com.openjiuwen.core.memory.migration.operation` 定义迁移操作的公共抽象、注册容器以及 SQL、向量库、KV 三类后端可复用的具体操作对象。

## 核心类型

| 类型 | 说明 |
| --- | --- |
| [`BaseOperation`](./operation/BaseOperation.md) | 所有迁移操作的抽象基类，统一暴露版本号与描述。 |
| [`OperationMetadata`](./operation/OperationMetadata.md) | 封装 `schemaVersion` 与说明文本。 |
| [`OperationRegistry`](./operation/OperationRegistry.md) | 按实体键维护递增的迁移操作链。 |
| [`AddColumnOperation`](./operation/AddColumnOperation.md) | 新增 SQL 列。 |
| [`AddScalarFieldOperation`](./operation/AddScalarFieldOperation.md) | 为向量数据类型新增标量字段。 |
| [`RenameColumnOperation`](./operation/RenameColumnOperation.md) | 重命名 SQL 列。 |
| [`RenameScalarFieldOperation`](./operation/RenameScalarFieldOperation.md) | 重命名向量数据类型中的标量字段。 |
| [`UpdateColumnTypeOperation`](./operation/UpdateColumnTypeOperation.md) | 更新 SQL 列类型。 |
| [`UpdateEmbeddingDimensionOperation`](./operation/UpdateEmbeddingDimensionOperation.md) | 更新向量字段的 embedding 维度。 |
| [`UpdateKVOperation`](./operation/UpdateKVOperation.md) | 以 `Consumer<BaseKVStore>` 的形式定义 KV 更新逻辑。 |
| [`UpdateScalarFieldTypeOperation`](./operation/UpdateScalarFieldTypeOperation.md) | 更新向量标量字段类型。 |

## 关键行为

- `OperationRegistry.register(...)` 要求同一实体下的 `schemaVersion` 严格递增，否则抛出 `BaseError`。
- `BaseOperation.getDescription()` 会优先返回 `OperationMetadata.description`，为空时退回到类名。
- 具体迁移器会根据运行环境挑选可执行的 `BaseOperation` 子类，超出支持范围的操作会在运行期报错。
