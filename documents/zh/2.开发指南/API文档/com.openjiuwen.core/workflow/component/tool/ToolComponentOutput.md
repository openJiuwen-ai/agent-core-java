# com.openjiuwen.core.workflow.component.tool.ToolComponentOutput

## 类 ToolComponentOutput

```java
public class ToolComponentOutput
```

Tool 组件输出模型，封装错误码、错误消息与工具返回数据。

## 字段

| 签名 | 说明 |
| --- | --- |
| `public static final String ERR_CODE = "errCode"` | 错误码字段键名。 |
| `public static final String ERR_MESSAGE = "errMessage"` | 错误消息字段键名。 |
| `public static final String RESTFUL_DATA = "data"` | 工具返回数据字段键名。 |
| `private int errorCode = 0` | 错误码。 |
| `private String errorMessage = ""` | 错误消息。 |
| `private Object data = ""` | 工具返回数据。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public java.util.Map<String, Object> toMap()` | 转换为 `Map` 表示。 |
| `public static ToolComponentOutput fromMap(java.util.Map<String, Object> map)` | 根据 `Map` 构造 `ToolComponentOutput` 实例。 |

## 说明

- 该类型使用 Lombok 生成部分访问器或构造方法，文档仅记录源码中显式定义的字段与方法。
