# com.openjiuwen.core.retrieval.common.LoggingCallback

## 类 LoggingCallback

```java
public class LoggingCallback extends BaseCallback
```

基于 SLF4J 的批处理进度日志回调。

## 说明

- 构造时接收总量与日志描述。
- `desc` 为空白时会回退到默认值 `"Indexing"`。
- `onBatch(...)` 会输出当前进度日志，并继承父类的调用计数逻辑。
