# com.openjiuwen.core.workflow.component.Branch

## 类 Branch

```java
public class Branch
```

`Branch` 表示一条分支定义，封装条件、目标节点列表和分支 id。

## 方法

| 签名 | 说明 |
| --- | --- |
| `public Branch(Object conditionObj, List<String> target, String branchId)` | 创建分支；字符串条件会包装为 `ExpressionCondition`，`BooleanSupplier` 会包装为 `FuncCondition`。 |
| `public boolean evaluate(BaseSession session)` | 判断当前分支是否命中。 |
| `public Object traceInfo(BaseSession session)` | 返回当前分支的 trace 信息。 |
| `public String getBranchId()` | 返回分支 id。 |
| `public List<String> getTarget()` | 返回目标节点列表。 |
