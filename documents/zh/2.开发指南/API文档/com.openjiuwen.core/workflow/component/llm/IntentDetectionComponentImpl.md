# com.openjiuwen.core.workflow.component.llm.IntentDetectionComponentImpl

意图识别工作流组件实现，负责组装可执行体并维护分支路由。

## class IntentDetectionComponentImpl

```java
public class IntentDetectionComponentImpl implements ComponentComposable
```

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public IntentDetectionComponentImpl(IntentDetectionCompConfig componentConfig)` | 使用组件配置创建实例，并初始化内部 `BranchRouter`。 |

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public IntentDetectionExecutable getExecutable()` | 返回惰性创建的可执行体。 |
| `public void addComponent(Graph graph, String nodeId, boolean waitForAll)` | 将当前组件注册到工作流图并挂接条件边。 |
| `public Executable<?, ?> toExecutable()` | 构建新的 [`IntentDetectionExecutable`](./IntentDetectionExecutable.md)，并注入当前路由器。 |
| `public void addBranch(Object condition, Object target, String branchId)` | 添加一条带显式 `branchId` 的路由分支。 |
| `public void addBranch(Object condition, Object target)` | 添加一条不带显式 `branchId` 的路由分支。 |
| `public void add_branch(Object condition, Object target, String branchId)` | `addBranch` 的 snake_case 兼容别名。 |
| `public void add_branch(Object condition, Object target)` | `addBranch` 的 snake_case 兼容别名。 |
| `public BranchRouter router()` | 返回当前组件持有的分支路由器。 |

## Notes

- 该实现内部维护单个 `BranchRouter`，因此在 `addComponent()` 与 `toExecutable()` 之间共享同一套路由配置。
