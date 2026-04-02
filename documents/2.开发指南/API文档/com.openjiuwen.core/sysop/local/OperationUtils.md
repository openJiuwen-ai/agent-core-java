# com.openjiuwen.core.sysop.local.OperationUtils

## 类 OperationUtils

```java
public final class OperationUtils
```

`OperationUtils` 提供本地执行共用的静态辅助方法，覆盖临时文件管理、环境变量合并和 `ProcessHandler` 创建。

## 方法

| 签名 | 说明 |
| --- | --- |
| `public static String createTmpFile(String fileContent, String fileSuffix)` | 创建带指定后缀的 UTF-8 临时文件，并返回其绝对路径。 |
| `public static boolean deleteTmpFile(String filePath)` | 删除指定临时文件；文件不存在或删除失败时返回 `false`。 |
| `public static Map<String, String> prepareEnvironment(Map<String, String> customEnv)` | 以系统环境变量为基础，叠加调用方提供的自定义环境变量。 |
| `public static ProcessHandler createHandler(Process process, int chunkSize, Charset encoding, int timeout)` | 按显式参数创建 `ProcessHandler`。 |
| `public static ProcessHandler createHandler(Process process)` | 使用默认参数创建 `ProcessHandler`。 |

## 相关测试

- `LocalUtilsTest`
