# com.openjiuwen.core.retrieval.indexing.processor.Processor

## 接口 Processor

```java
public interface Processor<I, O>
```

`Processor` 是 indexing 处理链的最小抽象，只定义一个统一的 `process` 入口。

## 抽象方法

- `O process(I input, Map<String, Object> options)`：处理输入并返回输出，`options` 用于传递回调、开关或实现自定义参数。
