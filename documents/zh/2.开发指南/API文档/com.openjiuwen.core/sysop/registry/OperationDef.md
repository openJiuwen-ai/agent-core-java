# com.openjiuwen.core.sysop.registry.OperationDef

## 类 OperationDef

```java
public class OperationDef
```

`OperationDef` 保存一个系统操作的类、名称、模式和说明，并负责按约定构造函数创建操作实例。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `cls` | `Class<? extends BaseOperation>` | - | 实际操作实现类。 |
| `description` | `String` | - | 操作说明文本。 |
| `name` | `String` | - | 操作名称。 |
| `mode` | `OperationMode` | - | 操作运行模式。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public OperationDef(Class<? extends BaseOperation> cls, String name, OperationMode mode, String description)` | 使用实现类、名称、模式和说明创建操作定义。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public BaseOperation createInstance(Object runConfig)` | 根据运行配置创建操作实例；优先尝试四参构造 `(String, OperationMode, String, Object)`，找不到时退回单参构造 `(Object)`。 |
| `public Class<? extends BaseOperation> getCls()` | 返回实现类。 |
| `public String getDescription()` | 返回说明文本。 |
| `public String getName()` | 返回操作名称。 |
| `public OperationMode getMode()` | 返回运行模式。 |
| `public boolean equals(Object o)` | 判断两个 `OperationDef` 是否在类、名称、模式和说明上完全一致。 |
| `public int hashCode()` | 返回与 `equals` 对齐的哈希值。 |

## 相关测试

- `CustomOperationExtensionTest`
- `OperationRegistryTest`
