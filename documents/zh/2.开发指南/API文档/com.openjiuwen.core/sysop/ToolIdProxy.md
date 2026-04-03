# com.openjiuwen.core.sysop.ToolIdProxy

## 类 ToolIdProxy

```java
public class ToolIdProxy
```

`ToolIdProxy` 是工具 ID 的便捷拼装器，用 `cardId`、操作类型和方法名生成统一的 `cardId.opType.methodName` 形式标识。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `cardId` | `String` | - | `SysOperationCard` 的 ID。 |
| `opType` | `String` | - | 当前代理对应的操作类型，例如 `fs`、`shell`、`code`。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public ToolIdProxy(String cardId, String opType)` | 使用卡片 ID 和操作类型创建代理。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public String toolId(String methodName)` | 按 `cardId.opType.methodName` 规则生成工具 ID。 |
| `public String getCardId()` | 返回当前代理绑定的卡片 ID。 |
| `public String getOpType()` | 返回当前代理绑定的操作类型。 |

## 相关测试

- `SysOperationCardTest`
- `ToolIdProxyTest`
