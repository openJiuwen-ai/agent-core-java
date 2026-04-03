# result

`com.openjiuwen.core.sysop.result` 提供 sysop 操作使用的统一结果封装、负载 DTO 与流式分块 DTO。

## 类型

| 类型 | 说明 |
| --- | --- |
| [`BaseResult`](result/BaseResult.md) | 所有 `sysop` 结果对象的公共基类。 |
| [`DownloadFileChunkData`](result/DownloadFileChunkData.md) | 下载文件分块返回的数据结构。 |
| [`DownloadFileData`](result/DownloadFileData.md) | 下载文件操作的数据结构。 |
| [`DownloadFileResult`](result/DownloadFileResult.md) | 下载文件操作的结果类型。 |
| [`DownloadFileStreamResult`](result/DownloadFileStreamResult.md) | 下载文件操作的流式结果类型。 |
| [`ExecuteCmdChunkData`](result/ExecuteCmdChunkData.md) | 命令执行输出分块的数据结构。 |
| [`ExecuteCmdData`](result/ExecuteCmdData.md) | 命令执行的数据结构。 |
| [`ExecuteCmdResult`](result/ExecuteCmdResult.md) | 命令执行操作的结果类型。 |
| [`ExecuteCmdStreamResult`](result/ExecuteCmdStreamResult.md) | 命令执行操作的流式结果类型。 |
| [`ExecuteCodeChunkData`](result/ExecuteCodeChunkData.md) | 代码执行输出分块的数据结构。 |
| [`ExecuteCodeData`](result/ExecuteCodeData.md) | 代码执行结果的数据模型。 |
| [`ExecuteCodeResult`](result/ExecuteCodeResult.md) | 代码执行操作的结果类型。 |
| [`ExecuteCodeStreamResult`](result/ExecuteCodeStreamResult.md) | 代码执行操作的流式结果类型。 |
| [`FileSystemData`](result/FileSystemData.md) | 列出文件与目录操作共用的数据结构。 |
| [`FileSystemItem`](result/FileSystemItem.md) | 文件与目录公共属性的基础模型。 |
| [`ListDirsResult`](result/ListDirsResult.md) | 列出目录操作的结果类型。 |
| [`ListFilesResult`](result/ListFilesResult.md) | 列出文件操作的结果类型。 |
| [`ReadFileChunkData`](result/ReadFileChunkData.md) | 分块读取文件的数据结构。 |
| [`ReadFileData`](result/ReadFileData.md) | 读取文件操作的数据结构。 |
| [`ReadFileResult`](result/ReadFileResult.md) | 读取文件操作的结果类型。 |
| [`ReadFileStreamResult`](result/ReadFileStreamResult.md) | 读取文件操作的流式结果类型。 |
| [`SearchFilesData`](result/SearchFilesData.md) | 搜索文件操作的数据结构。 |
| [`SearchFilesResult`](result/SearchFilesResult.md) | 搜索文件操作的结果类型。 |
| [`UploadFileChunkData`](result/UploadFileChunkData.md) | 上传文件分块返回的数据结构。 |
| [`UploadFileData`](result/UploadFileData.md) | 上传文件操作的数据结构。 |
| [`UploadFileResult`](result/UploadFileResult.md) | 上传文件操作的结果类型。 |
| [`UploadFileStreamResult`](result/UploadFileStreamResult.md) | 上传文件操作的流式结果类型。 |
| [`WriteFileData`](result/WriteFileData.md) | 写入文件操作的数据结构。 |
| [`WriteFileResult`](result/WriteFileResult.md) | 写入文件操作的结果类型。 |

## 说明

- 多数 DTO 依赖 Lombok 生成访问器或 builder；页面只记录显式字段与源码中声明的辅助方法。
- 流式结果类型通常承载 `*ChunkData` 负载，终态结果类型统一继承 `BaseResult<T>`。
- 代表性测试：`LocalFsOperationTest`、`LocalShellOperationTest`、`LocalCodeOperationTest`、`SysOperationToolAdapterTest`。
