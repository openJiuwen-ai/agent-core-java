# com.openjiuwen.core.workflow.component.ComponentAbility

## 枚举 ComponentAbility

```java
public enum ComponentAbility
```

`ComponentAbility` 定义组件支持的四种执行能力。

## 枚举值

| 值 | 说明 |
| --- | --- |
| `INVOKE` | 普通同步执行。 |
| `STREAM` | 流式输出。 |
| `COLLECT` | 汇聚流输入后输出。 |
| `TRANSFORM` | 流输入流输出。 |
