# JVM Troubleshooting Inside Containers

This file supplements SKILL.md, focusing specifically on **K8s / Docker container** JVM troubleshooting. The container environment differs significantly from physical machines; common pitfalls are organized into separate sections. Read as needed when a user reports "JVM won't start inside container / gets killed / can't see process".

## Container vs Physical Machine Differences

| Dimension | Physical Machine | Container |
|---|---|---|
| Memory visible to JVM | Physical memory | cgroup limit (JDK 17 recognizes by default) |
| Process PID | Any | Usually PID 1 (container main process) |
| JDK tools | Complete | May only have JRE (minimal images) |
| JFR disk output | Local disk | emptyDir / PVC / temporary volume |
| Kernel logs | `dmesg` accessible | Requires host permissions to see OOM killer |
| ulimit | Default lenient | Often tightened inside containers |

## Failure Category Quick Reference (Container-Specific)

| Symptom | Jump to |
|---|---|
| Container OOM killed (exit 137) | [Container OOM Killed Troubleshooting](#container-oom-killed-troubleshooting) |
| `jps` cannot see process inside container | [PID 1 Issue](#pid-1-issue) |
| Container only has JRE, no JDK tools | [JRE-only Image Troubleshooting](#jre-only-image-troubleshooting) |
| JVM startup reports "Could not reserve enough space" | [Heap Exceeds Container Memory](#heap-exceeds-container-memory) |
| JFR / dump fails to write to disk | [Persistent Volume Configuration](#persistent-volume-configuration) |
| Thread count limited inside container | [ulimit / Thread Count](#ulimit--thread-count) |

## Container OOM Killed Troubleshooting

**Symptom**: K8s Pod status `OOMKilled`, exit code 137. Docker `docker inspect` shows `OOMKilled: true`.

**Root cause**: Container cgroup memory limit < JVM actual usage. **This is not a JVM OOM** -- the kernel kills the process before the JVM does.

### Troubleshooting Workflow

1. **Confirm it is a kernel OOM killer**:
   ```bash
   # K8s: Check Pod events
   kubectl describe pod <pod> | grep -A 5 -i oom

   # Docker: Check container status
   docker inspect <container> | grep -i oom

   # Host: Check kernel logs (requires host permissions)
   dmesg | grep -i "killed process"
   ```

2. **Check if JVM is actually using that much memory**:
   ```bash
   # Enter container
   kubectl exec <pod> -c <container> -- jcmd 1 VM.flags

   # Check heap configuration
   kubectl exec <pod> -c <container> -- jcmd 1 GC.heap_info
   ```

3. **Calculate JVM actual memory**:
   ```
   JVM total memory = Heap (Xmx)
                   + Metaspace (MaxMetaspaceSize)
                   + Thread stacks (Xss x thread count)
                   + Direct memory (MaxDirectMemorySize)
                   + CodeCache (ReservedCodeCacheSize)
                   + GC data structures
                   + JVM itself (approx 200-400MB)
   ```

### Common Root Causes and Countermeasures

| Root Cause | Manifestation | Countermeasure |
|---|---|---|
| `-Xmx` exceeds container memory | JVM startup fails or gets killed during runtime | Use `-XX:MaxRAMPercentage=75.0` instead of fixed `-Xmx` |
| `UseContainerSupport` not enabled | JDK 8u191- cannot see cgroup | Upgrade to JDK 17 (enabled by default) |
| Direct memory not limited | Netty / NIO off-heap memory grows | Add `-XX:MaxDirectMemorySize=256m` |
| Metaspace not limited | Dynamic proxy class bloat | Add `-XX:MaxMetaspaceSize=512m` |
| Too many threads | Each thread uses 1M stack, 1000 threads = 1GB | Reduce `-Xss=256k` or decrease thread pool size |
| CodeCache too large | Excessive JIT compilation | Default 240M is sufficient; adjust down in special scenarios |

### Configuration Recommendations (K8s / Docker)

**K8s deployment**:
```yaml
spec:
  containers:
  - name: app
    resources:
      requests:
        memory: "2Gi"
      limits:
        memory: "4Gi"   # cgroup limit
    env:
    - name: JAVA_OPTS
      value: >-
        -XX:+UseContainerSupport
        -XX:InitialRAMPercentage=50.0
        -XX:MaxRAMPercentage=75.0
        -XX:+HeapDumpOnOutOfMemoryError
        -XX:HeapDumpPath=/var/log/dumps/
        -Xlog:gc*:file=/var/log/gc.log:time,uptime:filecount=5,filesize=10M
```

**Recommended ratios**:
- Heap occupies 50-75% of container memory (`MaxRAMPercentage`)
- Leave 25-50% for Metaspace + stacks + direct memory + JVM itself
- K8s limit should not equal request (prevent being killed on sudden spikes)

## PID 1 Issue

**Symptom**: `jps` cannot see the JVM process inside the container, or `jstack <pid>` fails.

**Root cause**: The container's main process is PID 1. `jps` by default only lists non-PID 1 JVMs. Use `jcmd` / `jstack` with PID 1 instead.

### Troubleshooting

```bash
# Wrong: jps cannot see
kubectl exec <pod> -- jps

# Correct: Use PID 1 directly
kubectl exec <pod> -- jcmd 1 VM.flags
kubectl exec <pod> -- jstack 1
kubectl exec <pod> -- jstat -gcutil 1 1000

# Or find the Java process first
kubectl exec <pod> -- ps -ef | grep java
```

### JDK Tool Permission Issues Inside Containers

**Symptom**: `jcmd 1 Thread.print` reports `Permission denied` or `Operation not permitted`.

**Root cause**: Container user is not root; JDK tools use ptrace to attach to the process, which requires permissions.

**Countermeasures**:
- K8s: `securityContext.capabilities.add: [SYS_PTRACE]`
- Docker: `--cap-add=SYS_PTRACE`
- Or run container as root user (not recommended)

```yaml
spec:
  containers:
  - name: app
    securityContext:
      capabilities:
        add: ["SYS_PTRACE"]
```

## JRE-only Image Troubleshooting

**Symptom**: `jcmd` / `jstack` / `jmap` commands not found. Minimal images (e.g., `eclipse-temurin:17-jre-alpine`) only have JRE without JDK tools.

### Countermeasures

**Option 1: Use a sidecar container with JDK tools**:
```yaml
spec:
  containers:
  - name: app              # Main container, JRE-only
    image: app:latest
  - name: debug            # Sidecar with JDK
    image: eclipse-temurin:17-jdk-alpine
    command: ["sleep", "infinity"]
```

When troubleshooting, enter the sidecar:
```bash
kubectl exec <pod> -c debug -- jcmd <app-pid> ...
# But requires shared PID namespace
```

**Option 2: Use the `jattach` tool** (lightweight, no JDK required):
```dockerfile
# Install jattach in the image
RUN apk add --no-cache jattach
```
```bash
jattach <pid> jcmd GC.heap_info
jattach <pid> dump heap /tmp/heap.hprof
```

**Option 3: Install `procps` + `openjdk17-jdk` in the image** (for debugging, use cautiously in production):
```dockerfile
RUN apk add --no-cache openjdk17-jdk procps
```

**Option 4 (Recommended): Use JFR in production**, no JDK tools needed. JFR is embedded in the JVM; configure `StartFlightRecording` at startup and write to a log volume.

## Persistent Volume Configuration

**Symptom**: `-XX:HeapDumpPath=/var/log/dumps/` but no dump on OOM. JFR configured but `.jfr` files not found.

**Root cause**: Path inside container is not mounted to a persistent volume; data is lost on container restart.

### Configuration

**K8s**:
```yaml
spec:
  containers:
  - name: app
    volumeMounts:
    - name: dumps
      mountPath: /var/log/dumps
    - name: gc-log
      mountPath: /var/log
  volumes:
  - name: dumps
    persistentVolumeClaim:
      claimName: dumps-pvc
  - name: gc-log
    emptyDir: {}
```

**JVM Parameters**:
```
-XX:+HeapDumpOnOutOfMemoryError
-XX:HeapDumpPath=/var/log/dumps/           # Mounted to PVC
-Xlog:gc*:file=/var/log/gc.log:time,uptime:filecount=10,filesize=10M
-XX:StartFlightRecording=filename=/var/log/jfr/app.jfr,maxage=1h,maxsize=100M,settings=profile
-XX:FlightRecorderOptions=stackdepth=64
```

**Notes**:
- emptyDir is lost on container restart; only suitable for GC logs
- dump / JFR needs persistence -> PVC or host hostPath
- K8s `terminationGracePeriodSeconds` gives JVM time to write dump

## ulimit / Thread Count

**Symptom**: `OutOfMemoryError: unable to create new native thread`, but heap memory is sufficient.

**Root cause**: ulimit is tightened inside the container; thread count has reached the limit.

### Troubleshooting

```bash
# Enter container to check ulimit
kubectl exec <pod> -- bash -c "ulimit -a"

# Check thread count
kubectl exec <pod> -- bash -c "ps -ef | wc -l"
# Or
kubectl exec <pod> -- jcmd 1 Thread.print | grep "java.lang.Thread.State" | wc -l
```

### Countermeasures

| Root Cause | Countermeasure |
|---|---|
| ulimit -u too small | Increase via K8s `securityContext`, or Docker `--ulimit nproc=65535:65535` |
| Thread pool leak | Check if `new Thread` is not in a pool, `submit` without `shutdown` |
| Virtual threads (JDK 21+) | Virtual threads do not count toward native thread limit, but not available on JDK 17 |

**K8s Configuration**:
```yaml
spec:
  containers:
  - name: app
    securityContext:
      runAsNonRoot: true
      # ulimit adjustment needs to be done in the image or init container
```

**Adjust ulimit in the image** (Dockerfile):
```dockerfile
RUN echo "nproc 65535" >> /etc/security/limits.conf
```

## Heap Exceeds Container Memory

**Symptom**: JVM startup fails with `Could not reserve enough space for object heap`.

**Root cause**: `-Xmx` exceeds the container cgroup limit, or `MaxRAMPercentage` calculates a heap that exceeds container memory.

### Troubleshooting

```bash
# Check cgroup limit
kubectl exec <pod> -- cat /sys/fs/cgroup/memory.max       # cgroup v2
kubectl exec <pod> -- cat /sys/fs/cgroup/memory/memory.limit_in_bytes  # cgroup v1

# Check JVM startup parameters
kubectl exec <pod> -- jcmd 1 VM.flags 2>&1 | grep -i heap
```

### Countermeasures

- Use `-XX:MaxRAMPercentage=75.0` instead of fixed `-Xmx` (auto-adapts to cgroup)
- Leave 25% for non-heap memory
- Do not use `-XX:+UseCGroupMemoryLimitForBounds` (deprecated in JDK 11)

## Network and DNS Failures

Slow DNS resolution and connection timeouts inside containers are common:

**Symptom**: Application starts slowly, HTTP call timeouts.

**Root causes**:
- Slow DNS resolution inside container (K8s CoreDNS pressure)
- Connection pool not reused, new TCP connection each time
- Container network plugin (Calico/Flannel) overhead

**Troubleshooting**:
```bash
# Test DNS inside container
kubectl exec <pod> -- nslookup example.com

# Check connections
kubectl exec <pod> -- netstat -anp | grep ESTABLISHED | wc -l
```

**Countermeasures**:
- Add JVM parameter `-Dsun.net.inetaddr.ttl=30` (DNS cache for 30 seconds)
- Use connection pool for HTTP client (OkHttp / Apache HttpClient)
- Pre-pull images to reduce startup time

## Practical Troubleshooting Command Quick Reference

```bash
# === K8s ===
kubectl describe pod <pod> | grep -A 5 -i oom        # OOMKilled events
kubectl logs <pod> -c <container> --previous         # Previous container logs
kubectl top pod <pod>                                 # Real-time memory/CPU

# === Inside Container ===
jcmd 1 VM.flags                                       # JVM startup parameters (PID 1)
jcmd 1 GC.heap_info                                   # Heap info
jcmd 1 Thread.print                                   # Thread stack
jcmd 1 JFR.start duration=30s filename=/tmp/r.jfr     # JFR collection

# === cgroup ===
cat /sys/fs/cgroup/memory.max                         # Memory limit (cgroup v2)
cat /sys/fs/cgroup/memory/memory.limit_in_bytes       # cgroup v1
cat /sys/fs/cgroup/memory.peak                        # Actual peak (cgroup v2)

# === Host (requires permissions) ===
dmesg | grep -i "killed process"                      # Kernel OOM killer logs
```

## Reference Entries

- **SKILL.md**: Main entry for this skill, jump by symptom
- **Diagnostic command details**: `diagnostic_commands.md` (jcmd/jstack/jmap output fields)
- **GC troubleshooting guide**: `gc_tuning_guide.md` (reactive diagnostic perspective)
- **JFR post-incident analysis**: `jfr_analysis.md` (JFR disk output + analysis inside containers)
- Official container support documentation: `https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html#containers`
- K8s Java tuning: `https://kubernetes.io/docs/concepts/containers/`
- jattach tool: `https://github.com/jvm-profiling-tools/jattach`
