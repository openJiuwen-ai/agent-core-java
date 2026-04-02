# memory

`com.openjiuwen.core.memory` 提供长期记忆引擎的公开入口，负责组合存储注册、作用域配置、消息写入、变量维护、摘要检索，以及 `manage`、`common`、`config`、`prompt` 等子包能力。

## 子包

| 子包 | 说明 |
| --- | --- |
| [`common`](./memory/common.README.md) | 提供分布式锁、KV 前缀注册、加解密与命中结果解析等共享工具。 |
| [`config`](./memory/config.README.md) | 定义系统级、作用域级和代理级记忆配置模型。 |
| [`manage`](./memory/manage.README.md) | 汇总记忆管理器、底层存储模型、检索参数与更新判定逻辑。 |
| [`prompt`](./memory/prompt.README.md) | 提供记忆流程所需的提示词模板加载与变量替换能力。 |

## 核心类型

| 类型 | 说明 |
| --- | --- |
| [`LongTermMemory`](./memory/LongTermMemory.md) | 长期记忆主引擎，负责对外暴露配置、写入、检索和删除入口。 |
| [`MemInfo`](./memory/MemInfo.md) | 单条记忆的基础信息模型，封装 `memId`、正文与 `MemoryType`。 |
| [`MemResult`](./memory/MemResult.md) | 记忆检索结果模型，在 `MemInfo` 外补充相似度分数。 |

## 使用说明

- `LongTermMemory` 采用单例模式，测试可通过 `resetInstance()` 重置全局实例。
- `registerStore(...)` 会注册 KV、向量和 SQL 存储，并触发迁移流程。
- `LongTermMemoryTest` 覆盖了作用域配置持久化、默认时区时间戳与变量读取等关键行为。
