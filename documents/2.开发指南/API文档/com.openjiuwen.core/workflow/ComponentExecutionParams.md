# com.openjiuwen.core.workflow.ComponentExecutionParams

## class ComponentExecutionParams

```java
public class ComponentExecutionParams
```

Component execution parameters encapsulation.

## Fields

| Signature | Description |
| --- | --- |
| `private final String nodeId` | Node id. |
| `private final NodeSessionApi session` | Session. |
| `private final ComponentExecutable executor` | Executor. |
| `private final Map<String, Object> inputs` | Inputs. |
| `private final Map<String, Object> inputsSchema` | Inputs schema. |
| `private final Map<String, Object> outputsSchema` | Outputs schema. |
| `private final ModelContext context` | Context. |

## Constructors

| Signature | Description |
| --- | --- |
| `public ComponentExecutionParams(String nodeId, NodeSessionApi session, ComponentExecutable executor, Map<String, Object> inputs, Map<String, Object> inputsSchema, Map<String, Object> outputsSchema, ModelContext context)` | Create a new `ComponentExecutionParams` instance. |
| `public ComponentExecutionParams(String nodeId, NodeSessionApi session, ComponentExecutable executor, Map<String, Object> inputs)` | Create a new `ComponentExecutionParams` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public String getNodeId()` | Return the node id. |
| `public NodeSessionApi getSession()` | Return the session. |
| `public ComponentExecutable getExecutor()` | Return the executor. |
| `public Map<String, Object> getInputs()` | Return the inputs. |
| `public Map<String, Object> getInputsSchema()` | Return the inputs schema. |
| `public Map<String, Object> getOutputsSchema()` | Return the outputs schema. |
| `public ModelContext getContext()` | Return the context. |
