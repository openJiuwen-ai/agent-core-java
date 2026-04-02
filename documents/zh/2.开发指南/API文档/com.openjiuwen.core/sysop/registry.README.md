# registry

`com.openjiuwen.core.sysop.registry` 提供操作注册元数据、实例化定义和按运行模式查询操作实现的注册中心。

## 类型

| Type | Description |
| --- | --- |
| [`Operation`](registry/Operation.md) | 标注系统操作实现类的运行模式、名称和说明。 |
| [`OperationDef`](registry/OperationDef.md) | 保存操作类、模式、名称、说明并负责实例化。 |
| [`OperationRegistry`](registry/OperationRegistry.md) | 管理操作注册、内建操作加载、按模式查询和枚举支持列表。 |

## 说明

- 注册中心按 `OperationMode` 区分本地和沙箱两套操作命名空间。
- `CustomOperationExtensionTest` 与 `OperationRegistryTest` 验证了内建操作共存、自定义注册和按模式查询行为。
