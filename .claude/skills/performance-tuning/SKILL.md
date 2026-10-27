---
name: performance-tuning
description: Java performance tuning guide (JDK 17 baseline). Proactively apply when users are doing performance optimization, writing high-performance code, selecting JVM parameters, tuning GC collectors, running JMH benchmarks, using Profiling tools, optimizing collections/Stream/locks/reflection, tuning JIT/inlining, or optimizing AI agent frameworks (LLM calls, JSON serialization, reflection-based dynamic invocation, prompt concatenation). Covers: JVM parameter tuning (heap/stack/GC collector selection), code-level performance (collection selection, Stream performance, reflection optimization, escape analysis, lock optimization, Records, sealed class, Vector API), compilation optimization (JIT, inlining, tiered compilation, CDS/GraalVM), performance testing (JMH benchmarks, JFR, async-profiler, Arthas), AI agent framework-specific optimization (OkHttp connection pool, Jackson streaming parsing, MethodHandle tool invocation, prompt StringBuilder, CompletableFuture orchestration). Related keywords: performance tuning, JMH, benchmark, JIT, inlining, Profiling, escape analysis, stack allocation, lock optimization, lock coarsening, lock elision, TLAB, Stream performance, collection selection, reflection optimization, Records, sealed, Vector API, --add-opens, JFR, flame graph, ZGC, G1, GraalVM, LLM call, OkHttp, connection pool, Jackson, JSON serialization, prompt concatenation, MethodHandle, LambdaMetafactory, agent framework. Complements jvm-troubleshoot -- jvm-troubleshoot is "how to investigate when problems occur" (reactive), while this is "how to write fast code" (proactive). Not applicable to: coding standard issues (use coding-standard), JVM production incident troubleshooting (use jvm-troubleshoot), refactoring process governance (use refactor-guide).
---

# Java Performance Tuning Guide (JDK 17 baseline)

This skill uses **JDK 17 as the baseline**, organized by "optimization layer -> decision table -> optimization points", covering 4 layers of performance optimization. All optimizations should follow the principle of **measure first, then tune** -- never guess based on experience.

**Boundary with jvm-troubleshoot**:
- jvm-troubleshoot: Production issues already occurred (OOM/CPU 100%/deadlock) -> follow "symptom -> diagnosis -> fix" to investigate
- This skill: Code/configuration hasn't broken yet, but you want it faster -> follow "layer -> selection -> optimization" to prevent issues

## Optimization Layer Quick Reference

| Optimization Target | Jump To | Detailed Documentation |
|---|---|---|
| Select JVM parameters, tune GC collector | [JVM Parameter Tuning](#jvm-parameter-tuning) | `references/jvm_params.md`, `references/gc_tuning.md` |
| Select collections / optimize Stream / optimize locks / optimize reflection when writing code | [Code-Level Performance](#code-level-performance) | `references/code_level_optimization.md` |
| Understand JIT inlining, tiered compilation, warmup | [Compilation Optimization](#compilation-optimization) | `references/jit_compiler.md` |
| Run benchmarks, use Profiling to locate bottlenecks | [Performance Testing & Profiling](#performance-testing--profiling) | `references/jmh_profiling.md` |
| Reference complete tuning workflow cases | [Real-World Cases](#real-world-cases) | `references/tuning_cases.md` |
| AI agent framework-specific optimization (LLM calls / JSON / reflection / prompt) | [AI Agent Framework Optimization](#ai-agent-framework-optimization) | `references/ai_agent_optimization.md` |
| Not sure which layer is the bottleneck | Start with [Performance Optimization Decision Flow](#performance-optimization-decision-flow) | - |

## Performance Optimization Decision Flow

**Always follow this order, never skip steps**:

1. **Measure baseline first**: Use JMH or Profiling to get current performance numbers (throughput/latency/P99). No data, no optimization.
2. **Locate bottleneck layer**: Use Profiling (async-profiler / JFR) to see which of CPU/memory/locks/IO is the hotspot.
3. **Optimize from the top layer down**: Business algorithm > Data structure > Code style > JVM parameters > Hardware
   - Changing algorithm from O(n^2) to O(n log n) yields far more benefit than tuning JVM parameters.
4. **Change only one variable at a time**: After each change, re-measure and compare before/after data.
5. **Don't stop until the target is met**: Performance optimization is an iterative process, not a one-shot deal.

**Anti-patterns**:
- Adjusting `-Xmx` and GC parameters without measuring -> mostly no benefit
- Deploying code changes without running JMH -> no idea if it got faster or slower
- Assuming Stream/parallelStream is always faster -> many scenarios are slower than for loops
- Forcing reflection in JDK 17 without `--add-opens` -> `InaccessibleObjectException`

## JVM Parameter Tuning

### Heap Size Decision

| Scenario | -Xms / -Xmx | Reason |
|---|---|---|
| Microservice / Container | `-Xms = -Xmx`, 50-75% of container memory | Avoid heap dynamic expansion pauses |
| Batch processing | `-Xms < -Xmx` | Gradual growth, save memory |
| High throughput low latency | `-Xms = -Xmx`, heap not too large | Large heap = long GC time |
| Large cache application | Large heap + ZGC | Reduce Full GC; ZGC pause does not grow with heap |

**Rules of thumb**:
- Bigger heap is not always better. 32GB+ disables compressed oops (`-XX:+UseCompressedOops`), object references become 8 bytes, actually consuming more memory.
- Inside containers: `-XX:+UseContainerSupport` (enabled by default in JDK 17), lets JVM recognize container memory limits.
- Using percentages is more convenient: `-XX:InitialRAMPercentage=50.0 -XX:MaxRAMPercentage=75.0`

### Stack Size Tuning

| Scenario | -Xss | Reason |
|---|---|---|
| Default | 1M (JDK 17 Linux x64) | Sufficient for most applications |
| Deep recursion | 2M-4M | Prevent StackOverflowError |
| Many threads | 256K | Save thread memory (each thread gets its own stack) |

### GC Collector Selection

JDK 17 stable collectors:

| Scenario | Heap Size | Pause Requirement | Recommended Collector | Key Parameters |
|---|---|---|---|---|
| Microservice / Default | < 4GB | Within 200ms | G1 (JDK 17 default) | `-XX:MaxGCPauseMillis=200` |
| Medium application | 4-8GB | Within 200ms | G1 | `-XX:MaxGCPauseMillis=200 -XX:G1HeapRegionSize=8m` |
| Large low-latency | 8-32GB | Within 50ms | ZGC | `-XX:+UseZGC -XX:ZAllocationSpikeTolerance=2` |
| Very large heap | 32GB-16TB | Sub-millisecond | ZGC | Same as above; increase `-XX:ConcGCThreads` if needed |
| High throughput batch | Any | No pause requirement | Parallel | `-XX:+UseParallelGC -XX:ParallelGCThreads=N` |
| Low pause + concurrent marking | Medium | Within 100ms | Shenandoah (OpenJDK) | `-XX:+UseShenandoahGC` |

**JDK 17 ZGC key point**: Non-generational version (generational ZGC is stable only in JDK 21+). Heap > 32GB + throughput-sensitive -> upgrade to JDK 21.

**Selection decision**:
1. JDK 17 + heap >= 8GB + low latency -> prefer ZGC
2. JDK 17 + heap < 8GB -> G1 (default)
3. Throughput first (offline computation) -> Parallel
4. Heap > 32GB + throughput-sensitive -> upgrade to JDK 21 generational ZGC

**Do not**:
- Use ZGC with heap < 4GB -> no benefit
- Switch collectors without measuring -> might be slower
- Set `-XX:MaxGCPauseMillis=1` -> mostly unachievable, causes frequent GC instead

See `references/gc_tuning.md` for more GC tuning details.

### Key JVM Parameter Quick Reference

**Monitoring (commonly enabled in JDK 17 production)**:
```
-Xlog:gc*:file=gc.log:time,uptime:filecount=10,filesize=10M   # GC log
-XX:+HeapDumpOnOutOfMemoryError                                # Auto dump on OOM
-XX:HeapDumpPath=/var/log/dumps/                              # dump path
-XX:ErrorFile=/var/log/hs_err_%p.log                           # Fatal error log
-XX:StartFlightRecording=filename=/var/log/jfr/app.jfr,maxage=1h,maxsize=100M,settings=profile  # JFR always on
```

**Common tuning parameters**:
```
-Xms4g -Xmx4g                              # Initial/max heap, equal to avoid expansion
-XX:MetaspaceSize=256m                     # Initial Metaspace
-XX:MaxMetaspaceSize=512m                  # Metaspace upper limit
-XX:+DisableExplicitGC                     # Disable System.gc()
-XX:+AlwaysPreTouch                        # Pre-touch all pages at startup
-XX:ParallelGCThreads=N                    # GC thread count, default CPU cores * 5/8
-XX:ConcGCThreads=N                        # Concurrent GC thread count, default ParallelGCThreads * 1/4
```

**JIT-related**:
```
-XX:+TieredCompilation                     # Tiered compilation (enabled by default in JDK 17)
-XX:ReservedCodeCacheSize=256m             # JIT code cache; too small triggers deoptimization
```

**Module system (JDK 17 must-know)**:
```
--add-opens java.base/java.lang=ALL-UNNAMED            # Reflective access to String internals
--add-opens java.base/java.util=ALL-UNNAMED            # Reflective access to collection internals
--add-opens java.base/java.nio=ALL-UNNAMED             # Netty reflective access to NIO
```

See `references/jvm_params.md` for the complete JVM parameter list.

## Code-Level Performance

### Collection Selection

Select collections based on access patterns; don't default to `ArrayList`/`HashMap`:

| Scenario | Choose | Don't Choose |
|---|---|---|
| Frequent random reads, few writes | `ArrayList` / `CopyOnWriteArrayList` (many reads, very few writes) | `LinkedList` |
| Frequent head insert/delete | `ArrayDeque` / `LinkedList` | `ArrayList` (head is O(n)) |
| Frequent tail insert | `ArrayList` (amortized O(1)) | `LinkedList` (high node overhead) |
| High concurrent reads + occasional writes | `ConcurrentHashMap` | `HashMap` + `synchronized` |
| High concurrent reads + very few writes | `CopyOnWriteArrayList` | `Vector` |
| Key is enum | `EnumMap` (array-based, fastest) | `HashMap` |
| Enum set | `EnumSet` (bit vector) | `HashSet` |
| LRU cache | `LinkedHashMap` + `removeEldestEntry` | `HashMap` + hand-written |

**HashMap with known size, adjust capacity**: `new HashMap<>(expectedSize / 0.75 + 1)` to avoid resizing.

See `references/code_level_optimization.md` for more collection optimization details.

### Stream Performance

Stream is **not a performance optimization tool**; it is a readability tool. Using it incorrectly can be slower:

| Scenario | Stream | for Loop |
|---|---|---|
| Simple iteration | Slower (boxing/iterator overhead) | Faster |
| Complex chained filter/map/reduce | Comparable or slightly slower | Harder to write but faster |
| Large data parallel | `parallelStream` may be faster | Single-threaded for |
| Small collection (< 1000) | Slower | Faster |

**Stream anti-patterns**:
- `stream().collect(toList()).stream()` -- redundant boxing
- `list.parallelStream()` on small collections -- ForkJoin splitting overhead > benefit
- `stream.forEach(x -> sb.append(x))` -- use `collect(joining(","))`

### Reflection Optimization

Reflection is slow but useful. Optimization path:

| Optimization Level | Approach | Benefit |
|---|---|---|
| 0: Cache Method/Field | `static final Method M = ...` | 10x+ |
| 1: setAccessible(true) | Disable access checks | 2-5x |
| 2: MethodHandle | `MethodHandles.lookup()` | Close to direct call |
| 3: LambdaMetafactory | Convert MethodHandle to Lambda | Close to direct call |
| 4: Bytecode generation | ByteBuddy/CGLIB | Fastest, but has generation cost |

**JDK 17 module system**: Reflective access to non-exported packages requires `--add-opens`, otherwise `InaccessibleObjectException`.

### Lock Optimization

Finer locks are not always better. Lock optimization has 4 levels:

| Level | Technique | Applicable |
|---|---|---|
| **Lock elision** | `-XX:+DoEscapeAnalysis` (enabled by default in JDK 17) | Local objects with no contention; JIT auto-eliminates |
| **Lock coarsening** | JIT merges adjacent synchronized blocks | `synchronized` inside loop merged outside loop |
| **Lock granularity** | `ConcurrentHashMap` segmentation -> red-black tree | High concurrent writes |
| **Lock-free** | `Atomic*` / `LongAdder` / `VarHandle` | Counter/statistics scenarios |

**Lock selection decision**:
| Scenario | Choose | Don't Choose |
|---|---|---|
| Low contention | `synchronized` | `ReentrantLock` (higher overhead) |
| Need timeout/interrupt | `ReentrantLock.tryLock(timeout)` | `synchronized` (not supported) |
| Many reads, few writes | `StampedLock` (optimistic read) | `ReentrantReadWriteLock` |
| Fair queuing | `ReentrantLock(fair=true)` | `synchronized` (unfair) |
| Counter | `LongAdder` | `AtomicLong` (slow under high concurrency) |

### JDK 17 New Features

| Feature | Performance Benefit | Usage |
|---|---|---|
| **Records** (JDK 14+ stable) | Compiler-generated equals/hashCode/toString, no reflection | `public record Point(int x, int y) {}` |
| **Sealed class** (JDK 17 stable) | Compile-time exhaustive matching, helps JIT devirtualization | `sealed interface Shape permits Circle, Rectangle, Triangle {}` |
| **Vector API** (incubator) | Explicit SIMD, more stable than C2 auto-vectorization | `--add-modules jdk.incubator.vector` |
| **Foreign Memory API** (incubator) | Off-heap memory access, replaces Unsafe | `--add-modules jdk.incubator.foreign` |
| **Compact Strings** (JDK 9+ default) | ASCII string memory halved | Enabled by default, no configuration needed |
| **CDS / AppCDS** | Startup acceleration 30-50% | `java -Xshare:dump` |
| **GraalVM Native Image** | AOT compilation, millisecond startup | Preferred for CLI / function computing |

**Virtual threads**: Only stable in JDK 21+, not available in JDK 17. JDK 17 alternative: use `CompletableFuture` / `Reactor`.

See `references/code_level_optimization.md` for more code-level optimization details.

## Compilation Optimization

### JIT Tiered Compilation

JDK 17 enables tiered compilation by default, with 5 tiers:

| Tier | Interpreter | C1 | C2 | Description |
|---|---|---|---|---|
| 0 | Yes | | | Interpreted execution, collects invocation counts |
| 1 | | Yes | | C1 compiled, no profiling |
| 2 | | Yes | | C1 + profiling |
| 3 | | Yes | | C1 + full profiling (most methods stop here) |
| 4 | | | Yes | C2 compiled, aggressive optimization (inlining/escape analysis/loop unrolling) |

**Warmup**:
- Method invocation count reaches threshold (default 10000) to trigger C1 -> C2 compilation
- Long-running applications (production services) stabilize after warmup; short-running applications (CLI/batch) may run entirely in interpreted mode
- Short-running applications should consider GraalVM Native Image or CDS

**Code cache**: `-XX:ReservedCodeCacheSize=256m` (JDK 17 default 240M); too small triggers deoptimization.

See `references/jit_compiler.md` for more JIT details.

### Inlining Optimization

Inlining is JIT's most important optimization. **Help inlining**:
- Don't write hot methods too large (< 35 bytecodes, `-XX:MaxInlineSize`)
- Add `final` to classes/methods to help JIT determine non-virtual calls -> inlining
- Use sealed class (JDK 17) to let JIT know all implementations -> monomorphic inlining
- Don't use complex inheritance hierarchies on hot paths (virtual methods are hard to inline)

**Anti-patterns**:
- Complex inheritance hierarchies on hot paths -> virtual method polymorphism, no inlining
- Using reflection on hot paths -> JIT has difficulty optimizing
- Lambda capturing variables on hot paths -> creates Lambda object each time

## Performance Testing & Profiling

### JMH Benchmarking

JMH is OpenJDK's Java benchmarking framework, the **only trustworthy** Java microbenchmark tool.

**When to use JMH**: Verify if a coding pattern is faster, measure method throughput/latency, compare before/after optimization.

**When not to use JMH**: Measure entire application performance (use Profiling + integration testing), measure IO-intensive scenarios (JMH is inaccurate), measure one-time initialization (not JMH's design goal).

**Minimal JMH usage**:
```java
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
@State(Scope.Thread)
public class CollectionBenchmark {

    @Param({"100", "1000", "10000"})
    int size;

    List<Integer> list;

    @Setup
    public void setup() {
        list = IntStream.range(0, size).boxed().collect(toList());
    }

    @Benchmark
    public int forEachSum() {
        int sum = 0;
        for (int x : list) sum += x;
        return sum;
    }

    @Benchmark
    public int streamSum() {
        return list.stream().mapToInt(Integer::intValue).sum();
    }
}
```

**JMH pitfalls**: Must use `@Fork(2)`, must have sufficient `@Warmup`, don't `new` objects inside benchmark methods, avoid dead code elimination (use `Blackhole`).

**JDK 17 JMH notes**:
- Reflective access to non-exported packages requires `--add-opens`: `@Fork(jvmArgsAppend = {"--add-opens", "java.base/java.lang=ALL-UNNAMED"})`
- Vector API benchmark requires `--add-modules jdk.incubator.vector`

See `references/jmh_profiling.md` for more JMH details.

### Profiling Tools

Select tools by granularity:

| Tool | Granularity | Scenario | Overhead |
|---|---|---|---|
| **JFR** (JDK Flight Recorder) | System-level + method-level | Production long-running, view CPU/memory/IO/locks | Very low (< 1%) |
| **async-profiler** | Method-level CPU/Alloc/Lock | Continuous sampling, flame graphs | Low (< 1%) |
| **JProfiler / YourKit** | Full-featured GUI | Local deep tuning | Medium |
| **VisualVM** | Overview | Local quick view | Medium |
| **JMH** | Method-level precise | Microbenchmark | High (dedicated run) |
| **Arthas** | Online diagnosis | Production dynamic trace | Medium |

**JDK 17 JFR advantages**: Streaming API (JDK 14+) enables real-time streaming consumption of events without disk persistence. `settings=profile` provides more complete events. Commonly enabled in production: `-XX:StartFlightRecording=...`.

**async-profiler flame graph**:
```bash
./profiler.sh -d 30 -f flame.html <pid>            # CPU
./profiler.sh -d 30 -e alloc -f alloc.html <pid>    # Allocation
./profiler.sh -d 30 -e lock -f lock.html <pid>      # Lock
```

### Performance Metrics System

| Metric | Meaning | Optimization Target |
|---|---|---|
| **Throughput** (QPS/TPS) | Requests processed per second | Higher is better |
| **Average latency** | Average request time | Lower is better (but skewed by long tail) |
| **P99 latency** | Time for 99% of requests | Lower is better (more realistic reflection of experience) |
| **P999 latency** | Time for 99.9% of requests | Look atch this for long-tail-sensitive scenarios |
| **GC pause time** | STW duration | Shorter is better |
| **GC throughput** | GC time proportion | < 5% |

**Metric pitfalls**: Only looking at average latency without P99 -> long tail is hidden; only looking at throughput without latency -> high QPS may rely on request queuing; drawing conclusions from a single benchmark -> use statistically significant multiple measurements.

## Real-World Cases

Reference 6 end-to-end cases with complete tuning workflows:

| Case | Optimization Point | Benefit |
|---|---|---|
| Stream to for loop | Simple iteration without Stream | 3.7-4.6x faster |
| HashMap capacity + LongAdder | High-concurrency counting + map capacity tuning | 5x+ concurrent throughput |
| Reflection to MethodHandle | Reflection must cache + MethodHandle | 27x faster |
| Container microservice tuning | Container heap ratio + JFR + warmup | P99 300ms -> 85ms |
| Escape analysis invalidation scenario | Simple string concatenation is better optimized by compiler | Allocation hotspot disappears |
| ZGC switch (large heap) | G1 -> ZGC | Pause 300ms -> < 5ms |

See `references/tuning_cases.md` for detailed cases.

## AI Agent Framework Optimization

agent-core-java is an AI agent framework with specific performance bottlenecks different from general Java applications.

**LLM calls account for 90%+ of time**. Code-level optimization has limited benefit, but don't let the code layer become a drag.

### Optimization Layers

| Layer | Optimization Point | Benefit |
|---|---|---|
| LLM HTTP call | OkHttp connection pool tuning, streaming response, timeout configuration, 429 retry | Save 100-300ms/request |
| JSON serialization | ObjectMapper singleton, streaming parsing for large payloads, disable unused features | 30-50% JSON speedup |
| Reflection / dynamic invocation | Tool Method caching, switch to MethodHandle, LambdaMetafactory | Reflection 5-27x faster |
| Prompt concatenation | StringBuilder with estimated capacity, don't use `+` in loops | Avoid unnecessary allocations |
| Async orchestration | Parallelize independent steps, use dedicated thread pool for LLM | Parallel 2-3x speedup |
| Caching | LLM response cache (temperature=0), Embedding cache | Hit saves LLM call |

### Agent Framework Performance Bottleneck Distribution

```
User input -> prompt concatenation -> LLM HTTP call (slowest) -> JSON parsing -> Tool invocation (reflection) -> Response concatenation
```

LLM HTTP calls account for 90%+ of time. Code-level optimization has limited benefit, but every microsecond of latency accumulates into milliseconds under high QPS.

See `references/ai_agent_optimization.md` for detailed optimization (including OkHttp / Jackson / reflection / prompt / async orchestration / caching strategies + practical checklist).

## Reference Entry Points

- **JVM Parameter Details**: `references/jvm_params.md` (JDK 17 default parameter table, heap/stack/Metaspace/CodeCache/JIT parameter values)
- **GC Tuning**: `references/gc_tuning.md` (proactive configuration, complements jvm-troubleshoot)
- **Code-Level Optimization**: `references/code_level_optimization.md` (collections/Stream/reflection/locks/escape analysis + JDK 17 new features)
- **JIT & Compilation Optimization**: `references/jit_compiler.md` (tiered compilation, inlining, Vector API, GraalVM)
- **JMH & Profiling**: `references/jmh_profiling.md` (JMH API, pitfalls, JFR, flame graph reading)
- **Real-World Cases**: `references/tuning_cases.md` (6 end-to-end optimization cases)
- **AI Agent Framework Optimization**: `references/ai_agent_optimization.md` (LLM calls, JSON serialization, reflection, prompt, async, caching)
- In-project incident troubleshooting: `../jvm-troubleshoot/SKILL.md` (reactive investigation after problems occur)
- In-project coding standards: `../coding-standard/SKILL.md`
- Official documentation: `https://docs.oracle.com/en/java/javase/17/docs/specs/man/`
- JMH official: `https://openjdk.org/projects/code-tools/jmh/`
- async-profiler: `https://github.com/async-profiler/async-profiler`
- JFR / JDK Mission Control: `https://github.com/openjdk/jmc`

## Usage

1. **Start with the decision flow**: Performance optimization follows "measure -> locate -> optimize -> re-measure"; don't skip steps
2. **Look up by layer**: JVM parameters -> Code-level -> Compilation -> Testing; use the "Optimization Layer Quick Reference" table to jump
3. **Read details on demand**: Each section points to the corresponding references file
4. **Does not replace jvm-troubleshoot**: For production issues (OOM/CPU 100%), use the incident troubleshooting skill
5. **Don't guess based on experience**: All optimizations must be verified with JMH or Profiling
6. **Don't fabricate when uncertain**: JVM/JIT behavior should be based on source code and official documentation; this skill does not replace formal documentation
