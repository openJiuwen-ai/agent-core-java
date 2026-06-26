# com.openjiuwen.core.session.tracer.TracerHandlerName

## 枚举 TracerHandlerName

```java
public enum TracerHandlerName
```

TracerHandlerName 定义回调系统中 tracer handler 的注册名。

## 方法

| 签名 | 说明 |
| --- | --- |
| `public String getValue()` | 返回 handler 名称字符串。 |

## 枚举值

| 枚举值 | 说明 |
| --- | --- |
| `TRACE_AGENT` | agent 级 trace handler。 |
| `TRACER_WORKFLOW` | workflow 级 trace handler。 |

## 说明

- `getValue()` 返回回调系统实际注册使用的 handler 名称字符串。
