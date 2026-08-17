# kv-cache-demo

Standalone Maven project for the MicroCompactProcessor + KV cache release demo.
Bundles the two demo classes plus the config loader; depends on `agent-core-java`
from the local Maven repo.

## Prerequisites

- JDK 17+
- Maven 3.9+
- `agent-core-java` installed to the local Maven repo:

  ```bash
  cd /path/to/agent-core-java
  mvn install -DskipTests
  ```

- A vLLM server with the `openJiuwen-vllm-affinity` plugin exposing:
  - `POST /v1/chat/completions` (standard OpenAI-compatible chat)
  - `POST /release_kv_cache` (affinity plugin endpoint)

## Configure

Edit `src/main/resources/apiconfig.json` (bundled into the jar at build time):

```json
{
  "API_BASE": "http://your-vllm-host:port",
  "API_KEY": "sk-xxx",
  "MODEL_PROVIDER": "InferenceAffinity",
  "MODEL_NAME": "GLM-5.2",
  "LLM_SSL_VERIFY": "False"
}
```

Notes:
- `API_BASE` must NOT include `/v1` — `InferenceAffinityModelClient` appends
  `/v1/chat/completions` and `/release_kv_cache` itself.
- `MODEL_PROVIDER` must be `InferenceAffinity` so `supportsKvCacheRelease()`
  returns true and the auto-release chain activates.

To override without rebuilding, point at an external file:

```bash
export OPENJIUWEN_API_CONFIG=/abs/path/apiconfig.json
# or
java -Dopenjiuwen.example.config=/abs/path/apiconfig.json -jar ...
```

Resolution order (first hit wins):
1. `-Dopenjiuwen.example.config=/abs/path.json`
2. `OPENJIUWEN_API_CONFIG=/abs/path.json`
3. filesystem: `examples/apiconfig.json`, `../apiconfig.json`, `apiconfig.json`
4. classpath resource `apiconfig.json` (inside the jar)

## Build

```bash
cd examples/kv-cache-demo
mvn clean package -DskipTests
```

Produces `target/kv-cache-demo-0.1.0.jar` (shaded uber-jar, all deps included).

## Run

### Option A: shaded uber-jar

```bash
java -Dfile.encoding=UTF-8 -jar target/kv-cache-demo-0.1.0.jar
```

### Option B: exec plugin (no jar build)

```bash
mvn -q -DskipTests exec:java
```

### Option C: plain classpath (after `mvn dependency:build-classpath -Dmdep.outputFile=target/cp.txt -q`)

```bash
java -Dfile.encoding=UTF-8 \
  -cp "target/classes:$(cat target/cp.txt)" \
  examples.context_evolver.MicroCompactProcessorKvCacheExample
```

## What to watch for

Expected log sequence (when the auto-release chain is active):

```
Round 1:
  [agent] INFO  agent - ReAct iteration 1/5
  [llm]    INFO  llm    - Before request chat model ...  (no "LLM does not support KV cache release" warning)
Round 3:
  [context_engine] INFO  context_engine - trigger context processor MicroCompactProcessor on ADD
  [context_engine] INFO  context_engine -   [RELEASE REASON] Message modified at index 3
  [context_engine] INFO  context_engine - KV cache release triggered for session micro_compact_kv_cache_001 (msg_idx=3, tool_idx=1)
  [llm]           INFO  llm    - release_kv_cache response: status=200, body=...
```

If you see `status=404`, the vLLM server does not have the `openJiuwen-vllm-affinity`
plugin (or it is not mounted at `/release_kv_cache`). The release is caught and
logged as a warning; the demo continues.

## Files

| File | Purpose |
|---|---|
| `pom.xml` | Maven build; shaded uber-jar with `mainClass` manifest |
| `src/main/java/examples/context_evolver/MicroCompactProcessorKvCacheExample.java` | Entry point |
| `src/main/java/examples/context_evolver/MicroCompactProcessorKvCacheExampleSupport.java` | Demo logic |
| `src/main/java/examples/reac_agent/ExampleApiConfigLoader.java` | Config loader (classpath fallback for jar run) |
| `src/main/resources/apiconfig.json` | Bundled default config (override via system prop / env) |
