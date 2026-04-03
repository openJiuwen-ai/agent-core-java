# update

`com.openjiuwen.core.memory.manage.update` 提供记忆增量更新判定能力，负责描述检查结果、动作项与状态枚举，并通过 `MemUpdateChecker` 调用模型判断冗余或冲突。

## 核心类型

| 类型 | 说明 |
| --- | --- |
| [`CheckResult`](./update/CheckResult.md) | 检查结果枚举。 |
| [`MemCheckItem`](./update/MemCheckItem.md) | 单条检查结果对象。 |
| [`MemoryActionItem`](./update/MemoryActionItem.md) | 记忆更新动作对象。 |
| [`MemoryStatus`](./update/MemoryStatus.md) | 动作状态枚举。 |
| [`MemUpdateChecker`](./update/MemUpdateChecker.md) | 基于提示词和模型输出判断记忆冗余与冲突。 |

## 相关测试

- `MemUpdateCheckerTest`
