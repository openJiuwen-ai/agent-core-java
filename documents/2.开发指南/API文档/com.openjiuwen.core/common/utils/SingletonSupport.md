# com.openjiuwen.core.common.utils.SingletonSupport

## class SingletonSupport

```java
public abstract class SingletonSupport<T>
```

`SingletonSupport` 为具体服务类提供线程安全的单例创建与重置能力。

## 字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `INSTANCES` | `ConcurrentHashMap<Class<?>, Object>` | 按具体类缓存单例对象。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public static <T> T getInstance(Class<T> clazz, java.util.function.Supplier<T> factory)` | 通过双重检查锁获取或创建指定类的单例实例。 |
| `public static void reset(Class<?> clazz)` | 移除指定类的已缓存实例，主要用于测试。 |

## 说明

- 每个具体 `Class<?>` 在 `INSTANCES` 中只保存一个对象，因此单例粒度是“每个具体类一个实例”。
