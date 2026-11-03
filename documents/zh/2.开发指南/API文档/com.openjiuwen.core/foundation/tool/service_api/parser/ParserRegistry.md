# com.openjiuwen.core.foundation.tool.service_api.ParserRegistry

## class ParserRegistry

```java
public final class ParserRegistry
```

响应解析注册中心。它统一管理解析器与解压缩器，并按响应头自动完成解压和内容解析。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `instance` | `ParserRegistry` | `null` | 单例实例。 |
| `parsers` | `List<BaseResponseParser>` | `new ArrayList<>()` | 已注册解析器列表。 |
| `decompressors` | `Map<String, BaseResponseDecompressor>` | `new LinkedHashMap<>()` | 已注册解压缩器。 |

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public static ParserRegistry getInstance()` | 返回线程安全懒加载单例。 |
| `public void register(BaseResponseParser parser)` | 注册响应解析器。 |
| `public void registerDecompressor(String encoding, BaseResponseDecompressor decompressor)` | 注册指定编码的解压缩器。 |
| `public Object parse(Map<String, String> responseHeaders, byte[] responseData, int statusCode)` | 先按 `Content-Encoding` 解压，再按 `Content-Type` 选择解析器。 |

## 使用说明

- 本类通过私有构造器与 `getInstance()` 组合实现单例，不提供公开构造方法。
- 默认解析器注册顺序为 JSON 再文本，先匹配者生效。
- 头名会在内部统一转为小写后参与匹配。
- 当找不到可用解析器时会抛出 `IllegalArgumentException`。

## 相关测试

- `ResponseParserTest`
