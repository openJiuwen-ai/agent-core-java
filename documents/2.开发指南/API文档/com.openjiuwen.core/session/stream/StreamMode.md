# com.openjiuwen.core.session.stream.StreamMode

## 枚举 StreamMode

```java
public enum StreamMode
```

定义可用的流模式及其元信息。

## 方法

| 签名 | 说明 |
| --- | --- |
| `public String getMode()` | 返回模式标识字符串。 |
| `public String getDesc()` | 返回模式说明文本。 |
| `public Map<String, Object> getOptions()` | 返回模式附带的选项映射。 |
| `public String toString()` | 返回包含 `mode`、`desc` 和 `options` 的字符串表示。 |

## 枚举值

| 值 | 说明 |
| --- | --- |
| `OUTPUT` | 框架标准输出流。 |
| `TRACE` | 图执行产生的 trace 流。 |
| `CUSTOM` | runnable 自定义的流。 |

## 说明

- 相关测试：`StreamOutputFullTest`、`StreamOutputTest`。
