# GC Tuning Guide (Proactive, JDK 17 baseline)

This file supplements SKILL.md's GC tuning content, **using JDK 17 as the baseline**, focusing on **proactive configuration** (how to select GC and tune parameters to prevent issues). For reactive troubleshooting (when GC is already frequent), see `../../jvm-troubleshoot/references/gc_tuning_guide.md`.

**Boundary between the two skills**:
- jvm-troubleshoot/gc_tuning_guide: Reactive troubleshooting (frequent Full GC, GC overhead limit) -> locate + fix
- This file: Proactive configuration (select GC and tune parameters before deployment) -> prevent

## Heap Generational Review

```
+--------------------------------------------------+
|                        Heap                       |
+-----------------------------+--------------------+
|       Young Generation      |   Old Generation   |
|  +-------+-------+-------+ |                    |
|  | Eden  | S0    | S1    | |                    |
|  +-------+-------+-------+ |                    |
+-----------------------------+--------------------+
              +------------------+
              | Metaspace (non-heap) |  Class metadata
              +------------------+
```

**Core tuning philosophy**:
- Let objects be reclaimed in the young generation as much as possible (short-lived objects)
- Avoid premature promotion to old generation
- Reduce Full GC (Full GC = STW long pause)

## JDK 17 GC Collector Status

JDK 17 stable collectors:

| Collector | Status | Algorithm | Pause | Applicable |
|---|---|---|---|---|
| **G1** | Default | Region-based + mark-compact | Short controllable (200ms target) | General, medium heap |
| **ZGC** | Stable (JDK 15+) | Colored pointers + read barrier | Sub-millisecond | Large heap, low latency |
| **Shenandoah** | Stable (JDK 15+) | Brooks pointer + concurrent compaction | Sub-millisecond | Large heap, low latency |
| **Parallel** | Stable | Multi-threaded parallel | Medium | Throughput-first |
| **Serial** | Stable | Single-threaded | Long | Client, small applications |
| **CMS** | **Removed** (JDK 14) | - | - | Upgrade to G1 |

**JDK 17 key points**:
- ZGC is the **non-generational version** (generational ZGC is stable only in JDK 21+). Non-generational version has throughput impact; for very large heap scenarios consider upgrading to JDK 21
- Shenandoah is stable in OpenJDK builds; Oracle JDK does not include Shenandoah
- G1 remains the default, sufficient for most scenarios

## GC Collector Selection (Proactive Decision)

### Select by Scenario

| Scenario | Heap Size | Pause Requirement | Recommended Collector | Key Parameters |
|---|---|---|---|---|
| Microservice / Default | < 4GB | Within 200ms | G1 | `-XX:MaxGCPauseMillis=200` |
| Medium application | 4-8GB | Within 200ms | G1 | `-XX:MaxGCPauseMillis=200 -XX:G1HeapRegionSize=8m` |
| Large low-latency | 8-32GB | Within 50ms | ZGC | `-XX:+UseZGC -XX:ZAllocationSpikeTolerance=2` |
| Very large heap | 32GB-16TB | Sub-millisecond | ZGC | `-XX:+UseZGC` |
| High throughput batch | Any | No pause requirement | Parallel | `-XX:+UseParallelGC -XX:ParallelGCThreads=N` |
| Low pause + concurrent marking | Medium | Within 100ms | Shenandoah (OpenJDK) | `-XX:+UseShenandoahGC` |

### Selection Decision Tree

1. JDK 17 + heap >= 8GB + low latency -> **ZGC**
2. JDK 17 + heap < 8GB -> **G1** (default)
3. Throughput first (offline computation) -> **Parallel**
4. Heap > 32GB + throughput-sensitive -> upgrade to JDK 21 for generational ZGC

**Do not**:
- Use ZGC with heap < 4GB -> no benefit, actually slower
- Switch collectors without measuring -> might be slower
- Set `-XX:MaxGCPauseMillis=1` -> mostly unachievable, causes frequent GC instead

## G1 Tuning

G1 is the JDK 17 default collector; most scenarios don't need manual tuning.

### G1 Working Principle

- Heap divided into regions (1MB-32MB, default adapts to heap size)
- Young generation = set of regions (dynamically adjusted)
- Old generation = set of regions
- Large objects = humongous region (object > 50% of region)
- Mixed GC: selectively reclaim old generation regions

### G1 Key Parameters

| Parameter | Default | Tuning Suggestion |
|---|---|---|
| `-XX:MaxGCPauseMillis` | 200 | Actual SLA pause * 0.8 |
| `-XX:G1HeapRegionSize` | Adaptive | Explicitly set 8m-32m |
| `-XX:InitiatingHeapOccupancyPercent` | 45 | Old generation occupancy triggers concurrent marking; lower triggers earlier |
| `-XX:G1NewSizePercent` | 5 | Young generation lower bound |
| `-XX:G1MaxNewSizePercent` | 60 | Young generation upper bound |
| `-XX:G1MixedGCCountTarget` | 8 | How many Mixed GC rounds to complete |
| `-XX:G1MixedGCLiveThresholdPercent` | 85 | Region live object ratio > this value won't be reclaimed |

### G1 Tuning Scenarios

**Scenario 1: Frequent Full GC**

Cause: Old generation growing too fast, Mixed GC can't keep up.

Tuning:
- Lower `-XX:InitiatingHeapOccupancyPercent=35` (trigger concurrent marking earlier)
- Raise `-XX:G1MixedGCCountTarget=16` (more Mixed GC rounds)
- Raise `-XX:G1MixedGCLiveThresholdPercent=90` (more` (more regions eligible for reclamation)

**Scenario 2: Long Young GC Pause**

Cause: Eden too large, single reclamation takes long.

Tuning:
- Lower `-XX:MaxGCPauseMillis=100`
- Lower `-XX:G1MaxNewSizePercent=40`

**Scenario 3: Frequent Humongous Allocation**

Cause: Many large objects occupy humongous regions; only reclaimed during Mixed GC.

Tuning:
- Increase `-XX:G1HeapRegionSize=16m` or `32m` (reduce humongous allocations)
- Application level: reduce large object allocation, or use object pooling

## ZGC Tuning (JDK 17 Non-Generational Version)

Production-ready since JDK 15. JDK 17 is the **non-generational version**; pause doesn't grow with heap (sub-millisecond), suitable for large heaps.

### ZGC Working Principle

- Colored pointers (multi-view mapping)
- Read barrier (application threads do concurrent marking)
- Almost entirely concurrent; STW only has a few short pauses (initial mark, remark, relocation)

### JDK 17 vs JDK 21+ ZGC

| Dimension | JDK 17 (Non-generational) | JDK 21+ (Generational) |
|---|---|---|
| Algorithm | Whole-heap concurrent | Generational + concurrent |
| Throughput | Slightly lower (scans entire heap) | Higher (only scans young generation) |
| Pause | Sub-millisecond | Sub-millisecond |
| Heap size | Any | Any |

**Recommendation**: Heap > 32GB + throughput-sensitive -> upgrade to JDK 21 for generational ZGC. Otherwise JDK 17 non-generational ZGC is sufficient.

### ZGC Key Parameters

| Parameter | Default | Description |
|---|---|---|
| `-XX:ZAllocationSpikeTolerance` | 2 | Allocation spike tolerance |
| `-XX:ConcGCThreads` | ParallelGCThreads / 4 | Concurrent GC thread count |
| `-XX:ZUncommitDelay` | 300 | Uncommitted memory reclamation delay (seconds) |

### ZGC Tuning Scenarios

**Concurrent marking consuming CPU**:
- Increase `-XX:ConcGCThreads` (more concurrent GC threads)
- But must not exceed half of physical cores, otherwise application CPU is squeezed

**Allocation spike causing STW**:
- Increase `-XX:ZAllocationSpikeTolerance=3`

## Parallel GC Tuning

Throughput-first collector. Commonly used for offline computation.

### Parallel Key Parameters

| Parameter | Default | Description |
|---|---|---|
| `-XX:ParallelGCThreads` | CPU cores * 5/8 | GC thread count |
| `-XX:MaxGCPauseMillis` | No upper limit | Target pause (not guaranteed) |
| `-XX:GCTimeRatio` | 99 | GC time ratio = 1/(1+N) -> 1% |
| `-XX:UseParallelOldGC` | JDK 17 default | Old generation parallel collection |

### Parallel Tuning Scenarios

**Throughput first**:
- Don't set `-XX:MaxGCPauseMillis` (let GC run freely)
- Increase young generation: `-XX:NewRatio=1` (young generation = half of old generation)

**Reduce GC frequency**:
- Increase heap
- Increase young generation
- Increase `-XX:GCTimeRatio=99` (allow 1% GC time ratio)

## General GC Tuning Principles

### Principle 1: Tune the Application First, Then GC

GC tuning has limited benefit. Optimize the application layer first (algorithms, data structures, allocation hotspots), then tune GC parameters.

### Principle 2: Bigger Heap Is Not Always Better

- Large heap -> longer single GC time (for non-concurrent collectors)
- Large heap -> exceeding 32GB disables compressed oops
- 32GB+ heap use ZGC / Shenandoah (concurrent, pause doesn't grow with heap)

### Principle 3: Avoid Premature Promotion

Objects reclaimed in young generation are better than entering old generation.

- Increase young generation (`-XX:NewRatio=1`)
- Increase Survivor (`-XX:SurvivorRatio=6`)
- Increase promotion age (`-XX:MaxTenuringThreshold=15`)

### Principle 4: Monitor GC Logs

GC logs must be enabled (JDK 17 unified logging syntax):

```
-Xlog:gc*:file=gc.log:time,uptime:filecount=10,filesize=10M
```

Periodically analyze with GCViewer / gceasy.io.

### Principle 5: Test and Verify

GC parameter changes require testing:
- Run actual traffic with JFR / container load testing
- Compare GC log throughput, pauses, Full GC frequency
- Don't stop until target is met

## GC Tuning Scenario Quick Reference

| Scenario | Tuning |
|---|---|
| Frequent Full GC | Increase heap / lower IHOP / fix memory leak |
| Frequent Young GC | Increase young generation |
| Long Young GC pause | Decrease young generation / lower MaxGCPauseMillis |
| GC throughput > 10% | Increase heap / switch GC / fix application |
| Metaspace full | Adjust `-XX:MaxMetaspaceSize` |
| Direct memory full | Adjust `-XX:MaxDirectMemorySize` |

## Production Environment Recommended Configuration

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
-XX:+AlwaysPreTouch
-XX:+DisableExplicitGC
```

### Large Low-Latency (JDK 17, Physical Machine)

```
-Xms32g -Xmx32g
-XX:+UseZGC                          # JDK 17 non-generational ZGC
-XX:ZAllocationSpikeTolerance=2
-XX:ConcGCThreads=8
-XX:MetaspaceSize=512m
-XX:MaxMetaspaceSize=1g
-XX:ReservedCodeCacheSize=512m
-XX:+HeapDumpOnOutOfMemoryError
-Xlog:gc*:file=/var/log/gc.log:time,uptime:filecount=20,filesize=50M
```

### High Throughput Batch Processing (JDK 17)

```
-Xms16g -Xmx16g
-XX:+UseParallelGC
-XX:ParallelGCThreads=16
-XX:NewRatio=1
-XX:+AlwaysPreTouch
```

## GC Log Analysis (JDK 17 Unified Syntax)

### Key Metrics

- **GC throughput**: Application time / total time (should be > 95%)
- **Average pause**: Average of all GC pauses
- **Maximum pause**: Single longest pause
- **Full GC frequency**: Normally once every few hours; once every few minutes is problematic
- **Old generation occupancy**: Should be able to drop back after stabilizing; if not = memory leak

### Tools

- **GCViewer** (open source): `https://github.com/chewiebug/GCViewer`
- **gceasy.io** (online): Upload GC log for automatic analysis
- **JFR**: `jdk.GarbageCollection` events

### JDK 17 Log Syntax

```
-Xlog:gc*:file=gc.log:time,uptime:filecount=10,filesize=10M
```

- `gc*`: All GC-related tags
- `time,uptime`: Log format (timestamp + seconds since startup)
- `filecount=10,filesize=10M`: Rolling log, 10 files x 10MB

**Online GC log viewing / real-time diagnostic commands**: Belongs to jvm-troubleshoot skill, see `../../jvm-troubleshoot/references/diagnostic_commands.md` and `gc_tuning_guide.md` (reactive troubleshooting perspective).

## Reference Documentation

- In-project reactive troubleshooting: `../../jvm-troubleshoot/references/gc_tuning_guide.md`
- In-project diagnostic commands: `../../jvm-troubleshoot/references/diagnostic_commands.md`
- Oracle JDK 17 GC tuning: `https://docs.oracle.com/en/java/javase/17/gctuning/`
- G1 tuning: `https://docs.oracle.com/en/java/javase/17/gctuning/garbage-first-garbage-collector.html`
- ZGC: `https://wiki.openjdk.org/display/zgc`
- ZGC generational design (JEP 439): `https://openjdk.org/jeps/439`
- Shenandoah: `https://wiki.openjdk.org/display/shenandoah`
- GC log analysis: `https://gceasy.io/`
