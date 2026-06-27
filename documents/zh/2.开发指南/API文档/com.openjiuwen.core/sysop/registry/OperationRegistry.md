# com.openjiuwen.core.memory.migration.operation.OperationRegistry

## 类 OperationRegistry

```java
public final class OperationRegistry
```

`OperationRegistry` 管理系统操作定义的注册、查询和加载。它会按运行模式延迟装配内建操作，并在需要时扫描对应包下带 `@Operation` 的实现类。

## 方法

| 签名 | 说明 |
| --- | --- |
| `public static void register(Class<? extends BaseOperation> operationCls, String name, OperationMode mode, String description)` | 以显式名称、模式和说明注册一个操作实现类。 |
| `public static void register(Class<? extends BaseOperation> operationCls)` | 从类上的 `@Operation` 注解读取元数据并完成注册。 |
| `public static Optional<OperationDef> getOperationInfo(String name, OperationMode mode)` | 按操作名和运行模式查询操作定义；找不到时返回空值。 |
| `public static List<String> getSupportedOperations(OperationMode mode)` | 返回指定模式下支持的操作名称列表，并按字典序排序。 |

## 说明

- `LOCAL` 模式默认加载 `LocalShellOperation`、`LocalCodeOperation`、`LocalFsOperation`。
- `SANDBOX` 模式默认加载 `SandboxShellOperation`、`SandboxCodeOperation`、`SandboxFsOperation`。
- 已注册的内建操作会缓存在静态仓库中，后续查询不会重复扫描。

## 相关测试

- `CustomOperationExtensionTest`
- `OperationRegistryTest`
