# humaneval-demo

Standalone Maven project for the OpenAI HumanEval benchmark demo. Sends each
task's function signature + docstring to a configured LLM through the Java
ReAct agent (`agent-core-java`), then executes each generated completion with
a local Python interpreter and reports the pass rate.

## Prerequisites

- JDK 17+
- Maven 3.9+
- Python 3.x (on PATH as `python`, or override)
- `agent-core-java` installed to the local Maven repo:

  ```bash
  cd /path/to/agent-core-java
  mvn install -DskipTests
  ```

- An OpenAI-compatible LLM endpoint (vLLM, etc.)
- OpenAI HumanEval dataset (`HumanEval.jsonl` or `.jsonl.gz`). Default path:

  ```
  D:/jiuwen_projects/modelscope--humaneval/snapshots/master/HumanEval.jsonl
  ```

  Override with `-Dopenjiuwen.humaneval.path=...` or env
  `HUMANEVAL_PATH=/abs/path/HumanEval.jsonl`.

## Configure

Edit `src/main/resources/apiconfig.json` (bundled into the jar at build time):

```json
{
  "API_BASE": "http://your-llm-host:port",
  "API_KEY": "sk-xxx",
  "MODEL_PROVIDER": "InferenceAffinity",
  "MODEL_NAME": "GLM-5.2",
  "LLM_SSL_VERIFY": "False",
  "MODEL_NAME_EMBEDDING": "your-embedding-model-name"
}
```

Notes:
- `API_BASE` must NOT include `/v1` — the model client appends
  `/v1/chat/completions` itself.
- `MODEL_PROVIDER` selects the model client implementation. Use
  `InferenceAffinity` for the vLLM affinity plugin; other providers work too
  but KV cache release is disabled.

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

Python executable resolution (first hit wins):
1. `-Dopenjiuwen.humaneval.python=...`
2. env `HUMANEVAL_PYTHON=...`
3. default `python`

## Build

```bash
cd examples/humaneval-demo
mvn clean package -DskipTests
```

Produces `target/humaneval-demo-0.1.0.jar` (shaded uber-jar, all deps
included).

## Run

### Option A: shaded uber-jar

```bash
java -Dfile.encoding=UTF-8 -jar target/humaneval-demo-0.1.0.jar
```

### Option B: exec plugin (no jar build)

```bash
mvn -q -DskipTests exec:java
```

### Option C: plain classpath (after
`mvn dependency:build-classpath -Dmdep.outputFile=target/cp.txt -q`)

```bash
java -Dfile.encoding=UTF-8 \
  -cp "target/classes:$(cat target/cp.txt)" \
  examples.humaneval.HumanEvalDemo
```

## What to watch for

Expected log sequence:

```
[main] INFO  Loaded 164 HumanEval tasks from ...
[main] INFO  [1/164] HumanEval/0 passed=true (pass_rate=1/1)
[main] INFO  [2/164] HumanEval/1 passed=false (pass_rate=1/2)
...
[main] INFO  HumanEval finished. passed=N total=164 errored=M pass_rate=XX.XX%
```

Tuning knobs (in `HumanEvalDemo`):
- `MAX_ITERATIONS` — ReAct iterations per task (default 1: code completion
  does not benefit from multi-round reasoning)
- `PARALLELISM` — concurrent tasks (default 15)
- `MAX_ATTEMPTS` — retry count on transient LLM/executor failures (default 3)

## Files

| File | Purpose |
|---|---|
| `pom.xml` | Maven build; shaded uber-jar with `mainClass` manifest |
| `src/main/java/examples/humaneval/HumanEvalDemo.java` | Entry point |
| `src/main/java/examples/humaneval/HumanEvalDataset.java` | JSONL dataset loader (plain or gzip) |
| `src/main/java/examples/humaneval/HumanEvalExecutor.java` | Python subprocess runner |
| `src/main/java/examples/humaneval/HumanEvalTask.java` | Task record |
| `src/main/java/examples/reac_agent/ExampleApiConfigLoader.java` | Config loader (classpath fallback for jar run) |
| `src/main/resources/apiconfig.json` | Bundled default config (override via system prop / env) |
