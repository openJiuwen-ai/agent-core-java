# com.openjiuwen.core.foundation.tool.service_api.parser.BaseResponseDecompressor

## class BaseResponseDecompressor

```java
public abstract class BaseResponseDecompressor
```

Base class for response decompressors.

## Methods

| Signature | Description |
| --- | --- |
| `public abstract boolean canDecompress(String encoding)` | Check if this decompressor supports the given content encoding. |
| `public abstract byte[] decompress(byte[] responseData) throws java.io.IOException` | Decompress the response data. |
