# com.openjiuwen.core.graph.pregel.PregelLoop

## 类 PregelLoop

```java
public class PregelLoop
```

实现 BSP 超步调度的 Pregel 执行循环。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `graph` | `Pregel` | `-` | 当前循环绑定的 Pregel 图实例。 |
| `manager` | `ChannelManager` | `-` | 管理 channel 缓冲与 ready 节点的 `ChannelManager`。 |
| `config` | `PregelConfig` | `-` | 当前循环执行配置。 |
| `saver` | `Store` | `-` | 可选的图状态持久化存储。 |
| `step` | `int` | `0` | 当前 super-step 序号。 |
| `maxStep` | `int` | `0` | 本轮允许执行的最大 super-step。 |
| `executor` | `TaskExecutorPool` | `-` | 当前 super-step 使用的任务执行池。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public PregelLoop(Pregel graph, PregelConfig config)` | 基于 `Pregel` 与 `PregelConfig` 创建执行循环。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public void init()` | 初始化执行循环；如存在持久化状态则恢复，否则触发起始节点。 |
| `public boolean runStep() throws Exception` | 执行一个 super-step；发生异常时会保存错误态后继续抛出。 |
| `public int getStep()` | 返回当前 `step`。 |
| `public PregelConfig getConfig()` | 返回当前 `config`。 |
| `public List<String> getActiveNodes()` | 返回当前正在执行或即将执行的活跃节点列表。 |

## 相关测试

- `PregelTest`
