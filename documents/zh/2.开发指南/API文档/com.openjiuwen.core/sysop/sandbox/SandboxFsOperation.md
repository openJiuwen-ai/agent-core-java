# com.openjiuwen.core.sysop.sandbox.SandboxFsOperation

## 类 SandboxFsOperation

```java
public class SandboxFsOperation extends BaseFsOperation
```

沙箱文件系统操作的占位实现，当前尚未实现。

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public SandboxFsOperation(Object runConfig)` | 创建 `SandboxFsOperation` 实例。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public ReadFileResult readFile(String path, String mode, Integer head, Integer tail, int[] lineRange, String encoding, int chunkSize, Map<String, Object> options)` | 读取文件；当前实现直接抛出 `UnsupportedOperationException`。 |
| `public Iterator<ReadFileStreamResult> readFileStream(String path, String mode, Integer head, Integer tail, int[] lineRange, String encoding, int chunkSize, Map<String, Object> options)` | 以流式方式读取文件；当前实现直接抛出 `UnsupportedOperationException`。 |
| `public WriteFileResult writeFile(String path, Object content, String mode, boolean prependNewline, boolean appendNewline, boolean createIfNotExist, String permissions, String encoding, Map<String, Object> options)` | 写入文件；当前实现直接抛出 `UnsupportedOperationException`。 |
| `public UploadFileResult uploadFile(String localPath, String targetPath, boolean overwrite, boolean createParentDirs, boolean preservePermissions, int chunkSize, Map<String, Object> options)` | 上传文件；当前实现直接抛出 `UnsupportedOperationException`。 |
| `public Iterator<UploadFileStreamResult> uploadFileStream(String localPath, String targetPath, boolean overwrite, boolean createParentDirs, boolean preservePermissions, int chunkSize, Map<String, Object> options)` | 以流式方式上传文件；当前实现直接抛出 `UnsupportedOperationException`。 |
| `public DownloadFileResult downloadFile(String sourcePath, String localPath, boolean overwrite, boolean createParentDirs, boolean preservePermissions, int chunkSize, Map<String, Object> options)` | 下载文件；当前实现直接抛出 `UnsupportedOperationException`。 |
| `public Iterator<DownloadFileStreamResult> downloadFileStream(String sourcePath, String localPath, boolean overwrite, boolean createParentDirs, boolean preservePermissions, int chunkSize, Map<String, Object> options)` | 以流式方式下载文件；当前实现直接抛出 `UnsupportedOperationException`。 |
| `public ListFilesResult listFiles(String path, boolean recursive, Integer maxDepth, String sortBy, boolean sortDescending, List<String> fileTypes, Map<String, Object> options)` | 列出文件；当前实现直接抛出 `UnsupportedOperationException`。 |
| `public ListDirsResult listDirectories(String path, boolean recursive, Integer maxDepth, String sortBy, boolean sortDescending, Map<String, Object> options)` | 列出目录；当前实现直接抛出 `UnsupportedOperationException`。 |
| `public SearchFilesResult searchFiles(String path, String pattern, List<String> excludePatterns)` | 搜索文件；当前实现直接抛出 `UnsupportedOperationException`。 |
| `public List<ToolCard> listTools()` | 返回可暴露给模型的文件工具，不包含 `upload_file`、`upload_file_stream`、`download_file`、`download_file_stream`。 |

## 说明

- 沙箱模式下，upload/download 的 `localPath` 指向 Agent 宿主机而不是沙箱，交给模型会导致可读写任意宿主机文件、绕过沙箱隔离，因此这四个方法不作为工具暴露；业务代码仍可直接调用对应的 Java 方法，宿主机路径由调用方自行控制。
- `SandboxOperationTest` 验证了代表性沙箱操作方法当前会抛出 `UnsupportedOperationException`。

## 相关测试

- `SandboxOperationTest`
- `SandboxFsOperationTest`
