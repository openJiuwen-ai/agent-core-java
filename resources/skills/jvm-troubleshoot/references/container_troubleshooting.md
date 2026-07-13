# 容器内 JVM 故障排查

本文件补充 SKILL.md，专讲 **K8s / Docker 容器内** JVM 故障排查。容器环境与物理机差异大，常见排坑点独立成节。用户报"容器内 JVM 起不来 / 被 kill / 看不到进程"时按需读取。

## 容器内 vs 物理机差异

| 维度 | 物理机 | 容器 |
|---|---|---|
| JVM 看到的内存 | 物理内存 | cgroup limit（JDK 17 默认识别） |
| 进程 PID | 任意 | 通常 PID 1（容器主进程） |
| JDK 工具 | 完整 | 可能只有 JRE（精简镜像） |
| JFR 落盘 | 本地磁盘 | emptyDir / PVC / 临时卷 |
| 内核日志 | `dmesg` 可看 | 需宿主机权限看 OOM killer |
| ulimit | 默认宽松 | 容器内常收紧 |

## 故障分类速查（容器特定）

| 症状 | 跳转 |
|---|---|
| 容器被 OOM killed（exit 137） | [容器 OOM killed 排查](#容器-oom-killed-排查) |
| 容器内 `jps` 看不到进程 | [PID 1 问题](#pid-1-问题) |
| 容器只有 JRE 无 JDK 工具 | [JRE-only 镜像排查](#jre-only-镜像排查) |
| JVM 启动报 "Could not reserve enough space" | [堆超容器内存](#堆超容器内存) |
| JFR / dump 落盘失败 | [持久化卷配置](#持久化卷配置) |
| 容器内线程数受限 | [ulimit / 线程数](#ulimit--线程数) |

## 容器 OOM killed 排查

**现象**：K8s Pod 状态 `OOMKilled`，exit code 137。Docker `docker inspect` 看 `OOMKilled: true`。

**根因**：容器 cgroup 内存限制 < JVM 实际使用。**不是 JVM OOM** —— 内核在 JVM 之前杀进程。

### 排查流程

1. **确认是内核 OOM killer**：
   ```bash
   # K8s：看 Pod 事件
   kubectl describe pod <pod> | grep -A 5 -i oom

   # Docker：看容器状态
   docker inspect <container> | grep -i oom

   # 宿主机：看内核日志（需宿主机权限）
   dmesg | grep -i "killed process"
   ```

2. **看 JVM 是不是真的用了那么多内存**：
   ```bash
   # 进容器
   kubectl exec <pod> -c <container> -- jcmd 1 VM.flags

   # 看堆配置
   kubectl exec <pod> -c <container> -- jcmd 1 GC.heap_info
   ```

3. **算 JVM 实际内存**：
   ```
   JVM 总内存 = 堆 (Xmx)
             + Metaspace (MaxMetaspaceSize)
             + 线程栈 (Xss × 线程数)
             + 直接内存 (MaxDirectMemorySize)
             + CodeCache (ReservedCodeCacheSize)
             + GC 数据结构
             + JVM 自身（约 200-400MB）
   ```

### 常见根因与对策

| 根因 | 表现 | 对策 |
|---|---|---|
| `-Xmx` 超过容器内存 | JVM 启动失败或运行中被 kill | 用 `-XX:MaxRAMPercentage=75.0` 替代固定 `-Xmx` |
| 没开 `UseContainerSupport` | JDK 8u191- 看不到 cgroup | 升级 JDK 17（默认开） |
| 直接内存没限制 | Netty / NIO 堆外内存涨 | 加 `-XX:MaxDirectMemorySize=256m` |
| Metaspace 没限制 | 动态代理类膨胀 | 加 `-XX:MaxMetaspaceSize=512m` |
| 线程数过多 | 每线程 1M 栈，千线程 = 1GB | 调小 `-Xss=256k` 或减线程池大小 |
| CodeCache 太大 | JIT 编译多 | 默认 240M 够，特殊场景调小 |

### 配置建议（K8s / Docker）

**K8s deployment**：
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

**经验比例**：
- 堆占容器内存 50-75%（`MaxRAMPercentage`）
- 留 25-50% 给 Metaspace + 栈 + 直接内存 + JVM 自身
- K8s limit 不要等于 request（防突增被 kill）

## PID 1 问题

**现象**：容器内 `jps` 看不到 JVM 进程，或 `jstack <pid>` 失败。

**根因**：容器主进程是 PID 1。`jps` 默认只列非 PID 1 的 JVM。`jcmd` / `jstack` 用 PID 1 即可。

### 排查

```bash
# 错：jps 看不到
kubectl exec <pod> -- jps

# 对：直接用 PID 1
kubectl exec <pod> -- jcmd 1 VM.flags
kubectl exec <pod> -- jstack 1
kubectl exec <pod> -- jstat -gcutil 1 1000

# 或先找 Java 进程
kubectl exec <pod> -- ps -ef | grep java
```

### 容器内 JDK 工具权限问题

**现象**：`jcmd 1 Thread.print` 报 `Permission denied` 或 `Operation not permitted`。

**根因**：容器用户非 root，JDK 工具用 ptrace 附加到进程，需权限。

**对策**：
- K8s：`securityContext.capabilities.add: [SYS_PTRACE]`
- Docker：`--cap-add=SYS_PTRACE`
- 或用 root 用户运行容器（不推荐）

```yaml
spec:
  containers:
  - name: app
    securityContext:
      capabilities:
        add: ["SYS_PTRACE"]
```

## JRE-only 镜像排查

**现象**：`jcmd` / `jstack` / `jmap` 命令找不到。精简镜像（如 `eclipse-temurin:17-jre-alpine`）只有 JRE 无 JDK 工具。

### 对策

**方案 1：用 sidecar 容器带 JDK 工具**：
```yaml
spec:
  containers:
  - name: app              # 主容器，JRE-only
    image: app:latest
  - name: debug            # sidecar，带 JDK
    image: eclipse-temurin:17-jdk-alpine
    command: ["sleep", "infinity"]
```

排查时进 sidecar：
```bash
kubectl exec <pod> -c debug -- jcmd <app-pid> ...
# 但需共享 PID namespace
```

**方案 2：用 `jattach` 工具**（轻量，无需 JDK）：
```dockerfile
# 镜像内安装 jattach
RUN apk add --no-cache jattach
```
```bash
jattach <pid> jcmd GC.heap_info
jattach <pid> dump heap /tmp/heap.hprof
```

**方案 3：镜像装 `procps` + `openjdk17-jdk`**（调试用，生产慎用）：
```dockerfile
RUN apk add --no-cache openjdk17-jdk procps
```

**方案 4（推荐）：生产用 JFR**，无需 JDK 工具。JFR 内嵌 JVM，启动时配 `StartFlightRecording` 即可，落盘到日志卷。

## 持久化卷配置

**现象**：`-XX:HeapDumpPath=/var/log/dumps/` 但 OOM 时没 dump。JFR 配置了但找不到 `.jfr` 文件。

**根因**：容器内路径未挂载到持久卷，容器重启即丢。

### 配置

**K8s**：
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

**JVM 参数**：
```
-XX:+HeapDumpOnOutOfMemoryError
-XX:HeapDumpPath=/var/log/dumps/           # 挂载到 PVC
-Xlog:gc*:file=/var/log/gc.log:time,uptime:filecount=10,filesize=10M
-XX:StartFlightRecording=filename=/var/log/jfr/app.jfr,maxage=1h,maxsize=100M,settings=profile
-XX:FlightRecorderOptions=stackdepth=64
```

**注意**：
- emptyDir 容器重启即丢，只适合 GC 日志
- dump / JFR 要持久 → PVC 或宿主机 hostPath
- K8s `terminationGracePeriodSeconds` 给 JVM 时间写 dump

## ulimit / 线程数

**现象**：`OutOfMemoryError: unable to create new native thread`，但堆内存充足。

**根因**：容器内 ulimit 收紧，线程数达上限。

### 排查

```bash
# 进容器看 ulimit
kubectl exec <pod> -- bash -c "ulimit -a"

# 看线程数
kubectl exec <pod> -- bash -c "ps -ef | wc -l"
# 或
kubectl exec <pod> -- jcmd 1 Thread.print | grep "java.lang.Thread.State" | wc -l
```

### 对策

| 根因 | 对策 |
|---|---|
| ulimit -u 太小 | K8s `securityContext` 调高，或 Docker `--ulimit nproc=65535:65535` |
| 线程池泄漏 | 看是否 `new Thread` 未纳入池、`submit` 后没 `shutdown` |
| 虚拟线程（JDK 21+） | 虚拟线程不占原生线程数，但 JDK 17 不可用 |

**K8s 配置**：
```yaml
spec:
  containers:
  - name: app
    securityContext:
      runAsNonRoot: true
      # ulimit 调整需在镜像内或 init container
```

**镜像内调 ulimit**（Dockerfile）：
```dockerfile
RUN echo "nproc 65535" >> /etc/security/limits.conf
```

## 堆超容器内存

**现象**：JVM 启动失败，报 `Could not reserve enough space for object heap`。

**根因**：`-Xmx` 超过容器 cgroup 限制，或 `MaxRAMPercentage` 算出堆超过容器内存。

### 排查

```bash
# 看 cgroup 限制
kubectl exec <pod> -- cat /sys/fs/cgroup/memory.max       # cgroup v2
kubectl exec <pod> -- cat /sys/fs/cgroup/memory/memory.limit_in_bytes  # cgroup v1

# 看 JVM 启动参数
kubectl exec <pod> -- jcmd 1 VM.flags 2>&1 | grep -i heap
```

### 对策

- 用 `-XX:MaxRAMPercentage=75.0` 替代固定 `-Xmx`（自适应 cgroup）
- 留 25% 给非堆内存
- 别用 `-XX:+UseCGroupMemoryLimitForBounds`（JDK 11 已废弃）

## 网络与 DNS 故障

容器内 DNS 解析慢、连接超时常见：

**现象**：应用启动慢、HTTP 调用超时。

**根因**：
- 容器内 DNS 解析慢（K8s CoreDNS 压力）
- 连接池未复用，每次新建 TCP 连接
- 容器网络插件（Calico/Flannel）开销

**排查**：
```bash
# 进容器测 DNS
kubectl exec <pod> -- nslookup example.com

# 看连接
kubectl exec <pod> -- netstat -anp | grep ESTABLISHED | wc -l
```

**对策**：
- JVM 加 `-Dsun.net.inetaddr.ttl=30`（DNS 缓存 30 秒）
- HTTP client 用连接池（OkHttp / Apache HttpClient）
- 预拉镜像减少启动时间

## 实战排查命令速查

```bash
# === K8s ===
kubectl describe pod <pod> | grep -A 5 -i oom        # OOMKilled 事件
kubectl logs <pod> -c <container> --previous         # 上次容器日志
kubectl top pod <pod>                                 # 实时内存/CPU

# === 容器内 ===
jcmd 1 VM.flags                                       # JVM 启动参数（PID 1）
jcmd 1 GC.heap_info                                   # 堆信息
jcmd 1 Thread.print                                   # 线程栈
jcmd 1 JFR.start duration=30s filename=/tmp/r.jfr     # JFR 采集

# === cgroup ===
cat /sys/fs/cgroup/memory.max                         # 内存限制（cgroup v2）
cat /sys/fs/cgroup/memory/memory.limit_in_bytes       # cgroup v1
cat /sys/fs/cgroup/memory.peak                        # 实际峰值（cgroup v2）

# === 宿主机（需权限）===
dmesg | grep -i "killed process"                      # 内核 OOM killer 日志
```

## 参考入口

- **SKILL.md**：本 skill 主入口，按症状跳转
- **诊断命令详解**：`diagnostic_commands.md`（jcmd/jstack/jmap 输出字段）
- **GC 排查指南**：`gc_tuning_guide.md`（事后诊断视角）
- **JFR 事后分析**：`jfr_analysis.md`（容器内 JFR 落盘 + 分析）
- 官方容器支持文档：`https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html#containers`
- K8s Java 调优：`https://kubernetes.io/docs/concepts/containers/`
- jattach 工具：`https://github.com/jvm-profiling-tools/jattach`
