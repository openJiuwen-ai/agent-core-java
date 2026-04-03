# com.openjiuwen.core.foundation.tool.service_api.parser.DeflateDecompressor

## class DeflateDecompressor

```java
public class DeflateDecompressor extends BaseResponseDecompressor
```

`deflate` 解压缩实现。

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public boolean canDecompress(String encoding)` | 仅在 `encoding` 忽略大小写等于 `deflate` 时返回 `true`。 |
| `public byte[] decompress(byte[] responseData) throws IOException` | 先按标准 zlib 头解压，失败后再回退到 raw deflate 模式。 |

## 相关测试

- `ResponseParserTest`
