# com.openjiuwen.core.graph.store.Serializer

## 抽象类 Serializer

```java
public abstract class Serializer
```

图状态持久化使用的抽象序列化器基类，并提供按类型名创建实现的工厂方法。

## 方法

| 签名 | 说明 |
| --- | --- |
| `public abstract TypedBytes dumpsTyped(Object obj)` | 将对象序列化为带类型标签的字节表示。 |
| `public abstract Object loadsTyped(TypedBytes data)` | 根据 `TypedBytes.type()` 反序列化对象。 |
| `public static Serializer create(String typeName)` | 根据 `typeName` 创建序列化器；支持 `"json"` 和 `"java"`，其他值会抛出 `IllegalArgumentException`。 |

## 嵌套公共类型

| 类型 | 签名 | 说明 |
| --- | --- | --- |
| `TypedBytes` | `public record TypedBytes(String type, byte[] data)` | 承载类型标签和原始字节数据的 record。 |
| `JsonSerializer` | `public static class JsonSerializer extends Serializer` | 使用 Jackson 读写 JSON，仅接受 `type == "json"` 的输入。 |
| `JavaNativeSerializer` | `public static class JavaNativeSerializer extends Serializer` | 使用 Java 原生序列化流读写对象，仅接受 `type == "java"` 的输入。 |
