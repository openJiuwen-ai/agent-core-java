# com.openjiuwen.core.foundation.store.graph.GraphStoreFactory

## class GraphStoreFactory

```java
public final class GraphStoreFactory
```

图存储后端工厂，负责注册后端并按配置实例化 `GraphStore`。

## 构造说明

- 构造方法为私有，且调用时会抛出 `UnsupportedOperationException`。

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public static void registerBackend(String name, Class<? extends GraphStore> backend, boolean force)` | 注册图存储后端。 |
| `public static void registerBackend(String name, Class<? extends GraphStore> backend)` | 以 `force = false` 注册后端。 |
| `public static GraphStore fromConfig(GraphConfig config, String backendName)` | 根据后端名称和配置创建图存储实例。 |
| `public static GraphStore fromConfig(GraphConfig config)` | 使用 `config.getBackend()` 创建图存储实例。 |

## 使用说明

- 默认已注册 `in_memory -> InMemoryGraphStore`。
- `fromConfig` 通过反射调用目标后端的静态 `fromConfig(GraphConfig)` 方法。
- 当后端名称为空白或未注册时会抛出异常。
