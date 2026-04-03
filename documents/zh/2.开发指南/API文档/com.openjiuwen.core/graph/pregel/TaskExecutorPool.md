# com.openjiuwen.core.graph.pregel.TaskExecutorPool

## 类 TaskExecutorPool

```java
public class TaskExecutorPool
```

基于虚拟线程并发执行节点任务的任务池。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `CANCEL_GRACE_TIMEOUT_MS` | `long` | `5000` | 等待已取消任务完成清理的宽限时间，单位毫秒。 |
| `config` | `PregelConfig` | `-` | 提交 `NodeTask` 时复用的执行配置。 |
| `executor` | `ExecutorService` | `-` | 基于虚拟线程创建的任务执行器。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public TaskExecutorPool(PregelConfig config)` | 基于指定配置创建 `TaskExecutorPool`。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public void submit(PregelNode node, int version)` | 将节点包装为 `NodeTask` 后提交到线程池执行。 |
| `public void waitAll() throws Exception` | 以 FIRST_EXCEPTION 语义等待所有任务完成；普通异常优先于 `GraphInterrupt` 抛出。 |
| `public void cancelAll()` | 取消全部运行中任务，并为失败节点写入 `PendingNode`。 |
| `public void clear()` | 清空成功消息、失败节点与运行中任务集合。 |
| `public List<Message> getSucceedMessages()` | 返回当前成功产生的消息列表。 |
| `public Map<String, PendingNode> getFailed()` | 返回当前失败节点映射。 |

## 相关测试

- `TaskExecutorPoolTest`
