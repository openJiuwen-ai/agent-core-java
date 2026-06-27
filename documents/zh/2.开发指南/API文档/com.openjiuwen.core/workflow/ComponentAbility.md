# com.openjiuwen.core.workflow.component.ComponentAbility

## 枚举 ComponentAbility

```java
public enum ComponentAbility
```

该枚举是 `com.openjiuwen.core.workflow.component.ComponentAbility` 的顶层兼容导出，方便旧测试或旧调用方在 `workflow` 包下直接引用组件能力。

## 枚举值

| 值 | 说明 |
| --- | --- |
| `INVOKE` | 普通同步执行能力。 |
| `STREAM` | 流式输出能力。 |
| `COLLECT` | 聚合流输入后再输出的能力。 |
| `TRANSFORM` | 对流输入逐段转换的能力。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public com.openjiuwen.core.workflow.component.ComponentAbility toInternal()` | 转换为内部真实能力枚举。 |
| `public static ComponentAbility fromInternal(...)` | 从内部能力枚举回转为顶层兼容枚举。 |
