# com.openjiuwen.core.sysop.FsConstants

## 类 FsConstants

```java
public final class FsConstants
```

`FsConstants` 收集文件系统操作使用的默认块大小，覆盖普通读取、流式读取、上传、下载以及尾部读取场景。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `DEFAULT_READ_CHUNK_SIZE` | `int` | `0` | 普通读取的默认块大小；`0` 表示不限大小。 |
| `DEFAULT_UPLOAD_CHUNK_SIZE` | `int` | `0` | 普通上传的默认块大小；`0` 表示按整文件复制。 |
| `DEFAULT_DOWNLOAD_CHUNK_SIZE` | `int` | `0` | 普通下载的默认块大小；`0` 表示按整文件复制。 |
| `DEFAULT_DOWNLOAD_STREAM_CHUNK_SIZE` | `int` | `1024 * 1024` | 流式下载时每个 chunk 的默认大小。 |
| `DEFAULT_UPLOAD_STREAM_CHUNK_SIZE` | `int` | `1024 * 1024` | 流式上传时每个 chunk 的默认大小。 |
| `DEFAULT_READ_STREAM_CHUNK_SIZE` | `int` | `8192` | 流式读取文件时的默认块大小。 |
| `TAIL_CHUNK_SIZE` | `int` | `1024` | 处理尾部读取时使用的块大小。 |
