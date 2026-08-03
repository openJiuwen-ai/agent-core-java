# com.openjiuwen.core.sys_operation.SysOperationCard

## 类 SysOperationCard

```java
public class SysOperationCard extends BaseCard
```

`SysOperationCard` 描述系统操作卡片的元数据、运行模式以及本地/沙箱运行配置，并提供工具 ID 生成辅助方法。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `mode` | `OperationMode` | - | 系统操作运行模式，取值为 `LOCAL` 或 `SANDBOX`。 |
| `workConfig` | `LocalWorkConfig` | - | 本地模式使用的工作目录与命令白名单配置。 |
| `gatewayConfig` | `SandboxGatewayConfig` | - | 沙箱模式使用的网关连接配置。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public static OperationMode validateMode(String modeValue)` | 校验字符串模式值并转换为 `OperationMode`；非法值会抛出参数错误。 |
| `public ToolIdProxy fs()` | 返回文件系统操作对应的 `ToolIdProxy`。 |
| `public ToolIdProxy shell()` | 返回命令执行操作对应的 `ToolIdProxy`。 |
| `public ToolIdProxy code()` | 返回代码执行操作对应的 `ToolIdProxy`。 |
| `public ToolIdProxy proxy(String opType)` | 为自定义操作类型创建 `ToolIdProxy`。 |
| `public static String generateToolId(String cardId, String opType, String methodName)` | 生成 `{cardId}.{opType}.{methodName}` 形式的工具 ID。 |

## Lombok 说明

- 该类型使用 `Data`、`SuperBuilder`、`NoArgsConstructor`、`AllArgsConstructor`、`EqualsAndHashCode` 生成访问器、构建器和构造辅助方法。
- 文档中的字段表以源码显式声明的字段为准，Lombok 生成的方法不单独逐一展开。

## 相关测试

- `LocalCodeOperationTest`
- `LocalFsOperationTest`
- `LocalShellOperationTest`
- `SysOperationCardTest`
- `SysOperationTest`
