# schema

`com.openjiuwen.core.common.schema` 定义框架内部常用的轻量数据模型，主要用于卡片对象、参数定义与内容片段表达。

## 核心类型

| 类型 | 说明 |
| --- | --- |
| [`BaseCard`](./schema/BaseCard.md) | 卡片类对象的基础父类，提供标识、名称、描述、复制与扩展钩子。 |
| [`Param`](./schema/Param.md) | 不可变参数定义模型，支持标量、数组和对象三类结构。 |
| [`ParamType`](./schema/ParamType.md) | 参数类型枚举。 |
| [`Part`](./schema/Part.md) | 表示内容片段的简单 DTO。 |

## 说明

- `BaseCard` 与 `Part` 依赖 Lombok 生成构造器、访问器与构建器等样板代码。
- `Param` 会在创建时校验 `ARRAY`/`OBJECT` 的结构约束，非法组合会立即抛出异常。
