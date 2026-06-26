# com.openjiuwen.core.memory.migration.operation.UpdateKVOperation

## 类 UpdateKVOperation

```java
public class UpdateKVOperation
```

`UpdateKVOperation` 用一个 `Consumer<BaseKVStore>` 表示 KV 存储上的自定义更新逻辑。

## 字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `updateFunc` | `Consumer<BaseKVStore>` | 接收 `BaseKVStore` 并执行更新的回调函数。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public UpdateKVOperation(OperationMetadata metadata, Consumer<BaseKVStore> updateFunc)` | 创建一条 KV 更新操作。 |

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public Consumer<BaseKVStore> getUpdateFunc()` | 返回实际执行 KV 更新的回调。 |

## 使用说明

- `KvMigrator` 运行该操作时会通过 `getUpdateFunc()` 取得回调并立即执行。
