# com.openjiuwen.core.sysop.BaseFsOperation

## 类 BaseFsOperation

```java
public abstract class BaseFsOperation extends BaseOperation
```

`BaseFsOperation` 定义了文件读取、写入、上传、下载、目录列举和搜索的统一抽象，是本地实现与沙箱实现共享的文件系统公开接口约定。

## 方法

| 签名 | 说明 |
| --- | --- |
| `public List<ToolCard> listTools()` | 返回读取、写入、上传、下载、列举和搜索文件所需的标准工具卡片。 |
| `public abstract ReadFileResult readFile( String path, String mode, Integer head, Integer tail, int[] lineRange, String encoding, int chunkSize, Map<String, Object> options)` | 读取文件内容，支持文本/字节模式以及 `head`、`tail`、`lineRange` 过滤。 |
| `public abstract Iterator<ReadFileStreamResult> readFileStream( String path, String mode, Integer head, Integer tail, int[] lineRange, String encoding, int chunkSize, Map<String, Object> options)` | 以流式方式读取文件内容。 |
| `public abstract WriteFileResult writeFile( String path, Object content, String mode, boolean prependNewline, boolean appendNewline, boolean createIfNotExist, String permissions, String encoding, Map<String, Object> options)` | 写入文本或字节内容，并支持换行、自动建文件和权限设置。 |
| `public abstract UploadFileResult uploadFile( String localPath, String targetPath, boolean overwrite, boolean createParentDirs, boolean preservePermissions, int chunkSize, Map<String, Object> options)` | 将本地文件上传到目标路径。 |
| `public abstract Iterator<UploadFileStreamResult> uploadFileStream( String localPath, String targetPath, boolean overwrite, boolean createParentDirs, boolean preservePermissions, int chunkSize, Map<String, Object> options)` | 以流式 chunk 形式上传文件。 |
| `public abstract DownloadFileResult downloadFile( String sourcePath, String localPath, boolean overwrite, boolean createParentDirs, boolean preservePermissions, int chunkSize, Map<String, Object> options)` | 将目标路径文件下载到本地路径。 |
| `public abstract Iterator<DownloadFileStreamResult> downloadFileStream( String sourcePath, String localPath, boolean overwrite, boolean createParentDirs, boolean preservePermissions, int chunkSize, Map<String, Object> options)` | 以流式 chunk 形式下载文件。 |
| `public abstract ListFilesResult listFiles( String path, boolean recursive, Integer maxDepth, String sortBy, boolean sortDescending, List<String> fileTypes, Map<String, Object> options)` | 列出目录中的文件，支持递归、深度、排序和扩展名过滤。 |
| `public abstract ListDirsResult listDirectories( String path, boolean recursive, Integer maxDepth, String sortBy, boolean sortDescending, Map<String, Object> options)` | 列出目录中的子目录，支持递归、深度和排序。 |
| `public abstract SearchFilesResult searchFiles( String path, String pattern, List<String> excludePatterns)` | 按 glob 模式搜索文件，并支持排除模式。 |

## 相关测试

- `LocalFsOperationTest`
