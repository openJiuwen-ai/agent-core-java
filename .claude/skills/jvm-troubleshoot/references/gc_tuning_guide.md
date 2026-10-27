# GC Troubleshooting Guide (Reactive Diagnostic Perspective)

This file supplements the GC troubleshooting content in SKILL.md, **focusing only on the reactive diagnostic perspective** (how to troubleshoot when GC has already gone wrong). For proactive selection and configuration (selecting GC and tuning parameters before deployment), see `../../performance-tuning/references/gc_tuning.md`.

**Boundary between the two files**:
- This file: Reactive troubleshooting (frequent Full GC, GC overhead limit, long pauses) -> identification + fix
- performance-tuning/gc_tuning.md: Proactive configuration (selecting G1/ZGC before deployment, tuning heap) -> prevention

## GC Type Identification

First step in troubleshooting: identify which type of GC problem is occurring.

| GC | Trigger Condition | Reclaimed Region | Pause Time | Troubleshooting Focus |
|---|---|---|---|---|
| **Young GC / Minor GC** | Eden full | Eden + one Survivor | Short (millisecond level) | Frequent = object creation too fast or Eden too small |
| **Mixed GC** (G1) | Old generation occupancy trigger | Young generation + some old generation regions | Medium | Occasional is normal; frequent indicates fast old generation growth |
| **Full GC** | Old generation full / Metaspace full / `System.gc()` | Entire heap + Metaspace | Long (second level) | Frequent Full GC always has a root cause |

**Troubleshooting goal**: Fewer Full GC is better. Frequent Full GC indicates a problem with the old generation or Metaspace.

## Pathways for Objects Entering the Old Generation (Investigating Promotion Issues)

When investigating "old generation growing too fast", check these 4 pathways:

1. **Age reaches threshold**: Survives 15 Young GCs by default (`-XX:MaxTenuringThreshold`)
2. **Large objects**: Exceeds `-XX:PretenureSizeThreshold` and goes directly to old generation
3. **Survivor full**: When Survivor space is insufficient, surviving objects are promoted directly
4. **Dynamic age calculation**: JVM automatically determines that too many objects of a certain age exist and promotes them early

**Troubleshooting approach**: If Young GC is frequent and Full GC is also frequent, it may be premature promotion -- use `jstat -gcnew <pid> 1000` to check Survivor occupancy and see if objects are being promoted before dying in the young generation.

## Troubleshooting Command Quick Reference

```bash
# View generation occupancy + GC counts (refresh every second)
jstat -gcutil <pid> 1000

# View last GC cause
jstat -gccause <pid> 1000

# View young generation details (Eden/Survivor occupancy, promotion age)
jstat -gcnew <pid> 1000

# View GC overview
jcmd <pid> GC.heap_info

# Real-time GC log (no file)
jcmd <pid> VM.log output='file=/dev/stdout' what='gc*'
```

## Key Metrics Quick Reference (jstat -gcutil)

Use `jstat -gcutil <pid> 1000` for continuous observation; focus on:

| Metric | Healthy Value | Abnormal Value | Abnormal Countermeasure |
|---|---|---|---|
| O (old generation occupancy) | < 70% | > 80% and not dropping | Scale up or investigate leak |
| YGC frequency | Once every few seconds | Multiple times per second | Reduce object creation |
| FGC frequency | Once every few hours | Once per minute | Investigate leak or scale up |
| FGCT/GCT ratio | < 20% | > 50% | High Full GC proportion, old generation issue |
| GCT/runtime ratio | < 5% | > 10% | High overall GC overhead |

**Troubleshooting focus**:
- O continuously rising without dropping = memory leak (see SKILL.md OOM Troubleshooting)
- High FGC frequency but O not high = possibly Metaspace full or `System.gc()` being called
- High YGC frequency but few FGC = object creation too fast, young generation insufficient

## Common Troubleshooting Scenarios

### Scenario 1: Frequent Young GC

**Symptom**: `jstat` shows YGC growing fast, application response fluctuates.

**Troubleshooting**:
- `jstat -gcnew <pid> 1000` to check Eden occupancy, whether it fills up in seconds
- Check if application creates objects at high frequency (profiling allocation flame graph)

**Countermeasures**:
- Reduce object creation (caching, object pool, StringBuilder)
- Increase young generation size (proactive tuning, see performance-tuning/gc_tuning.md)
- Eden too small causes frequent Young GC but little reclaimed each time

### Scenario 2: Frequent Full GC

**Symptom**: FGC growing fast, application has long pauses.

**Troubleshooting** (identify by root cause):
- `jstat -gcutil <pid> 1000` to check if O is continuously rising without dropping
- Old generation growing without dropping -> memory leak, get dump for analysis (see SKILL.md OOM Troubleshooting)
- Old generation growing then dropping -> heap insufficient
- Metaspace full -> `jmap -clstats <pid>` to check if ClassLoader count keeps growing (see SKILL.md Class Loading Troubleshooting)
- Check if code calls `System.gc()`

**Countermeasures**:
- Memory leak -> get dump to find GC Root chain, locate leak point
- Heap insufficient -> increase `-Xmx`
- Metaspace full -> increase `-XX:MaxMetaspaceSize` or fix class loading leak
- `System.gc()` being called -> add `-XX:+DisableExplicitGC`

### Scenario 3: Single GC Pause Too Long

**Symptom**: GC count is normal, but each FGCT is very large.

**Troubleshooting**:
- Check GC logs to confirm whether Young GC or Full GC has long pauses
- `jcmd <pid> GC.heap_info` to check heap size and region configuration

**Countermeasures**:
- G1 Young GC pause long -> Eden too large, proactively tune `MaxGCPauseMillis` (see performance-tuning)
- G1 Full GC pause long -> old generation fragmentation, consider switching to ZGC
- Switch to ZGC (JDK 17+ stable, proactive configuration see performance-tuning/gc_tuning.md)

### Scenario 4: Large Objects Promoted Directly to Old Generation

**Symptom**: Frequent Full GC but not many objects in old generation (large objects fragmented in old generation).

**Troubleshooting**:
- `jstat -gcnew <pid> 1000` to check if Survivor is frequently full
- Profiling to see if large arrays/large strings are being created

**Countermeasures**:
- Find large array/large string creation points, use streaming processing or sharding instead
- Proactively tune `-XX:PretenureSizeThreshold` (see performance-tuning)

### Scenario 5: GC Overhead Limit Exceeded

**Symptom**: `OutOfMemoryError: GC overhead limit exceeded`.

**Troubleshooting**: This is a safety mechanism when GC spends too much time reclaiming too little memory. Usually a memory leak or heap too small.

**Countermeasures**:
- First get a dump (if `-XX:+HeapDumpOnOutOfMemoryError` was enabled, dump is available)
- MAT analysis to find leak point
- Not a leak -> heap too small, increase `-Xmx`
- Temporarily disable this check: `-XX:-UseGCOverheadLimit` (symptomatic, not recommended)

## GC Log Analysis

### JDK 17 Log Format

JDK 17 unified logging syntax (`-Xlog:gc*`), log example:
```
[2023-12-01T10:00:00.000+0800] GC(0) Pause Young (Normal) (G1 Evacuation Pause)
[2023-12-01T10:00:00.050+0800] GC(0) 23M->10M(64M)(50.000ms) ...
```

**Key fields**:
- `GC(N)`: Nth GC
- `Pause Young / Pause Full`: GC type
- `23M->10M(64M)`: Heap before and after occupancy (total size)
- `(50.000ms)`: Pause time

### Analysis Tools

- **GCViewer** (open source): `https://github.com/chewiebug/GCViewer`
- **gceasy.io** (online): Upload GC log for automatic analysis
- **JFR**: `jdk.GarbageCollection` event (see performance-tuning/jmh_profiling.md)

## Reference Entries

- **SKILL.md**: Main entry for this skill, jump by symptom (OOM/CPU 100%/high-frequency GC etc.)
- **Diagnostic command details**: `diagnostic_commands.md` (jstat/jmap/jcmd output field meanings)
- **Troubleshooting cases**: `troubleshooting_cases.md` (4 end-to-end cases)
- **Proactive GC configuration**: `../../performance-tuning/references/gc_tuning.md` (selecting G1/ZGC before deployment, tuning heap, production parameter configuration)
- **JVM parameter details**: `../../performance-tuning/references/jvm_params.md` (JDK 17 default parameter table, parameter value ranges)
- Official GC tuning: `https://docs.oracle.com/en/java/javase/17/gctuning/`
- GC log analysis: `https://gceasy.io/`
