# com.openjiuwen.core.workflow.component.loop.PostLoopBody

## 类 PostLoopBody

```java
public class PostLoopBody extends Executable<Object, Object>
```

循环体后的收尾节点，用于记录已完成的轮次索引。

## 方法

| 签名 | 说明 |
| --- | --- |
| `public Object onInvoke(Object inputs, BaseSession session, Object... kwargs)` | 执行当前节点的运行逻辑。 |
| `public boolean skipTrace()` | 返回执行时是否跳过 trace 记录。 |
| `public int getFinishIndex()` | 返回`finishIndex` 字段。 |
| `public void setFinishIndex(int finishIndex)` | 设置`finishIndex` 字段。 |
