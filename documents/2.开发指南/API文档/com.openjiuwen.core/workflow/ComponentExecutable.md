# com.openjiuwen.core.workflow.ComponentExecutable

## class ComponentExecutable

```java
public abstract class ComponentExecutable extends Executable<Object, Object>
```

Base executable for workflow components, providing the four fundamental execution patterns: invoke, stream, collect, transform.

## Methods

| Signature | Description |
| --- | --- |
| `public Object onInvoke(Object inputs, BaseSession session, Object... kwargs)` | Execute `onInvoke`. |
| `public Iterator<Object> onStream(Object inputs, BaseSession session, Object... kwargs)` | Execute `onStream`. |
| `public Object onCollect(Object inputs, BaseSession session, Object... kwargs)` | Execute `onCollect`. |
| `public Iterator<Object> onTransform(Object inputs, BaseSession session, Object... kwargs)` | Execute `onTransform`. |
| `public Object invoke(Object inputs, NodeSessionApi session, ModelContext context)` | Execute component synchronously with batch input and output. |
| `public Iterator<Object> stream(Object inputs, NodeSessionApi session, ModelContext context)` | Execute component with batch input but streaming output. |
| `public Object collect(Object inputs, NodeSessionApi session, ModelContext context)` | Execute component with streaming input but batch output. |
| `public Iterator<Object> transform(Object inputs, NodeSessionApi session, ModelContext context)` | Execute component with streaming input and streaming output. |
| `private static ModelContext extractContext(Object... kwargs)` | Execute `extractContext`. |
