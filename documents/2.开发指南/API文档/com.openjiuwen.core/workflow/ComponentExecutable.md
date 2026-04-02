# com.openjiuwen.core.workflow.ComponentExecutable

## 抽象类 ComponentExecutable

```java
public abstract class ComponentExecutable extends Executable<Object, Object>
```

`ComponentExecutable` 是工作流组件执行基类，统一封装 `invoke`、`stream`、`collect`、`transform` 四种执行模式。

## 方法

| 签名 | 说明 |
| --- | --- |
| `public Object onInvoke(Object inputs, BaseSession session, Object... kwargs)` | 适配工作流运行时的同步调用入口。 |
| `public Iterator<Object> onStream(Object inputs, BaseSession session, Object... kwargs)` | 适配流式输出入口。 |
| `public Object onCollect(Object inputs, BaseSession session, Object... kwargs)` | 适配流输入聚合入口。 |
| `public Iterator<Object> onTransform(Object inputs, BaseSession session, Object... kwargs)` | 适配流输入流输出入口。 |
| `public Object invoke(Object inputs, NodeSessionApi session, ModelContext context)` | 组件同步执行方法，默认未实现。 |
| `public Iterator<Object> stream(Object inputs, NodeSessionApi session, ModelContext context)` | 组件流式执行方法，默认未实现。 |
| `public Object collect(Object inputs, NodeSessionApi session, ModelContext context)` | 组件流聚合方法，默认未实现。 |
| `public Iterator<Object> transform(Object inputs, NodeSessionApi session, ModelContext context)` | 组件流变换方法，默认未实现。 |

## 说明

- 运行时要求底层 session 为 `NodeSession`；否则会抛出内部编排错误。
- 子类只需按自身能力覆写对应方法；未覆写的方法保持“不支持”语义。
