# migration

`com.openjiuwen.core.memory.migration` 提供记忆存储的迁移注册表与统一执行入口，覆盖 SQL、向量库与 KV 存储三类后端。

## 子包

| 子包 | 说明 |
| --- | --- |
| [`migrator`](./migration/migrator.README.md) | 提供面向不同后端的迁移执行器与版本记录辅助类。 |
| [`operation`](./migration/operation.README.md) | 定义迁移操作元数据、注册表以及具体的模式变更操作对象。 |

## 核心类型

| 类型 | 说明 |
| --- | --- |
| [`MigrationPlan`](./migration/MigrationPlan.md) | 维护 SQL、向量库与 KV 三套全局 `OperationRegistry`。 |
| [`RunMigrations`](./migration/RunMigrations.md) | 读取 `MigrationPlan` 中已注册的操作并依次执行迁移。 |

## 关键行为

- `MigrationPlan` 只负责注册与查询，不直接操作存储。
- `RunMigrations` 会按实体键逐个取出操作列表；一旦某个实体迁移失败，对应方法立即返回 `false`。
- `MigrationPlanTest` 验证了注册表的版本递增约束、范围查询以及快照恢复行为。
