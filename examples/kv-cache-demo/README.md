# kv-cache-demo

Standalone Maven project for the KV-cache demos. Depends on `agent-core-java`
from the local Maven repo:

```bash
cd /path/to/agent-core-java
mvn install -DskipTests
```

## Demos

| Main class | What it verifies |
|---|---|
| `examples.AscendAffinitySchedulingDemo` (default) | Ascend-affinity scheduling flows end to end: identity hint on inference, window-diff evict, offload + admission auto-prefetch, session-release evict, and the sticky/non-sticky TaskTool subagent lifecycle. |
| `examples.AscendAffinityKvCacheDemo` | Protocol-level smoke for the three affinity management actions (prefetch / offload / evict). |
| `examples.ReActWeatherAgentExample` | ReAct agent with a weather REST tool. |

## Run

```bash
# default demo (scheduling), offline against the embedded mock gateway
mvn -q -DskipTests exec:java

# pick another demo
mvn -q -DskipTests exec:java -Ddemo.mainClass=examples.AscendAffinityKvCacheDemo

# shaded uber-jar
mvn clean package -DskipTests
java -Dfile.encoding=UTF-8 -jar target/kv-cache-demo-0.1.0.jar
```

API key / model name come from `src/main/resources/apiconfig.json`
(`API_KEY`, `MODEL_NAME`); override the file with
`-Dopenjiuwen.example.config=/abs/path.json` or
`OPENJIUWEN_API_CONFIG=/abs/path.json`. The affinity demos fix the provider to
`AscendAffinity` with `extensions.kv_cache.mode=affinity`.

## Ascend-affinity scheduling chains

Each chain asserts on the `agent_hint` sequence recorded by the embedded
`AffinityMockGateway` (no external service needed):

1. **chain1** — first `Runner.runAgent` binds the session: the model call
   carries the identity `agent_hint`
   (`{"session_id","parent_session_id"}`).
2. **chain2** — a demo `ContextProcessor` rewrites the conversation window
   between rounds (simulated compression); the model-call hook then issues a
   messages-range `evict` automatically
   (`edits=[{"type":"evict","target":"messages","start":1,"end":2}]`), before
   the next model call.
3. **chain3** — `session.suspendKvc()` offloads the cache
   (`edits=[{"type":"offload","target":"session"}]`) and blocks admission; the
   next inference auto-prefetches through admission control
   (`edits=[{"type":"prefetch","target":"session"}]`) without any manual call.
4. **chain4** — `session.releaseKvc()` evicts the whole session and unbinds
   the runtime; later suspend calls are terminal no-ops.
5. **chain5** — TaskTool subagents: the sticky `verification_agent` is
   prefetched on entry and offloaded on success, while the non-sticky `code`
   subagent is evicted on finish; management edits carry the child cache id
   under the parent linkage (`parent_session_id=<main session>`).

Any failed chain prints `[FAIL]` and the process exits non-zero with the
failing list.

Which subagent types are treated as stateful across TaskTool calls is
configurable via `DeepAgentConfig.stableSubSessionTypes`
(default `["browser_agent", "verification_agent"]`). Listed types reuse a
deterministic sub-session id AND a sticky cache lifecycle (prefetch on
entry, offload on success); types outside the list get a UUID suffix per
call and are evicted on finish. Note the default list makes
`browser_agent` sticky too.

Known integration gap (observed by this demo): with `enableTaskLoop=true`,
the task loop runs a subagent on its own effective session, whose inference
`agent_hint` reports a self-pointing parent. Management edits are unaffected.
The demo also uses `KVCacheConfig(5.0, 5.0, 5.0)` because the default 2s
action timeout can cancel queued actions when the single-core action executor
is shared across chains, tripping fail-open.
