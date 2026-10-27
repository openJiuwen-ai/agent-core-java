# JFR Post-Incident Analysis Guide

This file supplements SKILL.md, focusing specifically on **JDK Flight Recorder (JFR) post-incident analysis**. JDK 17 JFR overhead is < 1%, making it the primary tool for reactive troubleshooting, replacing the traditional jstack/jmap approach. Read as needed when a user reports a failure requiring JFR troubleshooting, or wants to use JFR events to locate root causes.

## JFR vs Traditional Tools

| Dimension | jstack / jmap | JFR |
|---|---|---|
| Overhead | Medium (each execution impacts application) | Very low (< 1%, can be always-on) |
| Data | Single point-in-time snapshot | Continuous time-window events |
| Online streaming | Not supported | Supported (JDK 14+) |
| Flame graphs | Requires additional processing | Built-in |
| Best for | One-off diagnosis | Long-running monitoring + post-incident analysis |
| Container friendly | Requires JDK + SYS_PTRACE | Configure at startup and use |

**Conclusion**: On JDK 17, prefer JFR; use jstack/jmap as supplementary tools.

## JFR Event Categories

JFR events by category:

| Category | Representative Events | Usage |
|---|---|---|
| **GC** | `jdk.GarbageCollection` | Troubleshoot frequent GC / pauses |
| **Memory** | `jdk.ObjectAllocationSample` / `jdk.GCHeapSummary` | Troubleshoot memory allocation hotspots |
| **Locks** | `jdk.JavaMonitorWait` / `jdk.JavaMonitorEnter` | Troubleshoot lock contention / deadlocks |
| **Threads** | `jdk.ThreadStart` / `jdk.ThreadPark` | Troubleshoot thread leaks / blocking |
| **CPU** | `jdk.ExecutionSample` | Flame graph source data |
| **Class loading** | `jdk.ClassLoad` / `jdk.ClassUnload` | Troubleshoot ClassLoader leaks |
| **IO** | `jdk.FileRead` / `jdk.SocketRead` | Troubleshoot IO blocking |
| **Exceptions** | `jdk.JavaExceptionThrow` | Troubleshoot exception storms |
| **JVM** | `jdk.JVMInformation` / `jdk.OSInformation` | Environment |

## Which Events to Check by Failure Type

### Which Events to Check Before OOM

Start JFR before OOM occurs, analyze post-incident:

| Event | What to Check |
|---|---|
| `jdk.ObjectAllocationSample` | Who is allocating large numbers of objects (find hotspots) |
| `jdk.GCHeapSummary` | Heap growth curve (when did it start growing) |
| `jdk.GarbageCollection` | GC frequency + reclaim rate (unable to reclaim = leak) |
| `jdk.JavaExceptionThrow` | Whether there are many OOM exception precursors |

**Analysis steps**:
1. Open `.jfr` file in JMC
2. Check GC Heap trend -> when did it stop dropping
3. Check Object Allocation Sample -> which class allocates the most
4. Correlate timeline -> which business traffic segment caused it

### Which Events to Check for CPU 100%

| Event | What to Check |
|---|---|
| `jdk.ExecutionSample` | Flame graph, which method consumes the most CPU |
| `jdk.CPULoad` | CPU load trend |
| `jdk.ThreadCPULoad` | Which thread consumes CPU |

**Analysis steps**:
1. JMC flame graph to find the widest method
2. Check if GC threads are consuming CPU (`GC Thread` consuming most -> actually a GC issue)
3. Check if business thread is in an infinite loop (fixed stack)

### Which Events to Check Before Deadlock

| Event | What to Check |
|---|---|
| `jdk.JavaMonitorEnter` | Lock wait duration + which lock |
| `jdk.JavaMonitorWait` | `wait()` wait duration |
| `jdk.ThreadPark` | Thread park blocking |
| `jdk.ExecutionSample` | Thread stack (see where it's stuck) |

**Note**: JFR does not automatically detect deadlocks. For deadlock detection use `jcmd <pid> Thread.print`, check for `Found Java deadlock`.

**JFR for deadlock precursors**:
1. Check `JavaMonitorEnter` wait duration distribution -> P99 long wait = intense lock contention
2. Check timeline -> when did wait spikes begin
3. Correlate `ExecutionSample` -> what method are threads waiting for a lock in

### Which Events to Check for High-Frequency GC

| Event | What to Check |
|---|---|
| `jdk.GarbageCollection` | GC count + duration + cause |
| `jdk.GCHeapSummary` | Heap before/after occupancy + pause time |
| `jdk.G1HeapRegionInformation` (G1) | Region occupancy |
| `jdk.ObjectAllocationSample` | Allocation hotspots |

**Analysis steps**:
1. Check GC frequency + cause (`Allocation Rate` / `System.gc()` / `Metaspace`)
2. Check Young GC vs Full GC ratio
3. Check if old generation drops after GC (not dropping = leak)
4. Correlate allocation sampling -> find allocation hotspots

### Which Events to Check for Class Loading Leak

| Event | What to Check |
|---|---|
| `jdk.ClassLoad` | Number of loaded classes + ClassLoader |
| `jdk.ClassLoaderStatistics` | ClassLoader count + occupancy |

**Analysis steps**:
1. Check ClassLoader count trend
2. Check which type of ClassLoader is continuously growing
3. Correlate `ClassLoad` events to see what classes are being loaded

## JFR Startup and Collection

### Configure Always-On at Startup

Recommended for production: enable JFR at startup so data is always available post-incident.

```
-XX:StartFlightRecording=filename=/var/log/jfr/app.jfr,maxage=1h,maxsize=100M,settings=profile
-XX:FlightRecorderOptions=stackdepth=64
```

**Parameter explanation**:
- `maxage=1h`: Rolling, 1 hour of data
- `maxsize=100M`: File size limit
- `settings=profile`: High sampling rate (vs `default` which uses fewer resources)
- `stackdepth=64`: Stack depth (default 64, increase to 128 for complex applications)

### Temporary 30-Second Collection

Commonly used for reactive troubleshooting:

```bash
# Start 30-second collection
jcmd <pid> JFR.start duration=30s filename=/tmp/recording.jfr

# Inside container (PID 1)
jcmd 1 JFR.start duration=30s filename=/tmp/recording.jfr
```

### Continuous Background Collection

```bash
# Start in background, run until stopped
jcmd <pid> JFR.start filename=/tmp/cont.jfr maxage=1h maxsize=100M settings=profile

# Check running recordings
jcmd <pid> JFR.check

# Stop and dump to file
jcmd <pid> JFR.stop name=1 filename=/tmp/final.jfr
```

### Online Streaming (JDK 14+)

No disk output required; consume JFR events within the application:

```java
import jdk.jfr.consumer.EventStream;

try (EventStream stream = EventStream.openRepository()) {
    // Real-time GC monitoring
    stream.onEvent("jdk.GarbageCollection", e -> {
        System.out.println("GC: " + e.getDuration("duration") + "ms");
        if (e.getDuration("duration").toMillis() > 100) {
            alertService.notify("Long GC detected!");
        }
    });

    // Real-time lock wait monitoring
    stream.onEvent("jdk.JavaMonitorEnter", e -> {
        if (e.getDuration("duration").toMillis() > 50) {
            log.warn("Long lock wait: " + e.getDuration("duration"));
        }
    });

    stream.startAsync();
    Thread.sleep(Long.MAX_VALUE);
}
```

**Use cases**:
- Embedded real-time monitoring and alerting in application
- APM vendor integration
- No dependency on external tools

## JMC (JDK Mission Control) Analysis

Official GUI tool for viewing JFR files.

### Installation

Download: `https://github.com/openjdk/jmc`

### Main Views

1. **Overview**: CPU/memory/GC/threads summary
2. **GC Configuration / Pauses**: GC frequency, pause time distribution
3. **Memory**: Heap occupancy trend, allocation hotspots
4. **Threads**: Thread state distribution, lock waits
5. **Code**: Method CPU sampling
6. **I/O**: File / network IO
7. **System**: JVM parameters, environment

### JMC Flame Graphs

JDK 14+ JMC supports flame graphs:
1. Open `.jfr` file
2. Select "Method Profile" / "Memory" view
3. Click the flame graph button

**Reading the graph**:
- X-axis: Call stack expanded
- Y-axis: Stack depth
- Width: CPU/memory time proportion
- Find the widest "flat top" -> optimization target

## Command-Line JFR Analysis

You can view JFR content without installing JMC:

```bash
# View all events (default first 10)
jfr print recording.jfr

# View specific events
jfr print --events jdk.GarbageCollection recording.jfr

# View specific events + JSON format
jfr print --events jdk.JavaMonitorEnter --json recording.jfr

# View summary statistics
jfr summary recording.jfr

# By category
jfr print --categories "GC" recording.jfr
```

## JFR vs async-profiler

| Dimension | JFR | async-profiler |
|---|---|---|
| Source | Oracle / OpenJDK official | Community open source |
| Integration | Built into JVM | External agent |
| Flame graphs | Built-in via JMC / `jfr print` | Directly generates HTML |
| Event types | Many (GC/locks/IO/class loading...) | Few (CPU/Alloc/Lock) |
| Online streaming | Supported (JDK 14+) | Not supported |
| Production always-on | Recommended | Possible |
| Ease of use | GUI (JMC) + CLI | CLI + flame graphs |

**Choose**:
- Global troubleshooting, long-running monitoring -> JFR
- Quick flame graphs, find CPU/Alloc hotspots -> async-profiler
- Production environment -> JFR always-on at startup + async-profiler for temporary sampling

## Practical Analysis Workflows

### Workflow 1: OOM Post-Incident Analysis

1. **Enable JFR at startup**: `-XX:StartFlightRecording=...`
2. **OOM triggers**: JVM does not exit (`HeapDumpOnOutOfMemoryError` gets dump)
3. **Post-incident JFR review**:
   - `jfr print --events jdk.GCHeapSummary recording.jfr` to see heap growth curve
   - `jfr print --events jdk.ObjectAllocationSample recording.jfr` to see allocation hotspots
   - Open in JMC to correlate timeline
4. **Get dump and analyze with MAT**: Find GC Root chain to locate leak point
5. **Combine JFR + dump**: Dump shows leak point, JFR shows when the leak started

### Workflow 2: CPU 100% Troubleshooting

1. **Start 30-second JFR**: `jcmd <pid> JFR.start duration=30s filename=/tmp/cpu.jfr`
2. **JMC flame graph**: Find the widest method
3. **Check if GC is consuming CPU**: `jfr print --events jdk.GarbageCollection recording.jfr`
4. **Check thread CPU distribution**: `jdk.ThreadCPULoad` event
5. **Combine with jstack**: `jcmd <pid> Thread.print` to see infinite loop stack

### Workflow 3: Deadlock / Lock Contention Troubleshooting

1. **Start 30-second JFR**
2. **Check lock waits**: `jfr print --events jdk.JavaMonitorEnter recording.jfr`
3. **Sort by wait duration**: Find the longest lock waits
4. **Correlate thread stacks**: `jdk.ExecutionSample` to see what method threads are waiting for a lock in
5. **Combine with jstack**: `jcmd <pid> Thread.print` to check for `Found Java deadlock`

### Workflow 4: High-Frequency GC Troubleshooting

1. **Start 30-second JFR**
2. **Check GC events**: `jfr print --events jdk.GarbageCollection recording.jfr`
3. **Check cause field**: Is it `Allocation Rate` or `System.gc()` or `Metaspace`
4. **Check heap before/after occupancy**: `jdk.GCHeapSummary` to see if old generation drops
5. **Check allocation hotspots**: `jdk.ObjectAllocationSample` to find who is allocating
6. **Decision**:
   - Old generation not dropping -> memory leak, get dump
   - Allocation too fast -> optimize code to reduce allocation
   - `System.gc()` -> add `-XX:+DisableExplicitGC`

## JFR Configuration Templates

### Temporary Troubleshooting (30 seconds)

```
-XX:StartFlightRecording=duration=30s,filename=/tmp/r.jfr,settings=profile
```

### Production Always-On

```
-XX:StartFlightRecording=filename=/var/log/jfr/app.jfr,maxage=1h,maxsize=100M,settings=profile,disk=true
-XX:FlightRecorderOptions=stackdepth=64
```

### Low-Overhead Monitoring

```
-XX:StartFlightRecording=filename=/var/log/jfr/app.jfr,maxage=24h,maxsize=500M,settings=default
-XX:FlightRecorderOptions=stackdepth=32
```

`settings=default` has lower sampling rate than `profile`, with less overhead, suitable for long-term monitoring.

## Reference Entries

- **SKILL.md**: Main entry for this skill, jump by symptom
- **Diagnostic command details**: `diagnostic_commands.md` (jcmd/jstack/jmap output fields)
- **GC troubleshooting**: `gc_tuning_guide.md` (reactive diagnostic perspective)
- **Container troubleshooting**: `container_troubleshooting.md` (JFR disk output configuration inside containers)
- **Proactive JMH + JFR**: `../../performance-tuning/references/jmh_profiling.md` (JMH benchmarking + JFR production always-on)
- Official JFR documentation: `https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html#starting-a-recording-on-a-running-java-application`
- JMC download: `https://github.com/openjdk/jmc`
- JFR online streaming: `https://docs.oracle.com/en/java/javase/17/docs/api/jdk.jfr/jdk/jfr/consumer/EventStream.html`
