# com.openjiuwen.core.session.checkpointer.PersistenceCheckpointerProvider

## 类 PersistenceCheckpointerProvider

```java
public class PersistenceCheckpointerProvider implements CheckpointerProvider
```

`PersistenceCheckpointerProvider` 负责从配置中读取 `kv_store`，并在可用时创建 `PersistenceCheckpointer`。

## 方法

| 签名 | 说明 |
| --- | --- |
| `public Checkpointer create(Map<String, Object> conf)` | 当 `conf.get("kv_store")` 是 `BaseKVStore` 时返回 `PersistenceCheckpointer`，否则回退到 `InMemoryCheckpointer`。 |

## 说明

- 当前源码只识别 `kv_store`，不会根据 `db_type` 或 `db_path` 自动创建 `BaseKVStore`。
- 回退到内存实现的设计主要用于缺少持久化依赖的测试环境。
