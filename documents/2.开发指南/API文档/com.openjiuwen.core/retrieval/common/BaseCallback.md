# com.openjiuwen.core.retrieval.common.BaseCallback

## 类 BaseCallback

```java
public class BaseCallback
```

批处理回调基类，用于记录批次调用次数与总任务量。

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public BaseCallback()` | 创建空回调，`total = 0`。 |
| `public BaseCallback(Collection<?> sequence)` | 根据输入集合大小初始化总量。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public void onBatch(int startIdx, int endIdx, List<String> batch)` | 处理一个批次并累计调用次数。 |
| `public int getCallCounter()` | 返回已调用批次数。 |
| `public int getTotal()` | 返回总量。 |

## 说明

- 该类本身不负责输出日志或进度条，只负责统计。
- `APIEmbeddingTest` 等测试会把它或其子类作为回调对象传入批处理流程。
