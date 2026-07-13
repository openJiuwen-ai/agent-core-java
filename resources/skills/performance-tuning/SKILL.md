---
name: performance-tuning
description: Java 性能调优指南（JDK 17 baseline）。在用户做性能优化、写高性能代码、选 JVM 参数、调 GC 收集器、做 JMH 基准测试、用 Profiling 工具、优化集合/Stream/锁/反射、调 JIT/内联、优化 AI agent 框架（LLM 调用、JSON 序列化、反射动态调用、prompt 拼接）时主动应用。覆盖：JVM 参数调优（堆/栈/GC 收集器选型）、代码级性能（集合选型、Stream 性能、反射优化、逃逸分析、锁优化、Records、sealed class、Vector API）、编译优化（JIT、内联、分层编译、CDS/GraalVM）、性能测试（JMH 基准测试、JFR、async-profiler、Arthas）、AI agent 框架特定优化（OkHttp 连接池、Jackson 流式解析、MethodHandle 工具调用、prompt StringBuilder、CompletableFuture 编排）。涉及关键词：性能调优、performance tuning、JMH、基准测试、benchmark、JIT、内联、inlining、Profiling、逃逸分析、栈上分配、锁优化、锁粗化、锁消除、TLAB、Stream 性能、集合选型、反射优化、Records、sealed、Vector API、--add-opens、JFR、火焰图、ZGC、G1、GraalVM、LLM 调用、OkHttp、连接池、Jackson、JSON 序列化、prompt 拼接、MethodHandle、LambdaMetafactory、agent 框架。与 jvm-troubleshoot 互补——jvm-troubleshoot 是"出问题了怎么查"（事后排查），这个是"怎么写才快"（事前优化）。不适用于：编码规范问题（用 coding-standard）、JVM 线上故障排查（用 jvm-troubleshoot）、重构流程治理（用 refactor-guide）。
---

# Java 性能调优指南（JDK 17 baseline）

本 skill 以 **JDK 17 为基线**，按"优化层次 → 决策表 → 优化要点"的结构组织，覆盖 4 个层次的性能优化。所有优化都应**先测后调**，不靠经验猜。

**与 jvm-troubleshoot 的边界**：
- jvm-troubleshoot：线上已出问题（OOM/CPU 100%/死锁）→ 按"症状 → 诊断 → 修复"排查
- 本 skill：代码/配置还没出问题，但想更快 → 按"层次 → 选型 → 优化"预防

## 优化层次速查

| 优化目标 | 跳转 | 详细文档 |
|---|---|---|
| 选 JVM 参数、调 GC 收集器 | [JVM 参数调优](#jvm-参数调优) | `references/jvm_params.md`、`references/gc_tuning.md` |
| 写代码时选集合/优化 Stream/优化锁/优化反射 | [代码级性能](#代码级性能) | `references/code_level_optimization.md` |
| 理解 JIT 内联、分层编译、预热 | [编译优化](#编译优化) | `references/jit_compiler.md` |
| 做基准测试、用 Profiling 定位瓶颈 | [性能测试与 Profiling](#性能测试与-profiling) | `references/jmh_profiling.md` |
| 参照完整调优流程案例 | [实战案例](#实战案例) | `references/tuning_cases.md` |
| AI agent 框架特定优化（LLM 调用 / JSON / 反射 / prompt） | [AI agent 框架优化](#ai-agent-框架优化) | `references/ai_agent_optimization.md` |
| 不确定哪层是瓶颈 | 先看 [性能优化决策流程](#性能优化决策流程) | - |

## 性能优化决策流程

**永远按这个顺序，别跳步**：

1. **先测基线**：用 JMH 或 Profiling 拿到当前性能数（吞吐量/延迟/P99），无数据不优化
2. **定位瓶颈层**：用 Profiling（async-profiler / JFR）看 CPU/内存/锁/IO 哪个是热点
3. **从上层开始优化**：业务算法 > 数据结构 > 代码写法 > JVM 参数 > 硬件
   - 改算法 O(n²) → O(n log n) 收益 >> 调 JVM 参数
4. **每次只改一个变量**：改完重测，对比前后数据
5. **不达目标不收工**：性能优化是迭代过程，不是一次到位

**反模式**：
- ❌ 没测就调 `-Xmx` 和 GC 参数 → 多数情况无收益
- ❌ 改完代码不跑 JMH 就上线 → 不知道是变快还是变慢
- ❌ 用 Stream/并行流就以为一定快 → 很多场景比 for 慢
- ❌ JDK 17 不开 `--add-opens` 强行反射 → `InaccessibleObjectException`

## JVM 参数调优

### 堆大小决策

| 场景 | -Xms / -Xmx | 理由 |
|---|---|---|
| 微服务 / 容器 | `-Xms = -Xmx`，按容器内存 50-75% | 避免堆动态扩张停顿 |
| 批处理 | `-Xms < -Xmx` | 渐进式增长，省内存 |
| 高吞吐低延迟 | `-Xms = -Xmx`，堆不要太大 | 大堆 GC 时间长 |
| 大缓存型应用 | 大堆 + ZGC | 减少 Full GC，ZGC 停顿不随堆增长 |

**经验法则**：
- 堆大小不是越大越好。32GB+ 会关闭指针压缩（`-XX:+UseCompressedOops`），对象引用变 8 字节，反而更耗内存
- 容器内：`-XX:+UseContainerSupport`（JDK 17 默认开），让 JVM 识别容器内存限制
- 用百分比更省心：`-XX:InitialRAMPercentage=50.0 -XX:MaxRAMPercentage=75.0`

### 栈大小调优

| 场景 | -Xss | 理由 |
|---|---|---|
| 默认 | 1M（JDK 17 Linux x64） | 多数应用足够 |
| 深递归 | 2M-4M | 防止 StackOverflowError |
| 线程数多 | 256K | 省线程内存（每线程一份栈） |

### GC 收集器选型

JDK 17 stable 的收集器：

| 场景 | 堆大小 | 停顿要求 | 推荐收集器 | 关键参数 |
|---|---|---|---|---|
| 微服务 / 默认 | < 4GB | 200ms 内 | G1（JDK 17 默认） | `-XX:MaxGCPauseMillis=200` |
| 中型应用 | 4-8GB | 200ms 内 | G1 | `-XX:MaxGCPauseMillis=200 -XX:G1HeapRegionSize=8m` |
| 大型低延迟 | 8-32GB | 50ms 内 | ZGC | `-XX:+UseZGC -XX:ZAllocationSpikeTolerance=2` |
| 超大堆 | 32GB-16TB | 亚毫秒 | ZGC | 同上，必要时 `-XX:ConcGCThreads` 调高 |
| 高吞吐批处理 | 任意 | 无停顿要求 | Parallel | `-XX:+UseParallelGC -XX:ParallelGCThreads=N` |
| 低停顿 + 并发标记 | 中型 | 100ms 内 | Shenandoah（OpenJDK） | `-XX:+UseShenandoahGC` |

**JDK 17 ZGC 关键点**：非分代版（generational ZGC JDK 21+ 才 stable）。堆 > 32GB + 对吞吐敏感 → 升级 JDK 21。

**选型决策**：
1. JDK 17 + 堆 ≥ 8GB + 低延迟 → 优先 ZGC
2. JDK 17 + 堆 < 8GB → G1（默认）
3. 吞吐量第一（离线计算） → Parallel
4. 堆 > 32GB + 对吞吐敏感 → 升级 JDK 21 分代 ZGC

**不要做**：
- ❌ 堆 < 4GB 用 ZGC → 没收益
- ❌ 没测就换收集器 → 可能更慢
- ❌ 调 `-XX:MaxGCPauseMillis=1` → 多数情况达不到，反而频繁 GC

更多 GC 调优细节看 `references/gc_tuning.md`。

### 关键 JVM 参数速查

**监控（JDK 17 生产环境常开）**：
```
-Xlog:gc*:file=gc.log:time,uptime:filecount=10,filesize=10M   # GC 日志
-XX:+HeapDumpOnOutOfMemoryError                                # OOM 自动 dump
-XX:HeapDumpPath=/var/log/dumps/                              # dump 路径
-XX:ErrorFile=/var/log/hs_err_%p.log                           # 致命错误日志
-XX:StartFlightRecording=filename=/var/log/jfr/app.jfr,maxage=1h,maxsize=100M,settings=profile  # JFR 常开
```

**调优常用**：
```
-Xms4g -Xmx4g                              # 初始/最大堆，相等避免扩张
-XX:MetaspaceSize=256m                     # Metaspace 初始
-XX:MaxMetaspaceSize=512m                  # Metaspace 上限
-XX:+DisableExplicitGC                     # 禁用 System.gc()
-XX:+AlwaysPreTouch                        # 启动时预触所有页
-XX:ParallelGCThreads=N                    # GC 线程数，默认 CPU 核 5/8
-XX:ConcGCThreads=N                        # 并发 GC 线程数，默认 ParallelGCThreads 1/4
```

**JIT 相关**：
```
-XX:+TieredCompilation                     # 分层编译（JDK 17 默认开）
-XX:ReservedCodeCacheSize=256m             # JIT 代码缓存，太小触发反优化
```

**模块系统（JDK 17 必踩）**：
```
--add-opens java.base/java.lang=ALL-UNNAMED            # 反射访问 String 内部
--add-opens java.base/java.util=ALL-UNNAMED            # 反射访问集合内部
--add-opens java.base/java.nio=ALL-UNNAMED             # Netty 反射访问 NIO
```

完整 JVM 参数清单看 `references/jvm_params.md`。

## 代码级性能

### 集合选型

按访问模式选集合，别默认 `ArrayList`/`HashMap`：

| 场景 | 选 | 不选 |
|---|---|---|
| 随机读多，写少 | `ArrayList` / `CopyOnWriteArrayList`（读多写极少） | `LinkedList` |
| 频繁头部插删 | `ArrayDeque` / `LinkedList` | `ArrayList`（头部 O(n)） |
| 频繁尾部插 | `ArrayList`（均摊 O(1)） | `LinkedList`（节点开销大） |
| 高并发读 + 偶尔写 | `ConcurrentHashMap` | `HashMap` + `synchronized` |
| 高并发读 + 极少写 | `CopyOnWriteArrayList` | `Vector` |
| 键是 enum | `EnumMap`（数组实现，最快） | `HashMap` |
| 枚举集合 | `EnumSet`（位向量） | `HashSet` |
| LRU 缓存 | `LinkedHashMap` + `removeEldestEntry` | `HashMap` + 手写 |

**HashMap 已知大小调容量**：`new HashMap<>(expectedSize / 0.75 + 1)` 避免扩容。

更多集合优化细节看 `references/code_level_optimization.md`。

### Stream 性能

Stream **不是性能优化手段**，是可读性工具。用错反而慢：

| 场景 | Stream | for 循环 |
|---|---|---|
| 简单遍历 | 慢（装箱/迭代器开销） | 快 |
| 复杂链式 filter/map/reduce | 相当或略慢 | 难写但快 |
| 大数据并行 | `parallelStream` 可能快 | 单线程 for |
| 小集合（< 1000） | 慢 | 快 |

**Stream 反模式**：
- ❌ `stream().collect(toList()).stream()` —— 多余装箱
- ❌ `list.parallelStream()` 在小集合上 —— ForkJoin 拆分开销 > 收益
- ❌ `stream.forEach(x -> sb.append(x))` —— 用 `collect(joining(","))`

### 反射优化

反射慢但有用。优化路径：

| 优化层级 | 做法 | 收益 |
|---|---|---|
| 0：缓存 Method/Field | `static final Method M = ...` | 10x+ |
| 1：setAccessible(true) | 关闭访问检查 | 2-5x |
| 2：MethodHandle | `MethodHandles.lookup()` | 接近直接调用 |
| 3：LambdaMetafactory | 把 MethodHandle 转成 Lambda | 接近直接调用 |
| 4：字节码生成 | ByteBuddy/CGLIB | 最快，但有生成成本 |

**JDK 17 模块系统**：反射访问非 export 包需 `--add-opens`，否则 `InaccessibleObjectException`。

### 锁优化

锁不是越细越好。锁优化 4 个层次：

| 层次 | 技术 | 适用 |
|---|---|---|
| **锁消除** | `-XX:+DoEscapeAnalysis`（JDK 17 默认开） | 局部对象无竞争，JIT 自动消除 |
| **锁粗化** | JIT 把相邻 synchronized 合并 | 循环内 `synchronized` 合并到循环外 |
| **锁粒度** | `ConcurrentHashMap` 分段 → 红黑树 | 高并发写 |
| **无锁** | `Atomic*` / `LongAdder` / `VarHandle` | 计数器/统计场景 |

**锁选型决策**：
| 场景 | 选 | 不选 |
|---|---|---|
| 低竞争 | `synchronized` | `ReentrantLock`（开销大） |
| 需要超时/中断 | `ReentrantLock.tryLock(timeout)` | `synchronized`（不支持） |
| 读多写少 | `StampedLock`（乐观读） | `ReentrantReadWriteLock` |
| 公平排队 | `ReentrantLock(fair=true)` | `synchronized`（非公平） |
| 计数器 | `LongAdder` | `AtomicLong`（高并发慢） |

### JDK 17 新特性

| 特性 | 性能收益 | 用法 |
|---|---|---|
| **Records**（JDK 14+ stable） | 编译器生成 equals/hashCode/toString，无反射 | `public record Point(int x, int y) {}` |
| **Sealed class**（JDK 17 stable） | 编译时穷尽匹配，帮助 JIT 去虚化 | `sealed interface Shape permits Circle, Rectangle, Triangle {}` |
| **Vector API**（incubator） | 明确 SIMD 化，比 C2 自动向量化稳定 | `--add-modules jdk.incubator.vector` |
| **Foreign Memory API**（incubator） | 堆外内存访问，替代 Unsafe | `--add-modules jdk.incubator.foreign` |
| **Compact Strings**（JDK 9+ 默认） | ASCII 字符串内存减半 | 默认开，无需配置 |
| **CDS / AppCDS** | 启动加速 30-50% | `java -Xshare:dump` |
| **GraalVM Native Image** | AOT 编译，启动毫秒级 | CLI / 函数计算首选 |

**虚拟线程**：JDK 21+ 才 stable，JDK 17 不可用。JDK 17 替代方案用 `CompletableFuture` / `Reactor`。

更多代码级优化细节看 `references/code_level_optimization.md`。

## 编译优化

### JIT 分层编译

JDK 17 默认开分层编译，5 层：

| 层 | 解释器 | C1 | C2 | 说明 |
|---|---|---|---|---|
| 0 | ✓ | | | 解释执行，采集调用计数 |
| 1 | | ✓ | | C1 编译，无 profiling |
| 2 | | ✓ | | C1 + profiling |
| 3 | | ✓ | | C1 + 完整 profiling（多数方法停在这） |
| 4 | | | ✓ | C2 编译，激进优化（内联/逃逸分析/循环展开） |

**预热**：
- 方法调用计数到阈值（默认 10000）触发 C1 → C2 编译
- 长跑应用（生产服务）预热后稳定，短跑应用（CLI/批处理）可能全程解释执行
- 短跑应用建议 GraalVM Native Image 或 CDS

**代码缓存**：`-XX:ReservedCodeCacheSize=256m`（JDK 17 默认 240M），太小触发反优化。

更多 JIT 细节看 `references/jit_compiler.md`。

### 内联优化

内联是 JIT 最重要的优化。**帮助内联**：
- 热点方法别写太大（< 35 字节码，`-XX:MaxInlineSize`）
- 加 `final` 类/方法帮助 JIT 判定非虚调用 → 内联
- 用 sealed class（JDK 17）让 JIT 知道所有实现 → 单态化内联
- 别在热路径用复杂继承层级（虚方法难内联）

**反模式**：
- ❌ 热路径写复杂继承层级 → 虚方法多态，不内联
- ❌ 热路径用反射 → JIT 难优化
- ❌ 在热路径 lambda 捕获变量 → 每次创建 Lambda 对象

## 性能测试与 Profiling

### JMH 基准测试

JMH 是 OpenJDK 出的 Java 基准测试框架，**唯一可信**的 Java 微基准测试工具。

**何时用 JMH**：验证某个写法是否更快、测方法吞吐量/延迟、对比优化前后。

**何时不用 JMH**：测整个应用性能（用 Profiling + 集成测试）、测 IO 密集场景（JMH 测不准）、测一次性初始化（不是 JMH 设计目标）。

**最小 JMH 用法**：
```java
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
@State(Scope.Thread)
public class CollectionBenchmark {

    @Param({"100", "1000", "10000"})
    int size;

    List<Integer> list;

    @Setup
    public void setup() {
        list = IntStream.range(0, size).boxed().collect(toList());
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
}
```

**JMH 陷阱**：必 `@Fork(2)`、必 `@Warmup` 充分、benchmark 方法里别 `new`、避免死代码消除（用 `Blackhole`）。

**JDK 17 JMH 注意**：
- 反射访问非 export 包需 `--add-opens`：`@Fork(jvmArgsAppend = {"--add-opens", "java.base/java.lang=ALL-UNNAMED"})`
- Vector API benchmark 需 `--add-modules jdk.incubator.vector`

更多 JMH 细节看 `references/jmh_profiling.md`。

### Profiling 工具

按粒度选工具：

| 工具 | 粒度 | 场景 | 开销 |
|---|---|---|---|
| **JFR**（JDK Flight Recorder） | 系统级 + 方法级 | 生产长跑，看 CPU/内存/IO/锁 | 极低（< 1%） |
| **async-profiler** | 方法级 CPU/Alloc/Lock | 持续采样，火焰图 | 低（< 1%） |
| **JProfiler / YourKit** | 全功能 GUI | 本地深度调优 | 中 |
| **VisualVM** | 概览 | 本地快速看 | 中 |
| **JMH** | 方法级精确 | 微基准 | 高（专门跑） |
| **Arthas** | 在线诊断 | 生产动态 trace | 中 |

**JDK 17 JFR 优势**：Streaming API（JDK 14+）可实时流式消费事件，无需落盘。`settings=profile` 事件更全。生产常开：`-XX:StartFlightRecording=...`。

**async-profiler 火焰图**：
```bash
./profiler.sh -d 30 -f flame.html <pid>            # CPU
./profiler.sh -d 30 -e alloc -f alloc.html <pid>    # 分配
./profiler.sh -d 30 -e lock -f lock.html <pid>      # 锁
```

### 性能指标体系

| 指标 | 含义 | 优化目标 |
|---|---|---|
| **吞吐量**（QPS/TPS） | 每秒处理数 | 越高越好 |
| **平均延迟** | 请求平均耗时 | 越低越好（但被长尾拉偏） |
| **P99 延迟** | 99% 请求的耗时 | 越低越好（更真实反映体验） |
| **P999 延迟** | 99.9% 请求耗时 | 长尾敏感场景看这个 |
| **GC 停顿时间** | STW 时间 | 越短越好 |
| **GC 吞吐量** | GC 时间占比 | < 5% |

**指标陷阱**：只看平均延迟不看 P99 → 长尾被掩盖；只看吞吐量不看延迟 → 高 QPS 可能靠堆积请求；单次基准就下结论 → 用统计显著的多次测量。

## 实战案例

参照完整调优流程的 6 个端到端案例：

| 案例 | 优化点 | 收益 |
|---|---|---|
| Stream 改 for 循环 | 简单遍历不用 Stream | 3.7-4.6x 快 |
| HashMap 调容量 + LongAdder | 高并发计数 + map 调容量 | 5x+ 并发吞吐 |
| 反射改 MethodHandle | 反射必缓存 + MethodHandle | 27x 快 |
| 容器内微服务调优 | 容器堆比例 + JFR + 预热 | P99 300ms → 85ms |
| 逃逸分析失效场景 | 简单字符串拼接编译器更优 | 分配热点消失 |
| ZGC 切换（大堆） | G1 → ZGC | 停顿 300ms → < 5ms |

详细案例看 `references/tuning_cases.md`。

## AI agent 框架优化

agent-core-java 是 AI agent 框架，特定性能瓶颈不同于通用 Java 应用。

**LLM 调用占 90%+ 耗时**，代码层优化收益有限，但别让代码层拖后腿。

### 优化层次

| 层 | 优化点 | 收益 |
|---|---|---|
| LLM HTTP 调用 | OkHttp 连接池调优、流式响应、超时配置、429 重试 | 省 100-300ms/请求 |
| JSON 序列化 | ObjectMapper 单例、流式解析大 payload、关闭未用特性 | 30-50% JSON 提速 |
| 反射 / 动态调用 | 工具 Method 缓存、改 MethodHandle、LambdaMetafactory | 反射 5-27x 快 |
| prompt 拼接 | StringBuilder 预估容量、别循环内 `+` | 避免无谓分配 |
| 异步编排 | 独立步骤并行化、LLM 用独立线程池 | 并行 2-3x 提速 |
| 缓存 | LLM 响应缓存（temperature=0）、Embedding 缓存 | 命中即省 LLM 调用 |

### agent 框架性能瓶颈分布

```
用户输入 → prompt 拼接 → LLM HTTP 调用（最慢）→ JSON 解析 → 工具调用（反射）→ 响应拼接
```

LLM HTTP 调用占 90%+ 耗时。代码层优化虽收益有限，但每个微秒级延迟在高 QPS 下累积成毫秒。

详细优化（含 OkHttp / Jackson / 反射 / prompt / 异步编排 / 缓存策略 + 实战清单）看 `references/ai_agent_optimization.md`。

## 参考入口

- **JVM 参数详解**：`references/jvm_params.md`（JDK 17 默认参数表、堆/栈/Metaspace/CodeCache/JIT 各参数取值）
- **GC 调优**：`references/gc_tuning.md`（事前配置，与 jvm-troubleshoot 互补）
- **代码级优化**：`references/code_level_optimization.md`（集合/Stream/反射/锁/逃逸分析 + JDK 17 新特性）
- **JIT 与编译优化**：`references/jit_compiler.md`（分层编译、内联、Vector API、GraalVM）
- **JMH 与 Profiling**：`references/jmh_profiling.md`（JMH API、陷阱、JFR、火焰图读法）
- **实战案例**：`references/tuning_cases.md`（6 个端到端优化案例）
- **AI agent 框架优化**：`references/ai_agent_optimization.md`（LLM 调用、JSON 序列化、反射、prompt、异步、缓存）
- 项目内故障排查：`../jvm-troubleshoot/SKILL.md`（出问题后的事后排查）
- 项目内编码规范：`../coding-standard/SKILL.md`
- 官方文档：`https://docs.oracle.com/en/java/javase/17/docs/specs/man/`
- JMH 官方：`https://openjdk.org/projects/code-tools/jmh/`
- async-profiler：`https://github.com/async-profiler/async-profiler`
- JFR / JDK Mission Control：`https://github.com/openjdk/jmc`

## 使用方式

1. **先看决策流程**：性能优化按"测 → 定位 → 优化 → 重测"流程，别跳步
2. **按层次查**：JVM 参数 → 代码级 → 编译 → 测试，看"优化层次速查"表跳转
3. **细节按需 Read**：每个小节指向对应的 references 文件
4. **不替代 jvm-troubleshoot**：线上已出问题（OOM/CPU 100%）用故障排查 skill
5. **不靠经验猜**：所有优化都要用 JMH 或 Profiling 验证
6. **不确定不要编造**：JVM/JIT 行为以源码和官方文档为准，本 skill 不替代正式文档
