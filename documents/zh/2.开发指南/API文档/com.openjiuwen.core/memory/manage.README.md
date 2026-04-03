# manage

`com.openjiuwen.core.memory.manage` 汇总长期记忆引擎的管理层能力，按职责拆分为 `index`、`mem_model`、`search`、`update` 四个子包，分别负责写入协调、底层数据模型、检索参数与增量更新判定。

## 子包

| 子包 | 说明 |
| --- | --- |
| [`index`](./manage/index.README.md) | 提供分片记忆、摘要记忆、变量记忆与统一写入协调器。 |
| [`mem_model`](./manage/mem_model.README.md) | 定义 KV/SQL/向量存储适配器、数据模型与枚举类型。 |
| [`search`](./manage/search.README.md) | 提供记忆检索入口与检索参数模型。 |
| [`update`](./manage/update.README.md) | 提供基于模型判断的冗余/冲突检测与动作决策对象。 |

## 关键行为

- `LongTermMemory.setConfig(...)` 会在该层创建各类 manager，并组装 `WriteManager`、`SearchManager` 与 `Generator`。
- `LongTermMemory` 中的写入、更新、删除操作会通过 `DistributedLock` 串行化到按用户粒度的临界区。
- `MemUpdateCheckerTest`、`SearchManagerTest`、`SqlDbStoreTest` 等测试覆盖了管理层的核心流程。
