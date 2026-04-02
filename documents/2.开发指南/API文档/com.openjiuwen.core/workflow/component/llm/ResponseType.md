# com.openjiuwen.core.workflow.component.llm.ResponseType

## enum ResponseType

```java
public enum ResponseType
```

Questioner 响应类型枚举。

当前 Java 实现只支持 `REPLY_DIRECTLY` 一种模式，对应字符串值 `reply_directly`；`isValid(...)` 用于配置校验阶段判断输入是否合法。

## Enum Constants

| Value | Description |
| --- | --- |
| `REPLY_DIRECTLY` | 直接回复并按需继续追问。 |

## Methods

| Signature | Description |
| --- | --- |
| `public String getValue()` | Return the value. |
| `public static boolean isValid(String value)` | Report whether valid. |
