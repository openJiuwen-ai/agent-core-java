# com.openjiuwen.core.session.checkpointer.CheckpointerFactory

## 类 CheckpointerFactory

```java
public final class CheckpointerFactory
```

`CheckpointerFactory` 负责注册检查点 Provider、按类型创建实例，以及维护默认检查点与按类型缓存的检查点实例。

## 方法

| 签名 | 说明 |
| --- | --- |
| `public static void register(String name, CheckpointerProvider provider)` | 为指定类型名注册 `CheckpointerProvider`。 |
| `public static Checkpointer create(CheckpointerConfig checkpointerConf)` | 根据 `CheckpointerConfig` 创建检查点；配置为 `null` 时抛出 `IllegalArgumentException`。 |
| `public static Checkpointer create(String type, Map<String, Object> conf)` | 按类型和配置创建检查点；未注册类型会抛出 `IllegalArgumentException`。 |
| `public static void setDefaultCheckpointer(Checkpointer checkpointer)` | 设置默认检查点实例。 |
| `public static void setCheckpointer(String storeType, Checkpointer checkpointer)` | 为某个类型直接绑定检查点实例。 |
| `public static Checkpointer getCheckpointer(String storeType)` | 按类型获取检查点；优先读取已绑定实例，其次对 `in_memory` 返回内置内存实现，再回退到默认实例。 |
| `public static Checkpointer getCheckpointer()` | 获取默认检查点。 |

## 说明

- 静态注册表内置 `in_memory`、`persistence`、`redis` 和 `redis_checkpointer_cluster`。
- 当前源码中 `redis` 与 `redis_checkpointer_cluster` 都回退到内置 `InMemoryCheckpointer`，便于测试环境运行。
