# Performance Tuning Real-World Cases

This file supplements SKILL.md's practical content, providing 6 end-to-end optimization cases, aligned with jvm-troubleshoot's troubleshooting_cases.md. Read on demand when users ask "how to optimize" or need a reference for a complete tuning workflow.

Each case follows the "problem -> measure baseline -> locate -> optimize -> verify" workflow.

## Case 1: Stream to for Loop

### Problem

Log parsing service processes 100k records/second, CPU consistently at 70%, needs to drop below 50%.

Code example:
```java
public int sum(List<Integer> values) {
    return values.stream()
        .filter(v -> v > 0)
        .mapToInt(Integer::intValue)
        .sum();
}
```

### Measure Baseline (JMH)

```java
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
@State(Scope.Thread)
public class StreamBenchmark {

    @Param({"100", "1000", "10000"})
    int size;

    List<Integer> list;

    @Setup
    public void setup() {
        list = IntStream.range(0, size).boxed().collect(Collectors.toList());
    }

    @Benchmark
    public int stream() {
        return list.stream()
            .filter(v -> v > 0)
            .mapToInt(Integer::intValue)
            .sum();
    }

    @Benchmark
    public int forLoop() {
        int sum = 0;
        for (int v : list) {
            if (v > 0) sum += v;
        }
        return sum;
    }
}
```

### Results (Example)

```
Benchmark                      (size)   Mode  Cnt     Score   Error  Units
StreamBenchmark.stream             100  thrpt    10    12.345 +/- 0.5  ops/ms
StreamBenchmark.forLoop            100  thrpt    10    45.678 +/- 1.2  ops/ms  <- 3.7x faster
StreamBenchmark.stream           10000  thrpt    10     1.234 +/- 0.1  ops/ms
StreamBenchmark.forLoop          10000  thrpt    10     5.678 +/- 0.3  ops/ms  <- 4.6x faster
```

### Locate

Reasons Stream is slow:
1. `stream()` creates Stream object -> overhead
2. `filter` / `mapToInt` boxing/unboxing -> boxing overhead
3. Internal Spliterator iteration -> indirect invocation

### Optimize

```java
// Use for for simple iteration
public int sum(List<Integer> values) {
    int sum = 0;
    for (int v : values) {
        if (v > 0) sum += v;
    }
    return sum;
}
```

### Verify

- JMH measurement: for is 3.7-4.6x faster than Stream
- Production CPU dropped from 70% to 45%
- Note: for is not always faster; complex chained transforms are more readable with Stream

### Reflection

- Stream is not a performance optimization tool; it's a readability tool
- Use for for simple iteration, Stream for complex transforms
- Don't blindly use `parallelStream` -- small collections are actually slower

## Case 2: HashMap Capacity Tuning + Lock to LongAdder

### Problem

Rate limiter counter: 1000 QPS + 200 concurrent threads -> `AtomicInteger` increment has CAS retries on multi-core CPU, throughput bottleneck.

Code:
```java
public class RateLimiter {
    private final AtomicInteger count = new AtomicInteger(0);
    private final Map<String, Integer> stats = new HashMap<>();
    private final Object lock = new Object();

    public void increment() {
        count.incrementAndGet();
        synchronized(lock) {
            stats.put("total", count.get());
        }
    }
}
```

### Measure Baseline (JMH)

```java
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
@State(Scope.Group)
public class CounterBenchmark {

    AtomicInteger atomic = new AtomicInteger();
    LongAdder adder = new LongAdder();

    @Benchmark
    @Group("atomic")
    @GroupThreads(8)
    public void atomicInc() {
        atomic.incrementAndGet();
    }

    @Benchmark
    @Group("adder")
    @GroupThreads(8)
    public void adderInc() {
        adder.increment();
    }
}
```

### Results

```
Benchmark                          Mode  Cnt     Score    Error  Units
CounterBenchmark.atomic              thrpt    10    234.5  +/- 12.3  ops/ms
CounterBenchmark.adder               thrpt    10   1234.5  +/- 45.6  ops/ms  <- 5.3x faster
```

### Locate

- `AtomicInteger` CAS retries under high contention -> spin overhead
- `synchronized + HashMap` coarse lock granularity, limited concurrency
- `HashMap` default capacity 16 + loadFactor 0.75 -> frequent resizing

### Optimize

```java
public class RateLimiter {
    // High-concurrency counting uses LongAdder
    private final LongAdder count = new LongAdder();

    // High-concurrency map uses ConcurrentHashMap
    private final ConcurrentHashMap<String, Long> stats = new ConcurrentHashMap<>(64);

    public void increment() {
        count.increment();
        stats.compute("total", (k, v) -> v == null ? 1 : v + 1);
    }
}
```

### Further: HashMap Capacity Tuning

```java
// Original code
Map<String, Integer> stats = new HashMap<>();  // Default capacity 16

// Known size (e.g., 100 keys) -> specify explicitly
Map<String, Integer> stats = new HashMap<>(100 / 0.75 + 1);  // Avoid resizing
```

Or for higher concurrency scenarios:
```java
ConcurrentHashMap<String, Long> stats = new ConcurrentHashMap<>(64);
```

### Verify

- LongAdder vs AtomicInteger: 5.3x faster
- ConcurrentHashMap vs HashMap + synchronized: 5x+ concurrency
- HashMap with tuned capacity has no resizing pauses

### Reflection

- `AtomicInteger` suits low contention, `LongAdder` suits high contention
- `HashMap + synchronized` is a dead end for high concurrency -> `ConcurrentHashMap`
- HashMap with known size -> explicit capacity, avoid resizing

## Case 3: Reflection to MethodHandle

### Problem

Serialization framework processes 50k objects/second; reflection `Method.invoke` is the bottleneck, CPU at 40%.

Code:
```java
public class Serializer {
    public Object getField(Object obj) throws Exception {
        Method m = obj.getClass().getMethod("getValue");  // Reflective lookup each time
        return m.invoke(obj);
    }
}
```

### Measure Baseline (JMH)

```java
@State(Scope.Thread)
public class ReflectionBenchmark {

    Method method;
    MethodHandle mh;
    Function<MyObject, String> lambda;

    @Setup
    public void setup() throws Exception {
        method = MyObject.class.getMethod("getValue");
        method.setAccessible(true);  // Disable access checks

        MethodHandles.Lookup lookup = MethodHandles.lookup();
        mh = lookup.findVirtual(MyObject.class, "getValue",
            MethodType.methodType(String.class));

        // LambdaMetafactory convert to Function
        // (Simplified: actual code is more complex, see code_level_optimization.md)
    }

    @Benchmark
    public String reflectNoCache() throws Exception {
        // Anti-pattern: reflective Method lookup each time
        Method m = obj.getClass().getMethod("getValue");
        return (String) m.invoke(obj);
    }

    @Benchmark
    public String reflectCached() throws Exception {
        return (String) method.invoke(obj);
    }

    @Benchmark
    public String methodHandle() throws Throwable {
        return (String) mh.invoke(obj);
    }
}
```

### Results

```
Benchmark                          Mode  Cnt     Score    Error  Units
ReflectionBenchmark.reflectNoCache    thrpt    10    45.6  +/- 2.3  ops/us
ReflectionBenchmark.reflectCached      thrpt    10   234.5  +/- 12.3  ops/us  <- 5x
ReflectionBenchmark.methodHandle       thrpt    10  1234.5  +/- 45.6  ops/us  <- 27x
```

### Locate

Reasons reflection is slow:
1. `getMethod` scans method table each time -> large overhead
2. `invoke` internal parameter checking (unless `setAccessible(true)`)
3. Boxing/unboxing
4. JIT has difficulty optimizing (virtual call)

### Optimize

```java
public class Serializer {
    private static final MethodHandle MH;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            MH = lookup.findVirtual(MyObject.class, "getValue",
                MethodType.methodType(String.class));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public Object getField(Object obj) throws Throwable {
        return MH.invoke(obj);
    }
}
```

**Further** (LambdaMetafactory):
```java
// Convert MethodHandle to Lambda, no reflection overhead at runtime
Function<MyObject, String> getter = (Function<MyObject, String>) LambdaMetafactory.metafactory(
    lookup, "apply", MethodType.methodType(Function.class),
    MethodType.methodType(Object.class, Object.class),
    MH, MethodType.methodType(String.class, MyObject.class)
).getTarget().invoke();

// Usage: close to direct call
String val = getter.apply(obj);
```

### Verify

- Cached Method reflection: 5x speedup
- MethodHandle: 27x speedup (vs uncached)
- LambdaMetafactory: close to direct call

### Reflection

- Reflection must cache Method/Field
- MethodHandle is 5-10x faster than `Method.invoke`
- LambdaMetafactory is close to direct call
- Large frameworks (Spring/Hibernate) use ByteBuddy/CGLIB bytecode generation, faster but more complex

## Case 4: JVM Parameter Tuning (Container Microservice)

### Problem

Spring Boot 3 microservice (JDK 17), deployed in 4GB memory container. P99 latency 300ms after startup, target below 100ms.

### Measure Baseline

- Startup time: 25s
- P99: 300ms
- GC log: Full GC every 5 minutes, Young GC 200ms pause
- `jcmd <pid> VM.flags`: Default G1, heap default value (about 1GB)

### Locate

1. **Heap too small**: Container 4GB, JVM defaults based on physical machine -> heap about 1GB, frequent GC
2. **No warmup**: JIT hasn't compiled to tier 4, methods all run in interpreted mode
3. **GC log not enabled**: Difficult to debug

### Optimize

**Step 1: Container heap ratio + GC log**

```
-XX:+UseContainerSupport
-XX:InitialRAMPercentage=50.0
-XX:MaxRAMPercentage=75.0
-XX:+UseG1GC
-XX:MaxGCPauseMillis=200
-XX:MetaspaceSize=256m
-XX:MaxMetaspaceSize=512m
-Xlog:gc*:file=/var/log/gc.log:time,uptime:filecount=10,filesize=10M
-XX:+HeapDumpOnOutOfMemoryError
-XX:HeapDumpPath=/var/log/dumps/
-XX:+AlwaysPreTouch
-XX:+DisableExplicitGC
```

**Changes**:
- Heap from 1GB -> 3GB (container 4GB x 75%)
- GC log enabled
- AlwaysPreTouch pre-touches memory at startup, reduces runtime page fault pauses

**Step 2: JFR monitoring**

```
-XX:StartFlightRecording=filename=/var/log/jfr/app.jfr,maxage=1h,maxsize=100M,settings=profile
-XX:FlightRecorderOptions=stackdepth=64
```

**Step 3: Application-level optimization**

- Warmup: Send mock traffic for 5 minutes after startup
- Logging framework: Async logging (Logback `AsyncAppender`)
- HTTP client: Connection pool (OkHttp)

### Verify

| Metric | Baseline | After Optimization |
|---|---|---|
| Startup time | 25s | 22s (warmup takes 5s) |
| P99 latency | 300ms | 85ms |
| Full GC | Every 5 minutes | 0 (eliminated) |
| Young GC pause | 200ms | 80ms |
| Heap occupancy | 90% (frequent GC) | 60% |

**JFR analysis**: Found 5% time still in `Object.wait` (connection pool wait); increased HikariCP connection pool 10 -> 20, P99 further dropped to 70ms.

### Reflection

- Must use `UseContainerSupport` + RAM percentage in containers
- GC log + JFR must be enabled; no monitoring, no optimization
- Warmup helps long-running services; for short-running applications use GraalVM
- Don't tune too many parameters at once; verify in stages

## Case 5: Escape Analysis Invalidation Scenario

### Problem

Log formatting method creates `StringBuilder` each call; should be eliminated by escape analysis, but GC log shows allocation hotspot.

Code:
```java
public String format(LogRecord record) {
    StringBuilder sb = new StringBuilder();
    sb.append(record.getTimestamp());
    sb.append(" ");
    sb.append(record.getLevel());
    sb.append(" ");
    sb.append(record.getMessage());
    return sb.toString();
}
```

### Locate

Escape analysis should determine `sb` doesn't escape -> scalar replacement. But JFR shows `StringBuilder` is still allocated on heap.

**Reason**: After C2 compiles the method, although `sb` doesn't escape the method, `sb.toString()` creates a new String object that does escape the method -> JIT conservatively doesn't eliminate `sb`.

### Verify JIT Behavior

```bash
# Add at startup
-XX:+UnlockDiagnosticVMOptions -XX:+PrintInlining -XX:+PrintEscapeAnalysis
```

Output:
```
 EscapeAnalysis:
   StringBuilder sb = new -- ArgEscape (toString returns)
```

### Optimize

**Option 1**: Direct string concatenation (compiler auto-uses StringBuilder, but simpler):
```java
public String format(LogRecord record) {
    return record.getTimestamp() + " " + record.getLevel() + " " + record.getMessage();
}
```

**Option 2**: Make method final, let JIT inline more aggressively:
```java
public final String format(LogRecord record) { ... }
```

**Option 3**: Use `String.format` (worse performance, avoid)

### Verify

- JFR: StringBuilder allocation hotspot disappeared
- GC frequency reduced 20%

### Reflection

- Escape analysis is not omnipotent; may fail in complex scenarios
- Simple string concatenation with `+` is more thoroughly optimized by the compiler
- Use `-XX:+PrintEscapeAnalysis` to verify on critical paths

## Case 6: ZGC Switch (Large Heap Low Latency)

### Problem

Cache service heap 32GB, G1 Young GC pause 300ms, business P99 stuck at 500ms. SLA requires P99 < 100ms.

### Measure Baseline

- G1: Young GC pause 300ms, Full GC every 1 hour for 1.5s
- P99 latency: 500ms (requests pile up during GC)

### Optimize

```
-Xms32g -Xmx32g
-XX:+UseZGC                       # JDK 17 non-generational ZGC
-XX:ZAllocationSpikeTolerance=2
-XX:ConcGCThreads=8              # 16-core machine, half for GC
-XX:MetaspaceSize=512m
-XX:MaxMetaspaceSize=1g
-Xlog:gc*:file=/var/log/gc.log:time,uptime:filecount=20,filesize=50M
```

### Verify

| Metric | G1 | ZGC |
|---|---|---|
| GC pause | 300ms | < 5ms |
| P99 latency | 500ms | 80ms |
| GC throughput | 95% | 98% |
| CPU usage | 60% | 75% (concurrent GC uses more) |

**Note**: JDK 17 ZGC is non-generational, CPU usage is slightly higher. If heap is larger (64GB+) and throughput-sensitive, upgrade to JDK 21 for generational ZGC.

### Reflection

- Large heap + low latency -> ZGC
- ZGC is not free; higher CPU usage trades for lower pause
- JDK 17 non-generational ZGC; JDK 21+ generational ZGC has better performance

## General Tuning Methodology

### Workflow

1. **Measure baseline**: JMH / JFR / GC log to get current data
2. **Locate bottleneck**: Flame graph / JFR to find hotspots
3. **Optimize**: Change only one variable at a time
4. **Verify**: Compare against baseline, look at confidence interval
5. **Production validation**: Microbenchmark results may not represent real load; A/B test

### Anti-patterns

- Tuning without measuring -> mostly no benefit
- Changing multiple variables -> don't know which one worked
- Deploying based on microbenchmark alone -> doesn't represent real load
- Over-optimizing -> 5% benefit for 50% time investment, not worth it

### Benefit Ranking (General Case)

1. **Algorithm layer**: O(n^2) -> O(n log n) benefit >> everything else
2. **Data structure layer**: ArrayList vs LinkedList, HashMap vs TreeMap
3. **Code layer**: Loop-invariant code motion, object reuse, StringBuilder caching
4. **JVM layer**: GC parameters, heap size
5. **Hardware layer**: CPU / memory / SSD

Work from top to bottom; diminishing returns at lower layers.

## Reference Documentation

- In-project reactive troubleshooting cases: `../../jvm-troubleshoot/references/troubleshooting_cases.md`
- In-project JMH details: `jmh_profiling.md`
- In-project code-level optimization: `code_level_optimization.md`
- JMH official samples: `https://github.com/openjdk/jmh/tree/main/jmh-samples/src/main/java/org/openjdk/jmh/samples`
