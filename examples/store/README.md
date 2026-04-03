# Store Java Example

这个目录对应 Python 版 `examples/store/showcase_obs.py` 的对象存储演示流程，但当前 Java 框架只有本地对象存储 provider，因此这里用 `LocalObjectStorageClient` 展示同一组核心操作：创建 bucket、列举对象、上传、下载、校验和清理。

## 文件说明

- `StoreShowcaseExample.java`: 示例入口，按步骤执行本地对象存储流程。
- `StoreExampleSupport.java`: 路径解析、环境变量/JVM 参数读取、输出格式化和清理辅助。
- `data/test.txt`: 默认上传样例文件。

## 默认行为

示例默认使用下面这些路径和对象名：

- bucket 名称：`openjiuwen-local-demo`
- object 名称：`demo/test.txt`
- 源文件：`examples/store/data/test.txt`
- 下载文件：`examples/store/output/download/download_test.txt`
- 本地对象存储根目录：`examples/store/output/local_object_storage`

示例会依次执行：

1. 确保演示 bucket 已存在。
2. 列举当前 bucket 中的对象。
3. 删除旧的演示 object（如果存在）。
4. 上传样例文件。
5. 下载 object 到本地文件。
6. 校验文件大小和内容是否一致。
7. 再次列举对象，确认上传结果。
8. 删除刚上传的 object。
9. 默认清理下载文件和 bucket 目录。

## 可选覆盖项

运行时可以通过环境变量或 JVM 参数覆盖默认配置。JVM 参数优先级高于环境变量。

- `STORE_BUCKET_NAME` / `-Dopenjiuwen.example.store.bucketName`
- `STORE_OBJECT_NAME` / `-Dopenjiuwen.example.store.objectName`
- `STORE_SOURCE_FILE` / `-Dopenjiuwen.example.store.sourceFile`
- `STORE_DOWNLOAD_FILE` / `-Dopenjiuwen.example.store.downloadFile`
- `STORE_STORAGE_ROOT` / `-Dopenjiuwen.example.store.storageRoot`
- `STORE_OUTPUT_DIR` / `-Dopenjiuwen.example.store.outputDir`
- `STORE_KEEP_ARTIFACTS` / `-Dopenjiuwen.example.store.keepArtifacts`

如果你想保留运行后的 bucket 和下载文件，方便手工检查，可以把 `STORE_KEEP_ARTIFACTS` 设为 `true`。

## 运行方式

建议从 `f:\openJiuwenTT\agent-core-java-myfork` 目录运行：

```powershell
mvn -DskipTests compile
mvn dependency:build-classpath "-Dmdep.outputFile=target/store_example.classpath"
javac -cp "target/classes;$(Get-Content target/store_example.classpath -Raw)" examples/store/StoreExampleSupport.java examples/store/StoreShowcaseExample.java
java -cp "target/classes;examples/store;$(Get-Content target/store_example.classpath -Raw)" StoreShowcaseExample
```

保留运行产物的示例：

```powershell
java "-Dopenjiuwen.example.store.keepArtifacts=true" -cp "target/classes;examples/store;$(Get-Content target/store_example.classpath -Raw)" StoreShowcaseExample
```

## 输出预期

程序会按步骤打印每个操作的输入、结果和 `SUCCESS` / `FAILURE` 状态。成功运行时，通常会看到：

- bucket 已创建或已存在
- 上传后的 object 出现在列举结果里
- 下载文件大小与源文件一致
- 内容校验通过
- 默认模式下，下载文件和 bucket 在最后被删除

## 与 Python 示例的差异

- Python 版 `showcase_obs.py` 使用 `AioBotoClient`，面向 OBS/S3 风格云对象存储。
- Java 当前只提供 `LocalObjectStorageClient`，把 bucket/object 映射到本地文件系统目录。
- 因此这个 Java 示例对齐的是对象存储操作流程和接口语义，不是云 provider 的一比一能力对齐。