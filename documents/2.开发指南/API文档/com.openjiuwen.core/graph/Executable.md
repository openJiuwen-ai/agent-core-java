# com.openjiuwen.core.graph.Executable

## 抽象类 Executable

```java
public abstract class Executable<I, O>
```

具备 invoke/stream/collect/transform 能力的通用执行体基类。

## 方法

| 签名 | 说明 |
| --- | --- |
| `public O onInvoke(I inputs, BaseSession session, Object... kwargs)` | 以给定输入和会话执行组件；默认抛出 `UnsupportedOperationException`，要求子类覆写。 |
| `public Iterator<O> onStream(I inputs, BaseSession session, Object... kwargs)` | 以流式方式执行组件；默认抛出 `UnsupportedOperationException`。 |
| `public O onCollect(I inputs, BaseSession session, Object... kwargs)` | 从流式输入聚合出单个结果；默认抛出 `UnsupportedOperationException`。 |
| `public Iterator<O> onTransform(I inputs, BaseSession session, Object... kwargs)` | 将流式输入转换为流式输出；默认抛出 `UnsupportedOperationException`。 |
| `public boolean skipTrace()` | 返回当前组件是否跳过 tracer 打点；默认 `false`。 |
| `public boolean graphInvoker()` | 返回当前组件是否作为子图调用器；默认 `false`。 |
| `public boolean postCommit()` | 返回执行后是否需要执行 post-commit；默认 `true`。 |
| `public String componentType()` | 返回组件类型标识；默认空字符串。 |
