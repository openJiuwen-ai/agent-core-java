# com.openjiuwen.core.runner.resourcemanager.AbstractManager

## 类 AbstractManager

```java
public abstract class AbstractManager<T>
```

`AbstractManager` 是基于 provider 注册机制的通用资源管理器基类。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `providers` | `ConcurrentHashMap<String, Supplier<? extends T>>` | `new ConcurrentHashMap<>()` | - |
