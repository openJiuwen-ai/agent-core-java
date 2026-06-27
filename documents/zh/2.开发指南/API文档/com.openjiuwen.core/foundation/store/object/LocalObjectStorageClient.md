# com.openjiuwen.core.foundation.store.object.LocalObjectStorageClient

## class LocalObjectStorageClient

```java
public class LocalObjectStorageClient extends BaseObjectStorageClient
```

基于本地文件系统的对象存储客户端。

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public LocalObjectStorageClient(Path rootDirectory)` | 指定对象存储根目录。 |

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public boolean uploadFile(String bucketName, String objectName, Path filePath)` | 上传本地文件到指定 bucket/object。 |
| `public boolean downloadFile(String bucketName, String objectName, Path filePath)` | 下载对象到目标路径。 |
| `public boolean deleteObject(String bucketName, String objectName)` | 删除指定对象。 |
| `public boolean createBucket(String bucketName, String location)` | 创建 bucket 目录。 |
| `public boolean deleteBucket(String bucketName)` | 删除 bucket 及其全部内容。 |
| `public List<Map<String, Object>> listObjects(String bucketName, String objectPrefix, int maxObjects)` | 列出 bucket 下的对象元信息。 |

## 使用说明

- `location` 参数当前未参与本地实现逻辑。
- `listObjects` 返回的对象信息包含 `bucket`、`object_name` 和 `size`。
