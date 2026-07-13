# JVM 参数详解（JDK 17 baseline）

本文件补充 SKILL.md 的 JVM 参数调优内容，**以 JDK 17 为基线**。讲清楚堆/栈/Metaspace/CodeCache/JIT 各参数的取值范围、默认值与场景。用户问"这个参数设多少"或"这个参数是干嘛的"时按需读取。

## JDK 17 默认参数表

JDK 17 关键默认值（多数场景不用动）：

| 参数 | JDK 17 默认 | 备注 |
|---|---|---|
| `-XX:+UseG1GC` | ✓ | 默认收集器 |
| `-XX:+TieredCompilation` | ✓ | 分层编译 |
| `-XX:+DoEscapeAnalysis` | ✓ | 逃逸分析 |
| `-XX:+UseContainerSupport` | ✓ | 容器内自动识别 cgroup |
| `-XX:MaxGCPauseMillis` | 200 | G1 目标停顿 |
| `-XX:ReservedCodeCacheSize` | 240M | JIT 代码缓存 |
| `-XX:MetaspaceSize` | 20M（Linux） | 触发 Full GC 阈值 |
| `-XX:MaxMetaspaceSize` | 无上限 | 受物理内存 |
| `-XX:CompileThreshold` | 10000 | C2 编译阈值（分层编译下自适应） |
| `-XX:MaxInlineSize` | 35 | 常规方法内联上限 |
| `-XX:FreqInlineSize` | 325 | 热点方法内联上限 |
| `-XX:SurvivorRatio` | 8 | Eden:Survivor |
| `-XX:TargetSurvivorRatio` | 50 | Survivor 占比目标 |
| `-XX:MaxTenuringThreshold` | 15 | 晋升老年代年龄 |

**看本进程实际值**：
```bash
jcmd <pid> VM.flags
# 或启动时打印
java -XX:+PrintFlagsFinal -version | grep <param>
```

## 参数分类

JVM 参数分 3 类：
- **标准参数**（`-`）：所有 JVM 实现都支持，如 `-version`、`-jar`
- **非标准参数**（`-X`）：HotSpot 默认实现，如 `-Xmx`、`-Xms`
- **不稳定参数**（`-XX`）：HotSpot 专属，可能版本变更，如 `-XX:+UseG1GC`

**布尔型 -XX**：`-XX:+OptionName` 开启，`-XX:-OptionName` 关闭。
**数值型 -XX**：`-XX:OptionName=value`，值可带单位（k/m/g）。

## 堆相关参数

### -Xms / -Xmx

| 参数 | 含义 | JDK 17 默认 |
|---|---|---|
| `-Xms` | 初始堆 | 容器：cgroup 内存 / 4；物理机：物理内存 / 64 |
| `-Xmx` | 最大堆 | 容器：cgroup 内存 / 4；物理机：物理内存 / 4 |

**取值建议**：
- 生产服务：`-Xms = -Xmx`，避免堆动态扩张触发 Full GC
- 容器：`-XX:+UseContainerSupport`（JDK 17 默认开），自动识别 cgroup 内存限制
- 用百分比更省心：`-XX:InitialRAMPercentage=50.0 -XX:MaxRAMPercentage=75.0`
- 堆上限：**≤ 32GB**，超过关闭指针压缩（`-XX:+UseCompressedOops`），对象引用变 8 字节，反而更耗内存
- 容器内：按容器内存 50-75% 留给堆，剩下给 Metaspace/线程栈/直接内存/JVM 自身

### -Xmn / -XX:NewRatio / -XX:SurvivorRatio

| 参数 | 含义 | 默认 |
|---|---|---|
| `-Xmn` | 新生代大小 | 平台依赖 |
| `-XX:NewRatio=N` | Old:Young = N:1（NewRatio=2 即新生代 = 1/3 堆） | 2 |
| `-XX:SurvivorRatio=N` | Eden:Survivor = N:1 | 8 |
| `-XX:MaxTenuringThreshold=N` | 对象晋升老年代年龄 | 15 |

**用 G1 时**（JDK 17 默认）：
- 别手动设 `-Xmn` / `-XX:NewRatio` —— G1 自适应调整
- 可以设 `-XX:G1HeapRegionSize=8m`，按堆大小自动选（1MB-32MB）

**Parallel/SerialGC 时**：
- `-XX:NewRatio=2`：新生代 1/3，老年代 2/3
- `-XX:SurvivorRatio=8`：Eden 80%，Survivor 各 10%
- 新生代大 → Young GC 少但单次久；新生代小 → Young GC 频繁但单次快

### -XX:PretenureSizeThreshold

大对象超过此值直接进老年代，避免在新生代来回复制。

- 单位：字节
- 默认：0（不限制，由 JVM 判定）
- 用 G1 时：大对象（> region 一半）走 humongous region，不用调此参数
- 调大可减少大对象在新生代复制，但太大会让老年代快速占满

**场景**：缓存大对象、大数组、大字符串应进老年代。

## Metaspace 参数

| 参数 | 含义 | JDK 17 默认 |
|---|---|---|
| `-XX:MetaspaceSize` | 触发 Full GC 的阈值 | 20M（Linux） |
| `-XX:MaxMetaspaceSize` | Metaspace 上限 | 无上限（受物理内存） |

**建议**：
- 生产固定：`-XX:MetaspaceSize=256m -XX:MaxMetaspaceSize=512m`，避免运行时扩张触发 Full GC
- 动态生成类多（CGLIB/ByteBuddy）：调高 MaxMetaspaceSize
- MetaspaceSize 不是初始分配，是触发 GC 的阈值 —— 第一次 Full GC 时间点

**OOM Metaspace 排查**：看 `jmap -clstats <pid>` ClassLoader 数量。详见 jvm-troubleshoot SKILL.md。

## 栈参数

### -Xss

每线程调用栈大小。

| 平台 | JDK 17 默认 |
|---|---|
| Linux x64 | 1M |
| Windows x64 | 1M |
| macOS x64 | 1M |
| Linux ARM64 | 2M |

**调优场景**：
- 深递归：调大到 2M-4M，防 StackOverflowError
- 线程数极多（千级）：调小到 256K，省内存
- 默认不动

**计算**：1000 线程 × 1M = 1GB 栈内存，线程数多时栈内存可观。

## 代码缓存

| 参数 | 含义 | JDK 17 默认 |
|---|---|---|
| `-XX:ReservedCodeCacheSize` | JIT 代码缓存上限 | 240M |
| `-XX:InitialCodeCacheSize` | 初始代码缓存 | 160K |
| `-XX:CodeCacheExpansionSize` | 扩张步长 | 64K |

**注意**：
- 太小 → JIT 编译的方法被丢弃回解释执行（反优化），性能骤降
- JDK 17 默认 240M 多数够用，大型应用（10万+ 类）可调到 512M
- 看：`jcmd <pid> Compiler.CodeCache`

## JIT 参数

### -XX:+TieredCompilation

分层编译开关，JDK 17 默认开。

**关闭影响**：
- 关闭后只用 C2 编译，方法编译阈值升高（10x+），预热慢
- 短跑应用可能受益（不付 profiling 开销）
- 长跑应用**不要关闭**

### -XX:CompileThreshold

方法调用次数到阈值触发编译。分层编译模式下自适应，不再固定。

| 模式 | 阈值 | 说明 |
|---|---|---|
| C1 | ~1500（自适应） | 触发 C1 编译 |
| C2 | ~10000（自适应） | 触发 C2 编译 |

### -XX:+PrintCompilation

打印 JIT 编译日志，调试 JIT 行为用。

```bash
jcmd <pid> Compiler.directives_print
# 或启动时加
-XX:+PrintCompilation -XX:+UnlockDiagnosticVMOptions -XX:+PrintInlining
```

更多 JIT 细节看 `jit_compiler.md`。

## GC 参数

**JDK 17 默认 G1**。常用：
```
-XX:+UseG1GC                          # 默认，写出来更清晰
-XX:+UseZGC                           # JDK 15+ production-ready
-XX:+UseShenandoahGC                  # JDK 15+ stable（OpenJDK 构建）
-XX:+UseParallelGC                    # 吞吐量优先
-XX:MaxGCPauseMillis=200              # 目标停顿时间（G1/ZGC）
-XX:G1HeapRegionSize=8m               # G1 region 大小
-XX:ParallelGCThreads=N               # GC 线程数
-XX:ConcGCThreads=N                   # 并发 GC 线程数
-XX:InitiatingHeapOccupancyPercent=45 # G1 触发并发标记的堆占用率
-XX:G1NewSizePercent=5                 # G1 新生代下限
-XX:G1MaxNewSizePercent=60             # G1 新生代上限
-XX:+DisableExplicitGC                # 禁用 System.gc()
-XX:+ExplicitGCInvokesConcurrent      # System.gc() 走并发（G1）
-XX:+AlwaysPreTouch                    # 启动时预触所有堆页
```

**ZGC 关键参数**（JDK 17 是**非分代版**，分代 ZGC JDK 21+ 才稳定）：
```
-XX:ZAllocationSpikeTolerance=2       # 分配尖峰容忍度
-XX:ConcGCThreads=N                   # 并发 GC 线程数（默认 ParallelGCThreads 1/4）
```

**Shenandoah 关键参数**：
```
-XX:ShenandoahGCHeuristics=adaptive   # 自适应（默认）
-XX:ShenandoahGCHeuristics=aggressive # 激进（低延迟）
-XX:ShenandoahGCHeuristics=compact     # 紧凑（省内存）
```

更多 GC 调优细节看 `gc_tuning.md`。

## 监控参数

**生产环境常开**（JDK 17 统一日志语法）：
```
-Xlog:gc*:file=gc.log:time,uptime:filecount=10,filesize=10M   # GC 日志
-XX:+HeapDumpOnOutOfMemoryError                                # OOM 自动 dump
-XX:HeapDumpPath=/var/log/dumps/                              # dump 路径
-XX:ErrorFile=/var/log/hs_err_%p.log                           # 致命错误日志
```

**JDK 17 JFR 推荐常开**（开销 < 1%）：
```
-XX:StartFlightRecording=filename=/var/log/jfr/app.jfr,maxage=1h,maxsize=100M,settings=profile
-XX:FlightRecorderOptions=stackdepth=64
```

## 调试参数

**解锁后才可用**：
```
-XX:+UnlockDiagnosticVMOptions     # 解锁诊断参数
-XX:+PrintFlagsFinal               # 打印所有 JVM 参数最终值
-XX:+PrintFlagsInitial            # 打印默认值
-XX:+PrintCommandLineFlags         # 打印启动时设置的参数
-XX:+PrintInlining                 # 打印内联决策
-XX:+PrintCompilation              # 打印 JIT 编译日志
-XX:+LogCompilation                # 编译详细日志（需要 PrintCompilation）
```

**看 JVM 参数**：
```bash
# 进程启动后看
jcmd <pid> VM.flags

# 所有参数最终值
java -XX:+PrintFlagsFinal -version | grep -i heapsize
```

## 模块系统参数（JDK 17 必踩）

JDK 9+ 模块系统，反射访问非 export 包需显式打开：

```
--add-opens java.base/java.lang=ALL-UNNAMED            # 反射访问 String 内部
--add-opens java.base/java.util=ALL-UNNAMED            # 反射访问集合内部
--add-opens java.base/java.nio=ALL-UNNAMED             # 反射访问 NIO
--add-exports java.base/sun.nio.ch=ALL-UNNAMED         # 直接访问（非反射）
```

**典型场景**：
- 用 Spring / Hibernate / ByteBuddy 反射 → 需 `--add-opens`
- 用 Netty 访问 NIO 内部 → 需 `--add-opens java.base/java.nio`
- 用 Reflect.setField 访问 private → 需 `--add-opens`

**报错关键词**：`InaccessibleObjectException` → 缺 `--add-opens`。

## 实战参数组合

### 微服务（JDK 17，容器内）

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

### 高吞吐批处理（JDK 17，物理机）

```
-Xms16g -Xmx16g
-XX:+UseParallelGC
-XX:ParallelGCThreads=16
-XX:+AggressiveHeap
-XX:+UseParallelOldGC
-XX:ReservedCodeCacheSize=512m
-XX:+TieredCompilation
```

### 低延迟大型应用（JDK 17，物理机或大容器）

```
-Xms32g -Xmx32g
-XX:+UseZGC                          # JDK 17 非分代 ZGC，停顿亚毫秒
-XX:ZAllocationSpikeTolerance=2
-XX:ConcGCThreads=8
-XX:MetaspaceSize=512m
-XX:MaxMetaspaceSize=1g
-XX:ReservedCodeCacheSize=512m
-XX:+HeapDumpOnOutOfMemoryError
-Xlog:gc*:file=/var/log/gc.log:time,uptime:filecount=20,filesize=50M
```

注意：JDK 17 ZGC 非分代，大堆下吞吐量比分代版（JDK 21+）略低。若堆 > 32GB 且对吞吐敏感，可考虑升级 JDK 21 用分代 ZGC。

### CLI / 短跑应用（JDK 17）

```
-XX:+TieredCompilation
-XX:ReservedCodeCacheSize=128m
# 或用 GraalVM Native Image 提前编译（JDK 17 production-ready）
```

**注意**：CLI 应用预热不够，JIT 可能全程没生效。考虑：
- CDS（Class Data Sharing）：`-Xshare:on`
- GraalVM Native Image：AOT 编译
- AppCDS：自定义类的 CDS

## 参考文档

- Oracle JDK 17 JVM 参数：`https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html`
- OpenJDK 17 源码：`https://github.com/openjdk/jdk17`
- 项目内 GC 调优：`gc_tuning.md`
- 项目内 JIT 调优：`jit_compiler.md`
- 项目内事后排查：`../../jvm-troubleshoot/references/gc_tuning_guide.md`
