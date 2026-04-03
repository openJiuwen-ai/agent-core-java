# com.openjiuwen.core.workflow.condition.Condition

## 抽象类 Condition

```java
public abstract class Condition extends AtomicNode
```

`Condition` 是工作流分支与循环条件的统一抽象基类。

## 方法

| 签名 | 说明 |
| --- | --- |
| `public Condition()` | 创建无输入 schema 的条件。 |
| `public Condition(Object inputSchema)` | 创建带输入 schema 的条件。 |
| `public boolean evaluate(BaseSession session)` | 基于当前 session 执行条件判断。 |
| `public abstract Object doInvoke(Object inputs, BaseSession session)` | 子类实现具体判断逻辑。 |
| `public Object traceInfo(BaseSession session)` | 返回 trace 信息，默认实现为空。 |

## 说明

- 子类可返回 `Object[]{boolean, outputs}`，在判断的同时把输出写回 session。
