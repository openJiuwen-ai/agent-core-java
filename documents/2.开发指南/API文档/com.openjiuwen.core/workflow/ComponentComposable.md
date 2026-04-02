# com.openjiuwen.core.workflow.ComponentComposable

## interface ComponentComposable

```java
public interface ComponentComposable
```

Interface for workflow graph construction. Separates graph construction logic from execution logic (ComponentExecutable).

## Methods

| Signature | Description |
| --- | --- |
| `default void addComponent(Graph graph, String nodeId, boolean waitForAll)` | Add this component to a workflow graph. |
| `default Executable<?, ?> toExecutable()` | Convert this composable component to an executable instance. |
