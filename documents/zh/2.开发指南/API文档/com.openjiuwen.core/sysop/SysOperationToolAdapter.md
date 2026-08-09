# com.openjiuwen.core.sysop.SysOperationToolAdapter

## 类 SysOperationToolAdapter

```java
public final class SysOperationToolAdapter
```

`SysOperationToolAdapter` 负责把 `SysOperation` 中的操作方法转换为 `LocalFunction` 工具，并根据目标方法签名对输入 `Map<String, Object>` 做基础类型转换。

## 方法

| 签名 | 说明 |
| --- | --- |
| `public static List<ToolEntry> extractTools(SysOperationCard card, SysOperation instance)` | 遍历当前模式支持的操作，把工具卡片和目标方法封装为可注册的 `LocalFunction` 列表。 |
| `public static String getToolIdPrefix(String sysOperationId)` | 返回单个系统操作卡片的工具 ID 前缀，格式为 `{sysOperationId}.`。 |
| `public static List<String> getToolIdPrefix(List<String> sysOperationIds)` | 批量返回多个系统操作卡片的工具 ID 前缀。 |

## 说明

- 源码内部使用 `ToolEntry` record 把工具 ID 与对应 `LocalFunction` 配对返回。
- 适配器会按目标参数类型对字符串、数字、布尔、`List`、`Map`、`Iterator` 和 `int[]` 做基础转换。
- 当输入缺失且目标参数是原始类型时，会自动填入该原始类型的默认值。

## 相关测试

- `SysOperationToolAdapterTest`
