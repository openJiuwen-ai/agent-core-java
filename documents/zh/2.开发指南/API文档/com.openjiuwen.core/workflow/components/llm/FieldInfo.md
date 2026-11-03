# com.openjiuwen.core.workflow.component.llm.FieldInfo

## class FieldInfo

```java
public class FieldInfo extends com.openjiuwen.core.workflow.component.llm.FieldInfo
```

`workflow.components.llm` 命名空间下的兼容包装类型。

它继承主包 `FieldInfo`，额外提供 `(fieldName, description, required)` 位置参数构造器，主要用于兼容旧测试与旧调用方式。

## Constructors

| Signature | Description |
| --- | --- |
| `public FieldInfo(String fieldName, String description, boolean required)` | Positional constructor: FieldInfo(fieldName, description, required). |
| `public FieldInfo()` | Create a new `FieldInfo` instance. |
