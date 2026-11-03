---
description: ThreadPoolExecutor, CompletableFuture, Reactor, EventBus, and lock conventions for agent-core-java.
language: english
paths:
  - "src/main/java/com/openjiuwen/**/*.java"
---

# Concurrency Rules

## Three Layers — Do Not Mix at API Boundary

| Layer | Where | Return type | Bridge rule |
|---|---|---|---|
| `ThreadPoolExecutor` | Background workers, scheduled tasks | `void` / side-effects | — |
| `CompletableFuture` | `TeamBackend`, team operations | `CompletableFuture<Boolean>` | `.join()` at sync boundaries only |
| Reactor (`Mono`/`Flux`) | `BaseAgent`, `RunnerImpl`, streaming | `Mono<T>` / `Flux<T>` | `.subscribe()` |

Do not introduce `Mono.fromFuture(...)` or `Future.fromMono(...)` bridges
in new code. `TeamBackend` stays on `CompletableFuture`;
`BaseAgent`/`RunnerImpl` stay on Reactor.

## ThreadPoolExecutor

```java
new ThreadPoolExecutor(core, max, keepAlive, SECONDS,
        new LinkedBlockingQueue<>(capacity),  // bounded, always
        threadFactory,                         // named, daemon, with UncaughtExceptionHandler
        new ThreadPoolExecutor.CallerRunsPolicy());
```

- **Bounded queue required** — unbounded hides backpressure.
- **Named daemon threads** — `<subsystem>-<role>[-<id>]` prefix.
  Non-daemon only if the pool must keep JVM alive (document why).
- **Banned** (`X.CON.06`): `Executors.newCachedThreadPool()`,
  `newFixedThreadPool()`, `newSingleThreadExecutor()`.
- **Scheduled**: use `ScheduledThreadPoolExecutor` directly, not
  `Executors.newScheduledThreadPool()`.

## CompletableFuture

- `thenApply` / `thenCompose` / `exceptionally` — standard chaining.
- `supplyAsync` / `runAsync` — offload to worker (used in `BashTool`,
  `CodeTool` for subprocess I/O).
- **`.join()` allowed** at synchronous entry points (`dispatchTask`,
  `destroyTeam`, `startup`, `approvePlan`).
- **`.join()` forbidden** inside `CoordinationKernel.dispatch` and
  event handlers — runs on single EventBus thread, blocking stalls
  the coordination pipeline.

## Reactor

- Subscribe on `Schedulers.boundedElastic()` — never global parallel.
- Use `ReactiveAdapters` for standard blocking→reactive conversions.
- `Flux.usingWhen(...)` for AutoCloseable iterators (SSE streams).
- `Flux.defer(...).doFinally(...)` for cleanup (see
  `RunnerImpl.deferWithSessionCleanup`).

## EventBus — Single-Threaded

- One loop thread (`agent-teams-coordinator-<role>`, daemon). Wake
  callback (`CoordinationKernel::dispatch`) runs on this thread.
- All handlers execute serially on the loop thread. Async work
  belongs in `TeamBackend.publishTeamEvent` → messager layer.
- Shutdown via sentinel event, not `Thread.interrupt()`.
- **Do not parallelize inside coordination handlers.**

## Pinned Session ID

Team event topics must use `TeamBackend.teamSessionId` (pinned at
construction), never the thread-local `SpawnContext.getSessionId()`.
This ensures leader and teammates agree on the same topic.

## Lock Selection

| Mechanism | When |
|---|---|
| `synchronized` (method) | Simple lifecycle guards |
| `synchronized` (DCL) | Singleton init — requires `volatile` (`X.CON.02`) |
| `ReentrantLock` | Fine-grained mutation guards |
| `volatile` | Cross-thread flag visibility |
| `AtomicBoolean`/`AtomicReference` | Lock-free flag/reference |
| `ConcurrentHashMap` | Shared mutable maps — use `computeIfAbsent`, not `get`+`put` (`X.CON.04`) |

No `StampedLock` or `ReadWriteLock` in this project.

## Anti-patterns

- `.join()` inside event handlers — blocks EventBus thread
- `Mono.fromFuture(cf)` — crosses layer boundary
- `HashMap` concurrent writes — use `ConcurrentHashMap` (`X.CON.05`)
- Thread without name or `UncaughtExceptionHandler` — silent failure
- `SpawnContext.getSessionId()` for team topics — breaks leader-teammate agreement
