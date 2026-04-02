# com.openjiuwen.core.sysop.local.LocalFsOperation

## 类 LocalFsOperation

```java
public class LocalFsOperation extends BaseFsOperation
```

`LocalFsOperation` 基于 Java NIO 实现本地文件系统操作，并在配置了 `LocalWorkConfig.workDir` 时把目标路径限制在工作目录内，防止越界访问。

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public LocalFsOperation(Object runConfig)` | 使用给定运行配置创建本地文件系统操作。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public ReadFileResult readFile(String path, String mode, Integer head, Integer tail, int[] lineRange, String encoding, int chunkSize, Map<String, Object> options)` | 读取文件内容；支持文本/字节模式，并支持 `head`、`tail`、`lineRange` 过滤。 |
| `public Iterator<ReadFileStreamResult> readFileStream(String path, String mode, Integer head, Integer tail, int[] lineRange, String encoding, int chunkSize, Map<String, Object> options)` | 流式读取文件；文本模式按行返回，字节模式按块返回。 |
| `public WriteFileResult writeFile(String path, Object content, String mode, boolean prependNewline, boolean appendNewline, boolean createIfNotExist, String permissions, String encoding, Map<String, Object> options)` | 写入文本或字节内容，并支持自动建文件、前后补换行和 POSIX 权限设置。 |
| `public UploadFileResult uploadFile(String localPath, String targetPath, boolean overwrite, boolean createParentDirs, boolean preservePermissions, int chunkSize, Map<String, Object> options)` | 将本地绝对路径文件复制到工作目录中的目标路径。 |
| `public Iterator<UploadFileStreamResult> uploadFileStream(String localPath, String targetPath, boolean overwrite, boolean createParentDirs, boolean preservePermissions, int chunkSize, Map<String, Object> options)` | 以流式 chunk 形式把本地文件上传到目标路径。 |
| `public DownloadFileResult downloadFile(String sourcePath, String localPath, boolean overwrite, boolean createParentDirs, boolean preservePermissions, int chunkSize, Map<String, Object> options)` | 将工作目录中的源文件复制到本地路径。 |
| `public Iterator<DownloadFileStreamResult> downloadFileStream(String sourcePath, String localPath, boolean overwrite, boolean createParentDirs, boolean preservePermissions, int chunkSize, Map<String, Object> options)` | 以流式 chunk 形式把源文件下载到本地路径。 |
| `public ListFilesResult listFiles(String path, boolean recursive, Integer maxDepth, String sortBy, boolean sortDescending, List<String> fileTypes, Map<String, Object> options)` | 列出文件；支持递归、最大深度、排序和扩展名过滤。 |
| `public ListDirsResult listDirectories(String path, boolean recursive, Integer maxDepth, String sortBy, boolean sortDescending, Map<String, Object> options)` | 列出目录；支持递归、最大深度和排序。 |
| `public SearchFilesResult searchFiles(String path, String pattern, List<String> excludePatterns)` | 按 glob 模式搜索文件，并支持排除模式。 |

## 说明

- 文本模式下 `head`、`tail` 与 `lineRange` 互斥；字节模式禁止使用这些文本过滤参数。
- `head=0` 与 `tail=0` 会按“未传入”处理；非法行区间会返回空内容而不是抛异常。
- 当配置了 `workDir` 时，所有相对路径都会解析到该目录下，路径越界会返回访问拒绝错误。
- `LocalFsOperationTest` 覆盖了读写、上传下载、流式读取、路径越界、排序过滤和搜索场景。

## 相关测试

- `LocalFsOperationTest`
