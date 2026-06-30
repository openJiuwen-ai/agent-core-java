# Agent Builder Example

This example demonstrates the Java `AgentBuilder` entry point.

## Run

```bash
cd agent-core-java
mvn -DskipTests compile
java -cp "target/classes:examples" examples.agent_builder.AgentBuilderExample
```

The current Java baseline supports:

- session history tracking
- progress reporting
- clarification / processing / completed state mapping
- simple DSL normalization for completed builds

Deeper Python-side builder pipelines are still being ported incrementally.
