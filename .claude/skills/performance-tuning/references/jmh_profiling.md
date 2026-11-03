# JMH and Profiling Tools

This file supplements SKILL.md's performance testing section, providing in-depth coverage of JMH API, pitfalls, Profiling tool comparison, and flame graphs. Read on demand when users ask "how to measure performance" or "what tool to use for locating bottlenecks".

## JMH (Java Microbenchmark Harness)

OpenJDK's Java microbenchmark framework, the only trustworthy Java microbenchmark tool. Hand-written `System.currentTimeMillis()` measurements are inaccurate -- JIT optimization, warmup, and dead code elimination all distort results.

### Maven Dependency

```xml
<dependency>
    <groupId>org.openjdk.jmh</groupId>
    <artifactId>jmh-core</artifactId>
    <version>1.37</version>
</dependency>
<dependency>
    <groupId>org.openjdk.jmh</groupId>
    <artifactId>jmh-generator-annprocess</artifactId>
    <version>1.37</version>
    <scope>provided</scope>
</dependency>
```

### JMH Annotations

| Annotation | Purpose | Common Values |
|---|---|---|
| `@Benchmark` | Mark benchmark method | - |
| `@BenchmarkMode` | What to measure | `Mode.Throughput` / `Mode.AverageTime` / `Mode.SampleTime` |
| `@OutputTimeUnit` | Time unit | `TimeUnit.MILLISECONDS` / `TimeUnit.NANOSECONDS` |
| `@Warmup` | Warmup | `iterations=5, time=1` |
| `@Measurement` | Formal measurement | `iterations=5, time=1` |
| `@Fork` | JVM process count | `2` (required, isolates JIT) |
| `@State` | State scope | `Scope.Thread` / `Scope.Benchmark` / `Scope.Group` |
| `@Param` | Parameterization | `{"100", "1000", "10000"}` |
| `@Setup` | Initialization | `Level.Trial` / `Level.Invocation` / `Level.Iteration` |
| `@TearDown` | Cleanup | Same as above |
| `@CompilerControl` | Control JIT | `CompilerControl.Mode.DONT_INLINE` |

### Basic Example

```java
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
@State(Scope.Thread)
public class ListBenchmark {

    @Param({"100", "1000", "10000"})
    int size;

    List<Integer> list;

    @Setup(Level.Trial)
    public void setup() {
        list = IntStream.range(0, size).boxed().collect(Collectors.toList());
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

    @Benchmark
    public int parallelStreamSum() {
        return list.parallelStream().mapToInt(Integer::intValue).sum();
    }

    public static void main(String[] args) throws Exception {
        org.openjdk.jmh.Main.main(args);
    }
}
```

Run: `mvn clean install && java -jar target/benchmarks.jar`

### JMH API Advanced

#### Blackhole (Avoid Dead Code Elimination)

JIT eliminates code whose results are unused. JMH provides `Blackhole` to consume results:

```java
@Benchmark
public void consumeResult(Blackhole bh) {
    bh.consume(list.size());
    bh.consume(list.get(0));
}
```

#### @State State Objects

State objects are shared between benchmark methods:

```java
@State(Scope.Thread)
public static class MyState {
    public int x;
    public List<Integer> list;

    @Setup
    public void setup() {
        list = IntStream.range(0, 1000).boxed().collect(Collectors.toList());
    }
}

@Benchmark
public int benchmark(MyState state) {
    return state.list.get(state.x);
}
```

#### @Param Parameterization

```java
@Param({"100", "1000", "10000", "100000"})
public int size;

@Param({"true", "false"})
public boolean useParallel;
```

JMH runs all combinations.

#### @CompilerControl Control Inlining

```java
@Benchmark
@CompilerControl(CompilerControl.Mode.DONT_INLINE)
public int noInline() { ... }
```

Use for debugging "performance difference before/after inlining".

### JMH Pitfalls

#### 1. Not Using @Fork

```java
// Wrong: single process, JIT state pollution
@Fork(0)  // or no @Fork
```

Correct: `@Fork(2)` minimum.

#### 2. Insufficient @Warmup

```java
// Wrong: insufficient warmup, JIT not fully effective
@Warmup(iterations = 1, time = 1)
```

Correct: `@Warmup(iterations = 5, time = 1)` minimum.

#### 3. new Objects Inside Benchmark Method

```java
// Wrong: measuring allocation
@Benchmark
public List<Integer> createList() {
    return new ArrayList<>();
}
```

Should: Prepare in `@Setup`, benchmark only measures core logic.

#### 4. Dead Code Elimination

```java
// Wrong: return value unused, JIT may eliminate entire method
@Benchmark
public void compute() {
    int x = 1 + 2 + 3;
}
```

Should: `return x` or use `Blackhole`.

#### 5. Loop Inside Benchmark

```java
// Wrong: JMH loops itself, you don't need to loop
@Benchmark
public void loop() {
    for (int i = 0; i < 1000; i++) { ... }
}
```

Should: Single operation; JMH calls based on `time`.

#### 6. Inconsistent Warmup

Short warmup measures C1-compiled version, formal run measures C2-compiled version. Ensure `@Warmup` + `@Fork` provide sufficient warmup for each fork.

#### 7. JDK 17 Module System Reflection

JMH benchmarks use reflection to load `@Benchmark` methods. Accessing fields in non-exported packages requires `--add-opens`.

```java
// JVM parameters
--add-opens java.base/java.lang=ALL-UNNAMED
--add-opens java.base/java.util=ALL-UNNAMED
```

Pass via `jvmArgsAppend` at JMH startup:
```java
@Fork(jvmArgsAppend = {"--add-opens", "java.base/java.lang=ALL-UNNAMED"})
```

#### 8. JDK 17 Vector API Benchmark

Vector API is incubator; benchmark requires `--add-modules jdk.incubator.vector`:

```java
@Fork(jvmArgsAppend = {"--add-modules", "jdk.incubator.vector"})
```

### JMH Output Interpretation

```
Benchmark                          (size)   Mode  Cnt     Score    Error  Units
ListBenchmark.forEachSum               100  thrpt    10  1234.567 +/- 12.345  ops/ms
ListBenchmark.streamSum               100  thrpt    10   987.654 +/- 10.123  ops/ms
```

- **Score**: Throughput (ops/ms = operations per millisecond) or average time
- **Error**: 99% confidence interval
- **Cnt**: Measurement iterations

### JMH Profiler

JMH built-in profilers:

```bash
# GC situation
-prof gc

# Memory allocation
-prof gc -gc true

# Stack sampling
-prof stack

# Linux perf (system-level)
-prof perf

# Class loading
-prof cl
```

Usage: `java -jar target/benchmarks.jar -prof gc`

## Profiling Tool Comparison

| Tool | Granularity | Overhead | Output | Scenario |
|---|---|---|---|---|
| **JFR** (JDK Flight Recorder) | System-level + method-level | Very low (< 1%) | Binary .jfr | Production long-running, global view |
| **async-profiler** | Method-level CPU/Alloc/Lock | Low | Flame graph | Continuous sampling, hotspot location |
| **JProfiler / YourKit** | Full-featured GUI | Medium | GUI | Local deep tuning |
| **VisualVM** | Overview | Medium | GUI | Local quick view |
| **JMH** | Method-level precise | High | Text | Microbenchmark |
| **Arthas** | Online diagnosis | Medium | CLI | Production dynamic trace |
| **BTrace** | Online scripting | Medium | Custom | Production dynamic instrumentation |

## JFR (JDK Flight Recorder)

Open-sourced since JDK 11, production-ready. Overhead < 1%, the **preferred** long-term sampling tool for production environments.

### Start JFR Recording

```bash
# Start 30-second recording
jcmd <pid> JFR.start duration=30s filename=/tmp/recording.jfr

# Continuous recording (background)
jcmd <pid> JFR.start filename=/tmp/cont.jfr maxage=1h maxsize=100M

# View running recordings
jcmd <pid> JFR.check

# Stop
jcmd <pid> JFR.stop name=1
```

### Analyze JFR

- **JDK Mission Control (JMC)**: Official GUI, download from `https://github.com/openjdk/jmc`
- Command-line tool: `jfr print --events cpuload recording.jfr`

### JFR Key Event Types

- `jdk.CPULoad`: CPU load
- `jdk.JavaMonitorWait` / `jdk.JavaMonitorEnter`: Lock wait
- `jdk.GarbageCollection`: GC
- `jdk.ObjectAllocationSample`: Allocation sampling
- `jdk.ExecutionSample`: Method stack sampling (source for flame graphs)

### JDK 17 JFR Enhancements

**Streaming API (JDK 14+)**: Real-time streaming JFR events without disk persistence:

```java
import jdk.jfr.consumer.EventStream;

try (var stream = EventStream.openRepository()) {
    stream.onEvent("jdk.GarbageCollection", e -> {
        System.out.println("GC: " + e.getDuration("duration"));
    });
    stream.startAsync();
    Thread.sleep(60_000);
}
```

**Application scenarios**:
- Application-embedded JFR consumption -> real-time monitoring and alerting
- Custom dashboards without external tool dependencies
- APM vendor integration

**JDK 17 JFR advantages**:
- Overhead < 1%, can be kept on in production long-term
- `settings=profile` provides more complete events than `settings=default`
- Commonly enabled at startup: `-XX:StartFlightRecording=...`
- Key event upgrade: `jdk.ObjectAllocationSample` (JDK 14+) replaces old allocation sampling

**JDK 17 JFR startup configuration recommendation**:
```
-XX:StartFlightRecording=filename=/var/log/jfr/app.jfr,maxage=1h,maxsize=100M,settings=profile
-XX:FlightRecorderOptions=stackdepth=64
```
- `maxage=1h maxsize=100M`: Rolling, 1 hour or 100MB
- `settings=profile`: High sampling rate (vs `default`)
- `stackdepth=64`: Stack depth (default 64, can increase to 128 for complex applications)

## async-profiler

Open source, low-overhead sampling, outputs flame graphs. **Production-ready**.

### Installation

```bash
# Download
wget https://github.com/async-profiler/async-profiler/releases/download/v3.0/async-profiler-3.0-linux-x64.tar.gz
tar xzf async-profiler-3.0-linux-x64.tar.gz

# macOS use .dmg package
```

### CPU Flame Graph

```bash
# 30-second CPU sampling
./profiler.sh -d 30 -f flame.html <pid>

# View which method consumes the most CPU in the SVG flame graph
```

### Allocation Flame Graph

```bash
# 30-second allocation sampling
./profiler.sh -d 30 -e alloc -f alloc.html <pid>

# Find memory allocation hotspots (high-frequency new objects)
```

### Lock Flame Graph

```bash
./profiler.sh -d 30 -e lock -f lock.html <pid>
```

### Attach at Java Startup

```bash
# Mount at JVM startup
java -agentpath:/path/libasyncProfiler.so=start,event=cpu,file=flame.html -jar app.jar
```

### How to Read Flame Graphs

- X-axis: Method call stack spread out
- Y-axis: Stack depth (top is callee, bottom is main)
- Width: CPU time proportion
- Wider = more worth optimizing

**Finding bottlenecks**: Look for the widest "plateau" -- a single method with the highest CPU proportion.

## Arthas

Alibaba's online diagnostic tool, production-ready.

### Installation and Startup

```bash
# Download
curl -O https://arthas.aliyun.com/arthas-boot.jar

# Start (attach to specified PID)
java -jar arthas-boot.jar <pid>
```

### Common Commands

```bash
# Method call timing (find slow methods)
trace com.example.MyService doSomething

# Decompile (view JIT-compiled bytecode)
jad com.example.MyService

# Method input/output parameters
watch com.example.MyService doSomething '{params, returnObj}' -x 2

# Method call path
stack com.example.MyService doSomething

# View thread usage
thread -n 3

# View JVM information
dashboard
jvm

# View which method is creating objects
profiler start --event alloc
profiler stop --format flame
```

## VisualVM

Local tuning tool, view real-time overview. Not suitable for production.

### Main Features

- Overview: CPU / heap / threads / classes
- Memory: Heap histogram, GC activity
- Threads: Thread state, deadlock detection
- CPU Profiler: Method-level sampling (high overhead)
- Memory Profiler: Allocation sampling

### Usage

```bash
jvisualvm  # Bundled with JDK 8

# JDK 9+ download separately
# https://visualvm.github.io/
```

## JProfiler / YourKit

Commercial tools for local deep tuning. More features, but require payment.

**When to use**:
- Complex memory leak analysis
- Interactive SQL query analysis needed
- Deep method-level Profiling needed

**Not for production**: High overhead, impacts performance.

## Performance Metrics System

### Core Metrics

| Metric | Meaning | Calculation |
|---|---|---|
| **Throughput** (QPS/TPS) | Requests processed per second | Total requests / total time |
| **Average latency** | Average request time | Total time / request count |
| **P50** | Median latency | 50th percentile after sorting |
| **P90** | 90% request time | 90th percentile after sorting |
| **P99** | 99% request time | 99th percentile after sorting |
| **P999** | 99.9% request time | 99.9th percentile after sorting |
| **GC throughput** | GC time proportion | Total GC time / total time |
| **GC pause time** | STW duration | Individual GC pauses |

### Metric Pitfalls

**Only looking at average latency**: Masked by long tail.
- Average 50ms, but possibly 99% of users < 10ms, 1% of users 5 seconds
- Look at P99 / P999 for realistic experience

**Only looking at throughput**: May rely on request queuing.
- High QPS but request backlog -> average latency spikes
- Must be viewed together with latency

**Only measuring once**: Not trustworthy.
- Use statistically significant multiple measurements (JMH default 5+5)
- Look at +/- Error confidence interval

### Latency vs Throughput

- **Throughput first**: Batch processing, ETL, offline computation
- **Latency first**: Real-time services, user interaction, financial trading
- **Balance both**: Usually constrained by SLA (e.g., P99 < 100ms)

## Performance Testing Methodology

### Pre-Test Confirmation

- What to measure? (Throughput / latency / memory allocation)
- What tool to use? (JMH / Profiling / integration testing)
- Which environment to test in? (Local / staging / production)
- What is the baseline? (Performance of unoptimized version)

### During-Test Guarantees

- **Isolation**: `@Fork` isolates JVM, avoids JIT state pollution
- **Sufficient warmup**: `@Warmup` lets JIT compilation complete
- **Multiple measurements**: 5+ iterations, look at confidence interval
- **Control variables**: Change only one variable at a time

### Post-Test Verification

- Compare against baseline: Same metric before/after optimization
- Significance: Is the difference outside the confidence interval
- Production validation: Microbenchmark results may not represent real load

## Practical Profiling Workflow

### 1. Locate CPU Bottleneck

```bash
# JFR 30-second sampling
jcmd <pid> JFR.start duration=30s filename=/tmp/cpu.jfr

# Or async-profiler
./profiler.sh -d 30 -f flame.html <pid>

# View flame graph in JMC, find widest method
```

### 2. Locate Allocation Hotspot

```bash
./profiler.sh -d 30 -e alloc -f alloc.html <pid>

# Or JFR: Add -XX:StartFlightRecording=filename=/tmp/alloc.jfr,settings=profile at JFR startup
```

Find methods with high-frequency `new`.

### 3. Locate Lock Wait

```bash
./profiler.sh -d 30 -e lock -f lock.html <pid>

# Or JFR: View jdk.JavaMonitorWait events
```

### 4. Locate Frequent GC

```bash
# JFR: View jdk.GarbageCollection events
```

**jstat / jcmd real-time GC diagnosis**: Belongs to jvm-troubleshoot skill, see `../../jvm-troubleshoot/references/diagnostic_commands.md` and `gc_tuning_guide.md`.

### 5. Online Trace

```bash
# Arthas trace
trace com.example.MyService doSomething

# Find slowest method stack
```

## Reference Documentation

- JMH official: `https://openjdk.org/projects/code-tools/jmh/`
- JMH samples: `https://github.com/openjdk/jmh/tree/main/jmh-samples/src/main/java/org/openjdk/jmh/samples`
- async-profiler: `https://github.com/async-profiler/async-profiler`
- JFR / JMC: `https://github.com/openjdk/jmc`
- Arthas: `https://arthas.aliyun.com/`
- VisualVM: `https://visualvm.github.io/`
- Flame graph principles (Brendan Gregg): `https://www.brendangregg.com/flamegraphs.html`
