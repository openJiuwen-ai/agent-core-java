# com.openjiuwen.core.foundation.store.object.LocalObjectStorageClient

## class LocalObjectStorageClient

```java
public class LocalObjectStorageClient extends BaseObjectStorageClient
```

Local-filesystem implementation of the object storage contract.

## Fields

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `rootDirectory` | `final Path` | `-` | Root directory. |

## Constructors

| Signature | Description |
| --- | --- |
| `public LocalObjectStorageClient(Path rootDirectory)` | Create a new `LocalObjectStorageClient` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public boolean uploadFile(String bucketName, String objectName, Path filePath) throws Exception` | Execute `uploadFile`. |
| `public boolean downloadFile(String bucketName, String objectName, Path filePath) throws Exception` | Execute `downloadFile`. |
| `public boolean deleteObject(String bucketName, String objectName) throws Exception` | Delete the requested resource. |
| `public boolean createBucket(String bucketName, String location) throws Exception` | Create the requested resource. |
| `public boolean deleteBucket(String bucketName) throws Exception` | Delete the requested resource. |
| `public List<Map<String, Object>> listObjects(String bucketName, String objectPrefix, int maxObjects) throws Exception` | List the available values. |
| `private Path resolveBucketPath(String bucketName)` | Execute `resolveBucketPath`. |
| `private Path resolveObjectPath(String bucketName, String objectName)` | Execute `resolveObjectPath`. |
