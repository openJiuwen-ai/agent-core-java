# checkpointer

`com.openjiuwen.core.session.checkpointer` 提供会话检查点能力，包括生命周期钩子、实例工厂、Provider 接口以及状态存储抽象，用于在 agent 与 workflow 执行之间恢复、保存和清理状态。

## Types

| 类型 | 说明 |
| --- | --- |
| [`Checkpointer`](./checkpointer/Checkpointer.md) | 检查点抽象基类，定义 workflow、agent 与图状态的恢复、保存和释放入口。 |
| [`CheckpointerConfig`](./checkpointer/CheckpointerConfig.md) | `CheckpointerFactory` 使用的配置对象，包含 `type` 与 `conf`。 |
| [`CheckpointerFactory`](./checkpointer/CheckpointerFactory.md) | 检查点工厂与注册表，内置 `in_memory` 与 `persistence` 类型。 |
| [`CheckpointerProvider`](./checkpointer/CheckpointerProvider.md) | 按配置创建 `Checkpointer` 实例的 Provider 接口；可扩展自定义保存、恢复和数据治理实现。 |
| [`InMemoryCheckpointer`](./checkpointer/InMemoryCheckpointer.md) | 基于内存 `Map` 与 `InMemoryStore` 的检查点实现。 |
| [`PersistenceCheckpointer`](./checkpointer/PersistenceCheckpointer.md) | 基于 `BaseKVStore` 的持久化检查点实现。 |
| [`PersistenceCheckpointerProvider`](./checkpointer/PersistenceCheckpointerProvider.md) | 从 `kv_store` 配置构造持久化检查点；缺失时回退到内存实现。 |
| [`Storage`](./checkpointer/Storage.md) | agent / workflow 存储实现共用的抽象存储基类。 |

## 说明

- 相关测试：`InMemoryCheckpointerTest`。
