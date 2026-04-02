# com.openjiuwen.core.workflow.component.loop.EmptyExecutable

## 类 EmptyExecutable

```java
public class EmptyExecutable extends Executable<Object, Object>
```

空执行节点，用作循环图中的条件占位和中转节点。

## 方法

| 签名 | 说明 |
| --- | --- |
| `public Object onInvoke(Object inputs, BaseSession session, Object... kwargs)` | 执行当前节点的运行逻辑。 |
| `public boolean skipTrace()` | 返回执行时是否跳过 trace 记录。 |
