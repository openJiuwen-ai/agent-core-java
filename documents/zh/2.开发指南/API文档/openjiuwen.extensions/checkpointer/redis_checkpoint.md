# openjiuwen.extensions.checkpointer Redis Checkpoint

Java 对应包：`com.openjiuwen.extensions.checkpointer.redis`

Redis Checkpoint 用于将 Agent、Agent Team、Workflow 和 Graph 运行态保存到 Redis，并在后续执行中恢复。

## 配置结构

Redis Checkpoint 使用 `RedisCheckpointer.Provider` 从配置 Map 创建实例。配置中必须包含 `connection`，可选包含 `ttl` 和 `dump_type`。

示例：

```java
Map<String, Object> config = Map.of(
        "connection", Map.of(
                "redis_client", redisClient
        ),
        "ttl", Map.of(
                "default_ttl", 30,
                "refresh_on_read", true
        ),
        "dump_type", "json"
);

Checkpointer checkpointer = new RedisCheckpointer.Provider().create(config);
```

## connection

`connection` 描述 Redis 连接来源：

- `redis_client`：调用方传入的 Redis 客户端对象。
- `url`：连接地址。
- `cluster_mode`：是否按 Redis Cluster 模式处理。
- `connection_args`：连接参数。

当 `redis_client` 是 `redis.clients.jedis.JedisCluster` 时，Provider 会使用 `JedisClusterRedisStore` 适配 Redis Cluster。

## JedisClusterRedisStore

`JedisClusterRedisStore` 支持 Redis Cluster 场景下的基础 KV 能力，包括：

- `set`
- `exclusiveSet`
- `get`
- `exists`
- `delete`
- `getByPrefix`
- `deleteByPrefix`
- `mget`
- `batchDelete`
- `pipeline`
- `refreshTtl`

由于 Redis Cluster 存在跨 slot 限制，批量删除按 key 逐个执行，以保证行为正确。

## dump_type

`dump_type` 控制 checkpoint payload 的序列化协议。当前支持：

- `java`：默认值，使用 Java 对象序列化，适合 Java 进程内恢复。
- `json`：使用 JSON 协议序列化，适合需要可读 checkpoint 或跨语言检查的场景。

未配置时默认使用 `java`。

```json
{
  "connection": {
    "url": "redis://localhost:6379"
  },
  "dump_type": "json"
}
```

## JSON checkpoint 协议

`dump_type=json` 时，Serializer 会对以下运行态对象写入类型标识并支持恢复：

- `UserMessage`
- `AssistantMessage`
- `SystemMessage`
- `ToolMessage`
- `GraphStoreState`
- `Message`
- `PendingNode`

JSON payload 中保留内部字段 `__jiuwenType` 用于标识对象类型。调用方自定义 Map 中不要使用该字段名。

## 兼容性说明

当前 0.1.14 支持读取 Redis 中标记为 `java` 或 `json` 的 checkpoint。旧数据如果使用 `pickle` 作为 dump type 标记，当前实现不会直接恢复，需要先清理旧 checkpoint 或做一次迁移。

如果同一 Redis 中同时存在新旧版本 checkpoint，建议按 session 前缀隔离，避免旧协议数据被新版运行时读取。

## ttl

`ttl` 用于控制 checkpoint 的过期时间和读取刷新策略：

- `default_ttl`：默认过期时间，单位为分钟。
- `refresh_on_read`：读取 checkpoint 后是否刷新 TTL。

示例：

```json
{
  "ttl": {
    "default_ttl": 60,
    "refresh_on_read": true
  }
}
```

开启 `refresh_on_read` 后，恢复 checkpoint 成功时会刷新相关 Redis key 的过期时间。
