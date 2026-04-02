# com.openjiuwen.core.workflow.component.AdvancedLoopComponent

## interface AdvancedLoopComponent

```java
public interface AdvancedLoopComponent extends ComponentComposable
```

Interface for advanced loop components that contain a body subgraph. Stub interface for the graph visualization module. Will be fully implemented

## Methods

| Signature | Description |
| --- | --- |
| `HasDrawable getBody()` | Gets the loop body (inner graph). |
| `void registerCallback(LoopCallback callback)` | Register a loop callback after construction. |
