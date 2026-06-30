# Graph Memory Examples Baseline

The current Java baseline aligns to the graph-store/checkpoint layer that backs graph-memory style flows:

- create checkpoint state
- persist via `InMemoryStore`
- reload and summarize checkpoint state

Suggested verification:

```bash
mvn -Dtest=GraphMemoryExampleSupportTest test
```
