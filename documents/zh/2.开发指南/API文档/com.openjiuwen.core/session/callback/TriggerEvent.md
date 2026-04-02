# com.openjiuwen.core.session.callback.TriggerEvent

## 注解 TriggerEvent

```java
public @interface TriggerEvent
```

用于标记处理器中的某个方法可以被 `CallbackManager` 识别为可触发事件。

## 元注解

| 元注解 | 说明 |
| --- | --- |
| `@Retention(RetentionPolicy.RUNTIME)` | 允许在运行时通过反射读取该注解。 |
| `@Target(ElementType.METHOD)` | 只能标注在方法上。 |
