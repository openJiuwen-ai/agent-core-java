# com.openjiuwen.core.graph.ExecutableGraph

## 抽象类 ExecutableGraph

```java
public abstract class ExecutableGraph<I, O> extends Executable<I, O>
```

从输入映射中提取 `inputs` 与 `config` 后执行图逻辑的抽象基类。

## 方法

| 签名 | 说明 |
| --- | --- |
| `public O invoke(I inputs, BaseSession session)` | 从输入映射中读取 `Constant.INPUTS_KEY` 与 `Constant.CONFIG_KEY`，再委托给 `doInvoke`。 |
| `public Iterator<O> stream(I inputs, BaseSession session)` | 流式执行默认未实现，当前返回 `null`。 |
| `public O collect(Iterator<I> inputs, BaseSession session)` | 聚合模式默认未实现，当前返回 `null`。 |
| `public Iterator<O> transform(Iterator<I> inputs, BaseSession session)` | 转换模式默认未实现，当前返回 `null`。 |
| `public void interrupt(Map<String, Object> message)` | 处理外部中断消息；默认实现为 no-op。 |
