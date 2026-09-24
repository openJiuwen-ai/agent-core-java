# DeepAgent without Jedis

This is a real downstream application example. It excludes `redis.clients:jedis`
from `agent-core-java` and runs a full `DeepAgent` with task loop, task planning,
a general-purpose subagent, tenant isolation, file-backed todos, and the
default in-memory checkpointer.

The example deliberately does not configure a Redis checkpointer or Redis KV
store. It demonstrates that an application which does not use Redis can remove
Jedis and still use the regular DeepAgent path.

## Configuration

The example follows the other Agent examples and reads model settings from
environment variables first, then `examples/apiconfig.json`:

| Variable | Description |
| --- | --- |
| `API_BASE` | OpenAI-compatible model endpoint |
| `API_KEY` | Model API key |
| `MODEL_PROVIDER` | Model client provider, for example `OpenAI` |
| `MODEL_NAME` | Model name |
| `LLM_SSL_VERIFY` | Whether to verify TLS certificates |

No API key is committed to the repository.

## Run

Install the current core artifact first:

```bash
mvn -DskipTests install
```

Build the consumer and confirm the transitive dependency is absent:

```bash
mvn -f examples/deep_agent_without_jedis/pom.xml package
mvn -f examples/deep_agent_without_jedis/pom.xml dependency:tree
```

The Maven Enforcer rule fails the build if `redis.clients:jedis` is present.
Run the real Agent after setting the model configuration:

```bash
mvn -f examples/deep_agent_without_jedis/pom.xml exec:java
```

The prompt asks the model to create a multi-step todo list and complete a
small local task. The example succeeds only when the invocation completes and
the file-backed todo is present. Model output remains model-dependent; the
temporary workspace path is printed for inspection.
