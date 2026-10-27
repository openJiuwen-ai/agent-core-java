---
name: jvm-troubleshoot
description: JVM production troubleshooting handbook. Actively applied when an application reports OOM, CPU 100%, frequent GC, thread deadlock, class loading leak, startup failure, response lag, JVM killed inside a container, or module system reflection failure. Keywords: OOM, OutOfMemoryError, CPU high, GC, Full GC, deadlock, jstack, jmap, jstat, JVM tuning, Metaspace, StackOverflow, OOMKilled, container, cgroup, PID 1, JRE-only, InaccessibleObjectException, --add-opens, module system, JPMS. Not applicable to: coding standard issues (use coding-standard), agent team assembly (use agent-team-guide), non-JVM failures, proactive performance tuning and JVM parameter selection (use performance-tuning).
---

# JVM Production Troubleshooting Handbook

This skill is organized by "symptom -> diagnostic workflow -> root cause table -> fix", covering 7 categories of high-frequency JVM failures. All commands require first running `jps -lvm` to obtain the `<pid>`.

**Boundary with performance-tuning**:
- jvm-troubleshoot (this skill): reactive troubleshooting (OOM/CPU 100% has already occurred) -> follow "symptom -> diagnosis -> fix"
- performance-tuning: proactive optimization (selecting GC before deployment, tuning JVM parameters, writing high-performance code) -> follow "layer -> selection -> optimization -> verification"

## Failure Category Quick Reference

| Symptom | Jump to |
|---|---|
| Application reports `OutOfMemoryError` | [OOM Troubleshooting](#oom-troubleshooting) |
| CPU usage at 100% | [CPU Troubleshooting](#cpu-100-troubleshooting) |
| Frequent GC, application lag | [High-Frequency GC Troubleshooting](#high-frequency-gc-troubleshooting) |
| Slow response, timeout, no error | [Performance Troubleshooting](#performance-troubleshooting) |
| Thread deadlock, program stuck | [Thread Deadlock Troubleshooting](#thread-deadlock-troubleshooting) |
| `ClassNotFoundException` / `NoClassDefFoundError` | [Class Loading Troubleshooting](#class-loading-troubleshooting) |
| `InaccessibleObjectException` / module system reflection failure | [Module System Troubleshooting](#module-system-troubleshooting) |
| JVM startup failure, immediate exit | [Startup Failure Troubleshooting](#startup-failure-troubleshooting) |
| JVM killed inside container / `jps` cannot see / JRE-only | [Container Troubleshooting](#container-troubleshooting) |

## OOM Troubleshooting

OOM types and identification:

| OOM Type | Error Keyword | Root Cause | Troubleshooting |
|---|---|---|---|
| **Java heap space** | `OutOfMemoryError: Java heap space` | Heap memory insufficient, objects not released | `jmap -dump` to get dump -> MAT to analyze large objects -> check static Map/list for continuous growth |
| **GC overhead** | `GC overhead limit exceeded` | GC spending too much time reclaiming too little memory | Same as above; usually a memory leak or heap too small |
| **Metaspace** | `OutOfMemoryError: Metaspace` | Too much class metadata (dynamically generated classes, ClassLoader leak) | Adjust `-XX:MaxMetaspaceSize`; check dynamic proxies/CGLIB; see [Class Loading Troubleshooting](#class-loading-troubleshooting) |
| **Direct buffer** | `OutOfMemoryError: Direct buffer memory` | NIO direct memory not released | Check `ByteBuffer.allocateDirect` release; adjust `-XX:MaxDirectMemorySize` |
| **StackOverflow** | `StackOverflowError` | Recursion too deep or stack frame too large | Check recursion termination condition; adjust `-Xss` to increase stack (symptomatic, not root fix) |
| **unable to create thread** | `OutOfMemoryError: unable to create new native thread` | Thread count reached OS limit | `jstack` to see thread count; check thread pool leak; adjust `ulimit -u` |

**Stop the bleeding**: First enable `-XX:+HeapDumpOnOutOfMemoryError` to get a dump, then restart. **Root fix**: Use MAT to analyze the dump, find the GC Root chain, and locate the leak point.

## CPU 100% Troubleshooting

**Diagnostic workflow** (4-step identification):

1. `top` to find the **process** PID with high CPU usage
2. `top -Hp <pid>` to find the **thread** TID with high CPU usage (decimal)
3. `printf "%x\n" <tid>` to convert to hexadecimal
4. `jstack <pid> | grep <hexadecimal tid> -A 30` to see what the thread is doing

**Common root causes**:

| Root Cause | Manifestation | Countermeasure |
|---|---|---|
| Infinite loop / infinite recursion | jstack stack frames repeating or fixed | Fix code logic, add termination condition |
| Regex catastrophic backtracking | Stack in `Pattern.matcher` | Simplify regex, or use pre-compiled + timeout |
| Large nested collection iteration | Stack in `for` / `Stream` | Change algorithm, reduce O(n^2) to O(n) |
| Serializing large objects | Stack in `ObjectOutputStream` | Limit object size, or use streaming processing |
| GC threads consuming all CPU | Multiple `GC task` threads with high CPU | Actually a memory issue, see [High-Frequency GC Troubleshooting](#high-frequency-gc-troubleshooting) |
| Intensive crypto hashing | Stack in `MessageDigest` | Use a faster algorithm, or execute asynchronously |

**Note**: If CPU is high but business logic is not complex, first check whether GC threads are consuming all CPU (check GC logs).

## High-Frequency GC Troubleshooting

**Diagnostic workflow**:

1. Enable GC logs: `-Xlog:gc*:file=gc.log:time,uptime:filecount=10,filesize=10M` (JDK9+)
2. Analyze logs with GCViewer or gceasy.io
3. Check key metrics:
   - **Full GC frequency**: Normal is once every few hours; once every few minutes is problematic
   - **GC throughput**: GC time ratio should be < 5%; > 10% requires investigation
   - **Old generation occupancy**: Continuously growing without dropping = memory leak
4. `jstat -gcutil <pid> 1000` to observe occupancy changes across generations

**Common root causes and countermeasures**:

| Root Cause | Countermeasure |
|---|---|
| Heap too small | Increase `-Xmx` |
| Object creation too fast | Caching, object pool, `StringBuilder` |
| Memory leak | Get dump to find leak point (see OOM Troubleshooting) |
| Large objects promoted directly to old generation | Adjust `-XX:PretenureSizeThreshold` |
| Metaspace insufficient | Adjust `-XX:MaxMetaspaceSize` (see OOM Metaspace) |
| `System.gc()` being called | Add `-XX:+DisableExplicitGC` to disable |

**GC collector selection and production parameter configuration**: Proactive configuration belongs to the performance-tuning skill, see `../performance-tuning/references/gc_tuning.md`. This skill only covers reactive troubleshooting.

## Performance Troubleshooting

When response is slow but CPU is not high and GC is normal:

1. `jstack <pid> > thread.txt` to see thread state distribution
2. Count `BLOCKED` / `WAITING` threads: `grep "java.lang.Thread.State" thread.txt | sort | uniq -c`
3. Many `BLOCKED` -> lock contention, see [Thread Deadlock Troubleshooting](#thread-deadlock-troubleshooting)
4. Many `WAITING` -> thread pool idle or waiting for external resources (DB/HTTP)
5. Check if threads are stuck in `SocketRead` -> RPC timeout or slow peer
6. `jstat -gcutil <pid> 1000` to rule out GC issues

## Thread Deadlock Troubleshooting

**Diagnosis**: `jstack <pid> | grep -A 20 "Found Java deadlock"` -- jstack automatically detects synchronized deadlocks and prints them.

**synchronized deadlock**:
- Manifestation: `jstack` reports "Found 1 deadlock", two threads waiting for each other's lock
- Root cause: Nested `synchronized` blocks with inconsistent lock ordering
- Fix: Unify lock ordering, or use `tryLock(timeout)`

**Lock deadlock (ReentrantLock)**:
- Manifestation: Thread `WAITING` in `AbstractQueuedSynchronizer`, but jstack **does not report deadlock** (Lock deadlocks are not automatically detected)
- Diagnosis: Check `jstack` stack for threads stuck in `lock()` calls
- Fix: Use `tryLock(timeout, unit)` instead of `lock()`, release and retry on timeout

**Thread leak (pseudo-deadlock)**:
- Manifestation: Thread count keeps growing, eventually `unable to create thread`
- Diagnosis: `jstack <pid> | grep "java.lang.Thread.State" | wc -l` to count total threads
- Fix: Check if thread pool is leaking (`new Thread` not in a pool, `submit` without `shutdown`)

## Class Loading Troubleshooting

`ClassNotFoundException` / `NoClassDefFoundError` / `LinkageError`:

1. `jmap -clstats <pid>` to see ClassLoader count and loaded class count
2. Abnormally many ClassLoaders -> ClassLoader leak (common in hot deployment, dynamic proxies)
3. Class not found -> check classpath: `jcmd <pid> VM.classloader_stats`
4. Same class with multiple version conflicts -> `jmap -clstats` to see if multiple ClassLoaders load the same class

**Metaspace leak**:
- Symptom: Metaspace does not drop after Full GC, eventually OOM Metaspace
- Diagnosis: `jmap -clstats <pid>` to see if ClassLoader count keeps growing
- Root cause: Dynamic proxies (CGLIB/ByteBuddy) generating new classes each time without caching, or web container hot deployment not cleaning up old ClassLoaders
- Fix: Cache proxy classes, fix hot deployment leak, adjust `-XX:MaxMetaspaceSize`

## Module System Troubleshooting

Failures specific to JDK 9+ module system (JPMS). Guaranteed to encounter on JDK 17.

### InaccessibleObjectException

**Symptom**: Reflection call reports `java.lang.reflect.InaccessibleObjectException: Unable to make field accessible: module java.base does not "opens java.lang" to unnamed module`

**Root cause**: JDK 9+ module system protects non-exported packages; reflection access requires explicit opening.

**Troubleshooting**:
1. Check which line of the error mentions "module X does not opens Y"
2. Add the corresponding `--add-opens` for that module

**Common `--add-opens` quick reference**:

| Scenario | Error Module | Parameter |
|---|---|---|
| Reflective access to `String` internals | `java.base/java.lang` | `--add-opens java.base/java.lang=ALL-UNNAMED` |
| Reflective access to collection internals | `java.base/java.util` | `--add-opens java.base/java.util=ALL-UNNAMED` |
| Netty NIO access | `java.base/java.nio` | `--add-opens java.base/java.nio=ALL-UNNAMED` |
| Reflective access to `Method` | `java.base/java.lang.reflect` | `--add-opens java.base/java.lang.reflect=ALL-UNNAMED` |
| Kryo / Gson serialization | `java.base/java.lang` etc. | Usually requires multiple `--add-opens` |
| Spring / Hibernate reflection | Multiple | See framework documentation |

**Configuration location**:
- Command line: `java --add-opens ... -jar app.jar`
- MANIFEST.MF: `Add-Opens: java.base/java.lang java.base/java.util`
- K8s: `JAVA_OPTS` environment variable

### `--add-opens` vs `--add-exports`

| Parameter | Purpose |
|---|---|
| `--add-opens` | Reflective access (open at runtime) |
| `--add-exports` | Compile-time + runtime access (non-reflective) |
| `--add-reads` | Let one module read another module |

**Choose**: Use `--add-opens` for reflection scenarios; use `--add-exports` for direct imports.

### Module Conflict

**Symptom**: Startup reports `Module resolution failed` or `module not found`.

**Troubleshooting**:
- Check if there are multiple versions of the same module
- Check module-path (`--module-path`) vs classpath
- Use `java --list-modules` to see loaded modules

### Module System Diagnostic Commands

```bash
# View module system parameters
jcmd <pid> VM.system_properties | grep jdk.module

# View loaded modules
java --list-modules

# Startup debugging
java --show-module-resolution -jar app.jar
```

## Container Troubleshooting

JVM failures specific to K8s / Docker containers. For detailed troubleshooting see `references/container_troubleshooting.md`.

**Quick reference**:

| Symptom | Root Cause | Countermeasure |
|---|---|---|
| Pod `OOMKilled` exit 137 | cgroup memory < JVM usage | Use `MaxRAMPercentage` instead of fixed `-Xmx` |
| `jps` cannot see JVM | Container main process is PID 1 | Use `jcmd 1 ...` |
| `jcmd` command not found | JRE-only image | Sidecar with JDK / use jattach / use JFR |
| `Could not reserve enough space` | `-Xmx` exceeds cgroup | Use `MaxRAMPercentage=75.0` |
| `unable to create new native thread` | Container ulimit tightened | Adjust `ulimit -u` or reduce thread count |
| JFR / dump lost on disk | Container path not mounted | Mount PVC to `HeapDumpPath` |

## Startup Failure Troubleshooting

JVM exits immediately on startup; check the error keyword:

| Error | Root Cause | Countermeasure |
|---|---|---|
| `Could not reserve enough space for object heap` | `-Xmx` exceeds physical memory | Reduce `-Xmx`, or check if other processes are using memory |
| `Invalid initial heap size` | `-Xms` / `-Xmx` parameter format error | Check parameter units (`4g` not `4096`) |
| `Incompatible version` | Compile-time JDK version > runtime | Run with same or higher JDK |
| `Unsupported major.minor version` | Class file version mismatch | Use `javap -verbose X.class` to check major version |
| `Unable to open jar` | Jar file corrupted or wrong path | Check `-jar` path and jar integrity |
| `Error: LinkageError` | Class conflict | Check classpath for duplicate classes |

## Command Quick Reference

JVM diagnostic commands organized by scenario:

```bash
# === Process Overview ===
jps -lvm                                      # List JVM processes
jcmd <pid> VM.flags                           # View JVM startup parameters
jcmd <pid> VM.system_properties               # View system properties

# === Heap Analysis ===
jmap -heap <pid>                              # Heap overview (generation occupancy)
jmap -histo:live <pid> | head -20             # Large object histogram (top 20)
jmap -dump:format=b,file=heap.hprof <pid>     # Export heap dump
jmap -finalizer_info <pid>                    # View finalizer queue

# === Thread Analysis ===
jstack <pid> > thread.txt                     # Thread stack
jstack -l <pid>                               # Thread stack with lock info
top -Hp <pid>                                 # Thread-level CPU usage

# === GC Analysis ===
jstat -gcutil <pid> 1000                      # Generation occupancy + GC count (refresh every second)
jstat -gccause <pid> 1000                     # Last GC cause
jcmd <pid> GC.heap_info                       # Heap info

# === Class Analysis ===
jmap -clstats <pid>                           # ClassLoader statistics
jcmd <pid> VM.classloader_stats               # ClassLoader status
jmap -permstat <pid>                          # Permanent generation statistics (JDK7)

# === System Level ===
top                                           # Process CPU/memory
iostat -x 1                                   # Disk IO
netstat -anp | grep <pid>                     # Network connections
```

## JVM Parameters

Monitoring parameters commonly enabled during troubleshooting:
```
-XX:+HeapDumpOnOutOfMemoryError               # Auto dump on OOM (essential for OOM troubleshooting)
-XX:HeapDumpPath=/var/log/dumps/              # Dump path
-XX:ErrorFile=/var/log/hs_err_%p.log          # Fatal error log
-Xlog:gc*:file=gc.log:time,uptime             # GC log
-XX:+PrintFlagsFinal                          # Print all JVM parameter final values
```

**Proactive tuning parameters (heap/GC collector/JIT configuration)**: Belongs to the performance-tuning skill, see `../performance-tuning/references/jvm_params.md`.

## Usage

1. **Locate by symptom**: First check the "Failure Category Quick Reference" table, jump to the corresponding section.
2. **Execute diagnostic workflow**: Each section has numbered steps; execute in order.
3. **Check root cause table**: After diagnosis, find countermeasures in the "Common Root Causes" table.
4. **Command details**: When command output is unclear, Read `references/diagnostic_commands.md`.
5. **GC troubleshooting**: When investigating GC issues, Read `references/gc_tuning_guide.md` (reactive diagnostic perspective). For proactive selection and configuration, see performance-tuning.
6. **Container troubleshooting**: For K8s/Docker container failures, Read `references/container_troubleshooting.md`.
7. **JFR analysis**: When using JFR for troubleshooting, Read `references/jfr_analysis.md`.
8. **Troubleshooting cases**: When an end-to-end reference is needed, Read `references/troubleshooting_cases.md`.
9. **Do not fabricate when uncertain**: JVM behavior is governed by source code and official documentation; this skill does not replace formal documentation.

## Reference Entries

- **Command details**: `references/diagnostic_commands.md` (output field meanings and examples for each JVM command)
- **GC troubleshooting guide**: `references/gc_tuning_guide.md` (GC problem troubleshooting from a reactive diagnostic perspective)
- **Container troubleshooting**: `references/container_troubleshooting.md` (JVM failures inside K8s/Docker containers: OOM killed / PID 1 / JRE-only)
- **JFR post-incident analysis**: `references/jfr_analysis.md` (JFR event quick reference, online streaming, JMC flame graphs)
- **Troubleshooting cases**: `references/troubleshooting_cases.md` (4 end-to-end cases: heap leak / CPU 100% / deadlock / Metaspace leak)
- **Proactive performance tuning**: `../performance-tuning/SKILL.md` (JVM parameter selection, GC collector selection, JIT tuning, JMH benchmarking)
- Official documentation: `https://docs.oracle.com/en/java/javase/17/docs/specs/man/` (JDK 17 tools)
- GC log analysis: `https://gceasy.io/`
- Heap dump analysis: MAT (`https://eclipse.org/mat/`)
- Project coding standards: `../coding-standard/SKILL.md`
