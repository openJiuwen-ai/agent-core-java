# com.openjiuwen.core.runner.drunner.dmessage_queue.MessageSerializer

## 类 MessageSerializer

```java
public final class MessageSerializer
```

`MessageSerializer` 负责分布式请求/响应消息及其嵌套负载的 JSON 序列化与反序列化。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `MAX_RECURSE_DEPTH` | `int` | `10` | 负载递归序列化/反序列化的最大深度，避免无限嵌套。 |
| `MAPPER` | `ObjectMapper` | `-` | 内部使用的 Jackson 序列化器，注册了 datetime 相关转换。 |
| `TYPE_REGISTRY` | `Map<String, Function<Map<String, Object>, Object>>` | `new ConcurrentHashMap<>()` | 按 `__class__` 标记注册自定义反序列化工厂。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public static void registerType(String className, Function<Map<String, Object>, Object> deserializer)` | 注册自定义类型反序列化器；当负载里出现 `__class__` 标记时，会调用该函数重建对象。 |
| `public static void unregisterType(String className)` | 注销已注册的自定义类型。 |
| `public static Map<String, Function<Map<String, Object>, Object>> getTypeRegistry()` | 返回当前类型注册表的只读副本。 |
| `public static byte[] serializeMessage(DmqMessage message) throws Exception` | 将请求或响应消息编码成 JSON 字节数组，并写入公共元数据。 |
| `public static DmqMessage deserializeMessage(byte[] bytes) throws Exception` | 从 JSON 字节数组恢复 `DmqRequestMessage` 或 `DmqResponseMessage`，并递归还原 `body`。 |
