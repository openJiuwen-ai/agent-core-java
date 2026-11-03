# JVM Parameter Details (JDK 17 baseline)

This file supplements SKILL.md's JVM parameter tuning content, **using JDK 17 as the baseline**. Explains the value ranges, defaults, and scenarios for heap/stack/Metaspace/CodeCache/JIT parameters. Read on demand when users ask "what value should this parameter be set to" or "what does this parameter do".

## JDK 17 Default Parameter Table

JDK 17 key default values (no need to change for most scenarios):

| Parameter | JDK 17 Default | Notes |
|---|---|---|
| `-XX:+UseG1GC` | Yes | Default collector |
| `-XX:+TieredCompilation` | Yes | Tiered compilation |
| `-XX:+DoEscapeAnalysis` | Yes | Escape analysis |
| `-XX:+UseContainerSupport` | Yes | Auto-detect cgroup in containers |
| `-XX:MaxGCPauseMillis` | 200 | G1 target pause |
| `-XX:ReservedCodeCacheSize` | 240M | JIT code cache |
| `-XX:MetaspaceSize` | 20M (Linux) | Full GC trigger threshold |
| `-XX:MaxMetaspaceSize` | No upper limit | Limited by physical memory |
| `-XX:CompileThreshold` | 10000 | C2 compilation threshold (adaptive under tiered compilation) |
| `-XX:MaxInlineSize` | 35 | Regular method inlining upper limit |
| `-XX:FreqInlineSize` | 325 | Hot method inlining upper limit |
| `-XX:SurvivorRatio` | 8 | Eden:Survivor |
| `-XX:TargetSurvivorRatio` | 50 | Survivor occupancy target |
| `-XX:MaxTenuringThreshold` | 15 | Age for promotion to old generation |

**View actual values for this process**:
```bash
jcmd <pid> VM.flags
# Or print at startup
java -XX:+PrintFlagsFinal -version | grep <param>
```

## Parameter Classification

JVM parameters fall into 3 categories:
- **Standard parameters** (`-`): Supported by all JVM implementations, e.g., `-version`, `-jar`
- **Non-standard parameters** (`-X`): HotSpot default implementation, e.g., `-Xmx`, `-Xms`
- **Unstable parameters** (`-XX`): HotSpot-specific, may change between versions, e.g., `-XX:+UseG1GC`

**Boolean -XX**: `-XX:+OptionName` enables, `-XX:-OptionName` disables.
**Numeric -XX**: `-XX:OptionName=value`, value can have units (k/m/g).

## Heap-Related Parameters

### -Xms / -Xmx

| Parameter | Meaning | JDK 17 Default |
|---|---|---|
| `-Xms` | Initial heap | Container: cgroup memory / 4; Physical machine: physical memory / 64 |
| `-Xmx` | Maximum heap | Container: cgroup memory / 4; Physical machine: physical memory / 4 |

**Value suggestions**:
- Production services: `-Xms = -Xmx`, avoid heap dynamic expansion triggering Full GC
- Container: `-XX:+UseContainerSupport` (enabled by default in JDK 17), auto-detects cgroup memory limits
- Using percentages is more convenient: `-XX:InitialRAMPercentage=50.0 -XX:MaxRAMPercentage=75.0`
- Heap upper limit: **<= 32GB**, exceeding disables compressed oops (`-XX:+UseCompressedOops`), object references become 8 bytes, actually consuming more memory
- Inside containers: Allocate 50-75% of container memory to heap, rest for Metaspace/thread stacks/direct memory/JVM itself

### -Xmn / -XX:NewRatio / -XX:SurvivorRatio

| Parameter | Meaning | Default |
|---|---|---|
| `-Xmn` | Young generation size | Platform-dependent |
| `-XX:NewRatio=N` | Old:Young = N:1 (NewRatio=2 means young generation = 1/3 of heap) | 2 |
| `-XX:SurvivorRatio=N` | Eden:Survivor = N:1 | 8 |
| `-XX:MaxTenuringThreshold=N` | Object age for promotion to old generation | 15 |

**When using G1** (JDK 17 default):
- Don't manually set `-Xmn` / `-XX:NewRatio` -- G1 adaptively adjusts
- Can set `-XX:G1HeapRegionSize=8m`, auto-selected based on heap size (1MB-32MB)

**When using Parallel/SerialGC**:
- `-XX:NewRatio=2`: Young generation 1/3, old generation 2/3
- `-XX:SurvivorRatio=8`: Eden 80%, each Survivor 10%
- Larger young generation -> fewer Young GC but longer each time; smaller young generation -> more frequent Young GC but shorter each time

### -XX:PretenureSizeThreshold

Large objects exceeding this value go directly to old generation, avoiding back-and-forth copying in young generation.

- Unit: bytes
- Default: 0 (no limit, JVM decides)
- When using G1: Large objects (> half of region) go to humongous region, no need to adjust this parameter
- Increasing reduces large object copying in young generation, but too large causes old generation to fill up quickly

**Scenario**: Cached large objects, large arrays, large strings should go to old generation.

## Metaspace Parameters

| Parameter | Meaning | JDK 17 Default |
|---|---|---|
| `-XX:MetaspaceSize` | Full GC trigger threshold | 20M (Linux) |
| `-XX:MaxMetaspaceSize` | Metaspace upper limit | No upper limit (limited by physical memory) |

**Suggestions**:
- Production fixed: `-XX:MetaspaceSize=256m -XX:MaxMetaspaceSize=512m`, avoid runtime expansion triggering Full GC
- Many dynamically generated classes (CGLIB/ByteBuddy): Increase MaxMetaspaceSize
- MetaspaceSize is not initial allocation, it's the GC trigger threshold -- the point of the first Full GC

**OOM Metaspace troubleshooting**: View `jmap -clstats <pid>` ClassLoader count. See jvm-troubleshoot SKILL.md for details.

## Stack Parameters

### -Xss

Per-thread call stack size.

| Platform | JDK 17 Default |
|---|---|
| Linux x64 | 1M |
| Windows x64 | 1M |
| macOS x64 | 1M |
| Linux ARM64 | 2M |

**Tuning scenarios**:
- Deep recursion: Increase to 2M-4M, prevent StackOverflowError
- Very many threads (thousands): Decrease to 256K, save memory
- Default: Don't change

**Calculation**: 1000 threads x 1M = 1GB stack memory; stack memory is significant with many threads.

## Code Cache

| Parameter | Meaning | JDK 17 Default |
|---|---|---|
| `-XX:ReservedCodeCacheSize` | JIT code cache upper limit | 240M |
| `-XX:InitialCodeCacheSize` | Initial code cache | 160K |
| `-XX:CodeCacheExpansionSize` | Expansion step | 64K |

**Notes**:
- Too small -> JIT-compiled methods are discarded back to interpreted execution (deoptimization), performance drops sharply
- JDK 17 default 240M is sufficient for most cases; large applications (100k+ classes) can increase to 512M
- View: `jcmd <pid> Compiler.CodeCache`

## JIT Parameters

### -XX:+TieredCompilation

Tiered compilation switch, enabled by default in JDK 17.

**Impact of disabling**:
- Only C2 compilation used, method compilation threshold increases (10x+), slow warmup
- Short-running applications may benefit (no profiling overhead)
- Long-running applications **should not disable**

### -XX:CompileThreshold

Method invocation count reaches threshold to trigger compilation. Adaptive under tiered compilation mode, no longer fixed.

| Mode | Threshold | Value | Description |
|---|---|---|
| C1 | ~1500 (adaptive) | Triggers C1 compilation |
| C2 | ~10000 (adaptive) | Triggers C2 compilation |

### -XX:+PrintCompilation

Print JIT compilation log for debugging JIT behavior.

```bash
jcmd <pid> Compiler.directives_print
# Or add at startup
-XX:+PrintCompilation -XX:+UnlockDiagnosticVMOptions -XX:+PrintInlining
```

See `jit_compiler.md` for more JIT details.

## GC Parameters

**JDK 17 default G1**. Commonly used:
```
-XX:+UseG1GC                          # Default, writing it out is clearer
-XX:+UseZGC                           # JDK 15+ production-ready
-XX:+UseShenandoahGC                  # JDK 15+ stable (OpenJDK build)
-XX:+UseParallelGC                    # Throughput first
-XX:MaxGCPauseMillis=200              # Target pause time (G1/ZGC)
-XX:G1HeapRegionSize=8m               # G1 region size
-XX:ParallelGCThreads=N               # GC thread count
-XX:ConcGCThreads=N                   # Concurrent GC thread count
-XX:InitiatingHeapOccupancyPercent=45 # G1 heap occupancy to trigger concurrent marking
-XX:G1NewSizePercent=5                 # G1 young generation lower bound
-XX:G1MaxNewSizePercent=60             # G1 young generation upper bound
-XX:+DisableExplicitGC                # Disable System.gc()
-XX:+ExplicitGCInvokesConcurrent      # System.gc() goes concurrent (G1)
-XX:+AlwaysPreTouch                    # Pre-touch all heap pages at startup
```

**ZGC key parameters** (JDK 17 is **non-generational version**, generational ZGC is stable in JDK 21+):
```
-XX:ZAllocationSpikeTolerance=2       # Allocation spike tolerance
-XX:ConcGCThreads=N                   # Concurrent GC thread count (default ParallelGCThreads * 1/4)
```

**Shenandoah key parameters**:
```
-XX:ShenandoahGCHeuristics=adaptive   # Adaptive (default)
-XX:ShenandoahGCHeuristics=aggressive # Aggressive (low latency)
-XX:ShenandoahGCHeuristics=compact     # Compact (save memory)
```

See `gc_tuning.md` for more GC tuning details.

## Monitoring Parameters

**Commonly enabled in production** (JDK 17 unified logging syntax):
```
-Xlog:gc*:file=gc.log:time,uptime:filecount=10,filesize=10M   # GC log
-XX:+HeapDumpOnOutOfMemoryError                                # Auto dump on OOM
-XX:HeapDumpPath=/var/log/dumps/                              # dump path
-XX:ErrorFile=/var/log/hs_err_%p.log                           # Fatal error log
```

**JDK 17 JFR recommended always on** (overhead < 1%):
```
-XX:StartFlightRecording=filename=/var/log/jfr/app.jfr,maxage=1h,maxsize=100M,settings=profile
-XX:FlightRecorderOptions=stackdepth=64
```

## Debug Parameters

**Must unlock first**:
```
-XX:+UnlockDiagnosticVMOptions     # Unlock diagnostic parameters
-XX:+PrintFlagsFinal               # Print all JVM parameter final values
-XX:+PrintFlagsInitial            # Print default values
-XX:+PrintCommandLineFlags         # Print parameters set at startup
-XX:+PrintInlining                 # Print inlining decisions
-XX:+PrintCompilation              # Print JIT compilation log
-XX:+LogCompilation                # Detailed compilation log (requires PrintCompilation)
```

**View JVM parameters**:
```bash
# View after process starts
jcmd <pid> VM.flags

# All parameter final values
java -XX:+PrintFlagsFinal -version | grep -i heapsize
```

## Module System Parameters (JDK 17 Must-Know)

JDK 9+ module system; reflective access to non-exported packages must be explicitly opened:

```
--add-opens java.base/java.lang=ALL-UNNAMED            # Reflective access to String internals
--add-opens java.base/java.util=ALL-UNNAMED            # Reflective access to collection internals
--add-opens java.base/java.nio=ALL-UNNAMED             # Reflective access to NIO
--add-exports java.base/sun.nio.ch=ALL-UNNAMED         # Direct access (non-reflective)
```

**Typical scenarios**:
- Using Spring / Hibernate / ByteBuddy reflection -> need `--add-opens`
- Using Netty to access NIO internals -> need `--add-opens java.base/java.nio`
- Using Reflect.setField to access private -> need `--add-opens`

**Error keyword**: `InaccessibleObjectException` -> missing `--add-opens`.

## Practical Parameter Combinations

### Microservice (JDK 17, Container)

```
-XX:+UseContainerSupport
-XX:InitialRAMPercentage=50.0
-XX:MaxRAMPercentage=75.0
-XX:+UseG1GC
-XX:MaxGCPauseMillis=200
-XX:MetaspaceSize=256m
-XX:MaxMetaspaceSize=512m
-XX:+HeapDumpOnOutOfMemoryError
-XX:HeapDumpPath=/var/log/dumps/
-Xlog:gc*:file=/var/log/gc.log:time,uptime:filecount=10,filesize=10M
-XX:StartFlightRecording=filename=/var/log/jfr/app.jfr,maxage=1h,maxsize=100M,settings=profile
-XX:+AlwaysPreTouch
-XX:+DisableExplicitGC
```

### High Throughput Batch Processing (JDK 17, Physical Machine)

```
-Xms16g -Xmx16g
-XX:+UseParallelGC
-XX:ParallelGCThreads=16
-XX:+AggressiveHeap
-XX:+UseParallelOldGC
-XX:ReservedCodeCacheSize=512m
-XX:+TieredCompilation
```

### Low-Latency Large Application (JDK 17, Physical Machine or Large Container)

```
-Xms32g -Xmx32g
-XX:+UseZGC                          # JDK 17 non-generational ZGC, sub-millisecond pause
-XX:ZAllocationSpikeTolerance=2
-XX:ConcGCThreads=8
-XX:MetaspaceSize=512m
-XX:MaxMetaspaceSize=1g
-XX:ReservedCodeCacheSize=512m
-XX:+HeapDumpOnOutOfMemoryError
-Xlog:gc*:file=/var/log/gc.log:time,uptime:filecount=20,filesize=50M
```

Note: JDK 17 ZGC is non-generational; throughput under large heap is slightly lower than generational version (JDK 21+). If heap > 32GB and throughput-sensitive, consider upgrading to JDK 21 for generational ZGC.

### CLI / Short-Running Application (JDK 17)

```
-XX:+TieredCompilation
-XX:ReservedCodeCacheSize=128m
# Or use GraalVM Native Image for ahead-of-time compilation (JDK 17 production-ready)
```

**Note**: CLI applications have insufficient warmup; JIT may never take effect. Consider:
- CDS (Class Data Sharing): `-Xshare:on`
- GraalVM Native Image: AOT compilation
- AppCDS: CDS for custom classes

## Reference Documentation

- Oracle JDK 17 JVM parameters: `https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html`
- OpenJDK 17 source: `https://github.com/openjdk/jdk17`
- In-project GC tuning: `gc_tuning.md`
- In-project JIT tuning: `jit_compiler.md`
- In-project reactive troubleshooting: `../../jvm-troubleshoot/references/gc_tuning_guide.md`
