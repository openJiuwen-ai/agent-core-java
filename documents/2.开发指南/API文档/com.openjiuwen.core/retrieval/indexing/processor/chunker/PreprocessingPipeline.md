# com.openjiuwen.core.retrieval.indexing.processor.chunker.PreprocessingPipeline

## class PreprocessingPipeline

```java
public class PreprocessingPipeline implements TextPreprocessor
```

Sequential text preprocessing pipeline.

## Constructors

| Signature | Description |
| --- | --- |
| `public PreprocessingPipeline(List<TextPreprocessor> preprocessors)` | Create a new `PreprocessingPipeline` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public String process(String text)` | Process the input values and return transformed results. |
