# com.openjiuwen.core.workflow.ComponentComposable

## 接口 ComponentComposable

```java
public interface ComponentComposable
```

`ComponentComposable` 定义“可被注册进工作流图”的组件契约。实现类既可以自己向 `Graph` 注册，也可以只提供 `Executable`，由默认实现完成注册。

## 方法

| 签名 | 说明 |
| --- | --- |
| `default void addComponent(Graph graph, String nodeId, boolean waitForAll)` | 将当前组件注册到图中。默认实现会调用 `toExecutable()`。 |
| `default Executable<?, ?> toExecutable()` | 返回对应执行器；若实现类本身不是 `Executable` 且未覆写该方法，会抛出 `UnsupportedOperationException`。 |

## 说明

- 新的工作流组件通常实现 `ComponentComposable` 或继承 `WorkflowComponent`。
- `Workflow` 和 `BaseWorkflow` 在注册节点时都依赖该接口，而旧 POJO 组件会先通过 `LegacyWorkflowComponentSupport` 适配成该接口。
