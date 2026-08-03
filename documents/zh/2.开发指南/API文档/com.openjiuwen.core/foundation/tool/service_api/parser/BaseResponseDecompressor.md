# com.openjiuwen.core.foundation.tool.service_api.BaseResponseDecompressor

## class BaseResponseDecompressor

```java
public abstract class BaseResponseDecompressor
```

响应解压缩器抽象基类。

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public abstract boolean canDecompress(String encoding)` | 判断当前解压缩器是否支持给定 `Content-Encoding`。 |
| `public abstract byte[] decompress(byte[] responseData) throws IOException` | 对响应字节数组执行解压。 |
