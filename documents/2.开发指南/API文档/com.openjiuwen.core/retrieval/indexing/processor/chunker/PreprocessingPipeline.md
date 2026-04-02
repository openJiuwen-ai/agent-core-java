# com.openjiuwen.core.retrieval.indexing.processor.chunker.PreprocessingPipeline

## 类 PreprocessingPipeline

```java
public class PreprocessingPipeline implements TextPreprocessor
```

`PreprocessingPipeline` 以顺序链的方式执行多个 `TextPreprocessor`。

## 构造方法

### `public PreprocessingPipeline(List<TextPreprocessor> preprocessors)`

把传入预处理器按顺序保存；`null` 会被视为空列表。

## 公开方法

### `public String process(String text)`

从原始文本开始，按注册顺序依次调用每个预处理器的 `process(...)`，并把上一步结果作为下一步输入。
