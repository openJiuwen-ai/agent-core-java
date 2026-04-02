# com.openjiuwen.core.workflow.BranchRouter

## 类 BranchRouter

```java
public class BranchRouter implements Router
```

`BranchRouter` 是工作流条件边使用的分支路由器。它顺序评估已注册的 `Branch`，返回第一个命中分支对应的目标节点列表，并可在启用时记录 trace 信息。

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public BranchRouter()` | 创建默认路由器，不主动上报 trace。 |
| `public BranchRouter(boolean reportTrace)` | 创建路由器，并指定是否记录分支 trace。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public void addBranch(Object condition, Object target, String branchId)` | 添加一条分支规则；`target` 支持单节点或节点列表。 |
| `public DrawableBranchRouter getDrawableBranchRouter()` | 返回供可视化使用的路由信息。 |
| `public void setSession(Object session)` | 绑定 `BaseSession` 或 `NodeSessionApi`，供条件判断和 trace 使用。 |
| `public Object apply(Object input)` | 执行分支匹配，返回命中的目标节点集合。 |

## 说明

- 分支按添加顺序求值，首个命中分支会立即返回。
- `condition` 或 `target` 为空时会抛出 `COMPONENT_BRANCH_PARAM_INVALID`。
- 若没有任何分支命中，会抛出 `COMPONENT_BRANCH_EXECUTION_ERROR`。
- `WorkflowTest` 的分支路由场景覆盖了该类型与 `ExpressionCondition` 的协作行为。
