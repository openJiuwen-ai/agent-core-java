# com.openjiuwen.core.foundation.tool.service_api.GzipDecompressor

## class GzipDecompressor

```java
public class GzipDecompressor extends BaseResponseDecompressor
```

`gzip` 解压缩实现，同时兼容 `x-gzip`。

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public boolean canDecompress(String encoding)` | 在 `gzip` 或 `x-gzip` 场景返回 `true`，大小写不敏感。 |
| `public byte[] decompress(byte[] responseData) throws IOException` | 先按 GZIP 格式解压，失败后再尝试 raw deflate 回退逻辑。 |

## 相关测试

- `ResponseParserTest`
