# com.openjiuwen.core.context.context.KVCacheManager

## class KVCacheManager

```java
public class KVCacheManager
```

`KVCacheManager` 用于记录上一次发送给推理端的 `ContextWindow`，在后续窗口发生变化时计算需要释放的 KV Cache 起点。

## 构造方法

### `public KVCacheManager(String sessionId)`

创建绑定到某个会话 ID 的 KV Cache 管理器。

## 主要方法

### `public void release(ContextWindow contextWindow)`

仅基于窗口内容做差异检查；首次调用只缓存窗口，不触发释放。

### `public void release(ContextWindow contextWindow, Object model)`

在窗口和工具发生差异时，尝试调用 `InferenceAffinityModel.release(...)` 释放旧缓存。

**说明**

- 仅当 `model instanceof InferenceAffinityModel` 时才会执行真实释放；否则只记录日志并更新 `lastContextWindow`。
- 比较逻辑会分别检查消息列表和工具列表的首个差异索引。
- 失败不会中断主流程，而是记录 warning 日志。

## 说明

- `KVCacheManagerTest` 验证了首次调用、相同窗口、消息变更、工具变更以及连续多次调用场景。
- 当前 Java 版本重点移植了差异检测逻辑；实际释放能力仍依赖推理模型实现。
