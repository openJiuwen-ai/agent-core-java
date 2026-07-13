# JMH 与 Profiling 工具

本文件补充 SKILL.md 的性能测试部分，深入讲 JMH API、陷阱、Profiling 工具对比与火焰图。用户问"怎么测性能"或"用什么工具定位瓶颈"时按需读取。

## JMH（Java Microbenchmark Harness）

OpenJDK 出的 Java 微基准测试框架，唯一可信的 Java 微基准测试工具。手写 `System.currentTimeMillis()` 测不准 —— JIT 优化、预热、死代码消除都会让结果失真。

### Maven 依赖

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

### JMH 注解

| 注解 | 作用 | 常用值 |
|---|---|---|
| `@Benchmark` | 标记基准方法 | - |
| `@BenchmarkMode` | 测什么 | `Mode.Throughput` / `Mode.AverageTime` / `Mode.SampleTime` |
| `@OutputTimeUnit` | 时间单位 | `TimeUnit.MILLISECONDS` / `TimeUnit.NANOSECONDS` |
| `@Warmup` | 预热 | `iterations=5, time=1` |
| `@Measurement` | 正式测 | `iterations=5, time=1` |
| `@Fork` | JVM 进程数 | `2`（必填，隔离 JIT） |
| `@State` | 状态作用域 | `Scope.Thread` / `Scope.Benchmark` / `Scope.Group` |
| `@Param` | 参数化 | `{"100", "1000", "10000"}` |
| `@Setup` | 初始化 | `Level.Trial` / `Level.Invocation` / `Level.Iteration` |
| `@TearDown` | 收尾 | 同上 |
| `@CompilerControl` | 控制 JIT | `CompilerControl.Mode.DONT_INLINE` |

### 基本示例

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

运行：`mvn clean install && java -jar target/benchmarks.jar`

### JMH API 进阶

#### Blackhole（避免死代码消除）

JIT 会消除"结果未使用"的代码。JMH 提供 `Blackhole` 消费结果：

```java
@Benchmark
public void consumeResult(Blackhole bh) {
    bh.consume(list.size());
    bh.consume(list.get(0));
}
```

#### @State 状态对象

状态对象在 benchmark 方法间共享：

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

#### @Param 参数化

```java
@Param({"100", "1000", "10000", "100000"})
public int size;

@Param({"true", "false"})
public boolean useParallel;
```

JMH 会跑所有组合。

#### @CompilerControl 控制内联

```java
@Benchmark
@CompilerControl(CompilerControl.Mode.DONT_INLINE)
public int noInline() { ... }
```

调试"内联前后性能差异"用。

### JMH 陷阱

#### 1. 不用 @Fork

```java
// 错：单进程跑，JIT 状态污染
@Fork(0)  // 或不写 @Fork
```

正确：`@Fork(2)` 至少。

#### 2. @Warmup 不够

```java
// 错：预热不够，JIT 没完全生效
@Warmup(iterations = 1, time = 1)
```

正确：`@Warmup(iterations = 5, time = 1)` 起步。

#### 3. benchmark 方法里 new 对象

```java
// 错：测的是分配
@Benchmark
public List<Integer> createList() {
    return new ArrayList<>();
}
```

应：`@Setup` 准备好，benchmark 只测核心逻辑。

#### 4. 死代码消除

```java
// 错：返回值未使用，JIT 可能消除整个方法
@Benchmark
public void compute() {
    int x = 1 + 2 + 3;
}
```

应：`return x` 或用 `Blackhole`。

#### 5. 循环在 benchmark 里

```java
// 错：JMH 会自己循环调用，你不用循环
@Benchmark
public void loop() {
    for (int i = 0; i < 1000; i++) { ... }
}
```

应：单次操作，JMH 按 `time` 调用。

#### 6. 不一致的预热

短预热测出 C1 编译版本，正式跑 C2 编译版本。要保证 `@Warmup` + `@Fork` 让每次 fork 都充分预热。

#### 7. JDK 17 模块系统反射

JMH benchmark 用反射加载 `@Benchmark` 方法。访问非 export 包内的字段需加 `--add-opens`。

```java
// JVM 参数
--add-opens java.base/java.lang=ALL-UNNAMED
--add-opens java.base/java.util=ALL-UNNAMED
```

JMH 启动时通过 `jvmArgsAppend` 传递：
```java
@Fork(jvmArgsAppend = {"--add-opens", "java.base/java.lang=ALL-UNNAMED"})
```

#### 8. JDK 17 Vector API benchmark

Vector API 是 incubator，benchmark 需加 `--add-modules jdk.incubator.vector`：

```java
@Fork(jvmArgsAppend = {"--add-modules", "jdk.incubator.vector"})
```

### JMH 输出解读

```
Benchmark                          (size)   Mode  Cnt     Score    Error  Units
ListBenchmark.forEachSum               100  thrpt    10  1234.567 ± 12.345  ops/ms
ListBenchmark.streamSum               100  thrpt    10   987.654 ± 10.123  ops/ms
```

- **Score**：吞吐量（ops/ms = 每毫秒操作数）或平均时间
- **Error**：99% 置信区间
- **Cnt**：测量迭代数

### JMH Profiler

JMH 内置 profiler：

```bash
# GC 情况
-prof gc

# 内存分配
-prof gc -gc true

# 栈采样
-prof stack

# Linux perf（系统级）
-prof perf

# 类加载
-prof cl
```

用法：`java -jar target/benchmarks.jar -prof gc`

## Profiling 工具对比

| 工具 | 粒度 | 开销 | 输出 | 场景 |
|---|---|---|---|---|
| **JFR**（JDK Flight Recorder） | 系统级 + 方法级 | 极低（< 1%） | 二进制 .jfr | 生产长跑，全局视图 |
| **async-profiler** | 方法级 CPU/Alloc/Lock | 低 | 火焰图 | 持续采样，热点定位 |
| **JProfiler / YourKit** | 全功能 GUI | 中 | GUI | 本地深度调优 |
| **VisualVM** | 概览 | 中 | GUI | 本地快速看 |
| **JMH** | 方法级精确 | 高 | 文本 | 微基准 |
| **Arthas** | 在线诊断 | 中 | CLI | 生产动态 trace |
| **BTrace** | 在线脚本 | 中 | 自定义 | 生产动态插桩 |

## JFR（JDK Flight Recorder）

JDK 11+ 开源，生产可用。开销 < 1%，是**生产环境首选**长期采样工具。

### 启动 JFR 采集

```bash
# 启动 30 秒采集
jcmd <pid> JFR.start duration=30s filename=/tmp/recording.jfr

# 持续采集（后台）
jcmd <pid> JFR.start filename=/tmp/cont.jfr maxage=1h maxsize=100M

# 看正在跑的 recording
jcmd <pid> JFR.check

# 停止
jcmd <pid> JFR.stop name=1
```

### 分析 JFR

- **JDK Mission Control (JMC)**：官方 GUI，下载 `https://github.com/openjdk/jmc`
- 命令行工具：`jfr print --events cpuload recording.jfr`

### JFR 关键事件类型

- `jdk.CPULoad`：CPU 负载
- `jdk.JavaMonitorWait` / `jdk.JavaMonitorEnter`：锁等待
- `jdk.GarbageCollection`：GC
- `jdk.ObjectAllocationSample`：分配采样
- `jdk.ExecutionSample`：方法栈采样（火焰图来源）

### JDK 17 JFR 强化

**Streaming API（JDK 14+）**：实时流式 JFR 事件，无需落盘：

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

**应用场景**：
- 应用内嵌 JFR 消费 → 实时监控告警
- 自定义 dashboard 不依赖外部工具
- APM 厂商集成

**JDK 17 JFR 优势**：
- 开销 < 1%，生产长开可用
- `settings=profile` 比 `settings=default` 事件更全
- 启动时常开：`-XX:StartFlightRecording=...`
- 关键事件升级：`jdk.ObjectAllocationSample`（JDK 14+）替代旧的 allocation 采样

**JDK 17 JFR 启动配置推荐**：
```
-XX:StartFlightRecording=filename=/var/log/jfr/app.jfr,maxage=1h,maxsize=100M,settings=profile
-XX:FlightRecorderOptions=stackdepth=64
```
- `maxage=1h maxsize=100M`：滚动，1 小时或 100MB
- `settings=profile`：高采样率（vs `default`）
- `stackdepth=64`：栈深度（默认 64，复杂应用可调 128）

## async-profiler

开源，低开销采样，输出火焰图。**生产可用**。

### 安装

```bash
# 下载
wget https://github.com/async-profiler/async-profiler/releases/download/v3.0/async-profiler-3.0-linux-x64.tar.gz
tar xzf async-profiler-3.0-linux-x64.tar.gz

# macOS 用 .dmg 包
```

### CPU 火焰图

```bash
# 30 秒 CPU 采样
./profiler.sh -d 30 -f flame.html <pid>

# 在 SVG 火焰图中看哪个方法占 CPU 多
```

### 分配火焰图

```bash
# 30 秒分配采样
./profiler.sh -d 30 -e alloc -f alloc.html <pid>

# 找内存分配热点（高频 new 对象）
```

### 锁火焰图

```bash
./profiler.sh -d 30 -e lock -f lock.html <pid>
```

### 在 Java 启动时附加

```bash
# JVM 启动时挂载
java -agentpath:/path/libasyncProfiler.so=start,event=cpu,file=flame.html -jar app.jar
```

### 火焰图读法

- 横轴：方法调用栈展开
- 纵轴：栈深度（顶层是被调用方，底层是 main）
- 宽度：CPU 时间占比
- 越宽越值得优化

**找瓶颈**：找最宽的"平顶"（plateau）—— 单个方法占 CPU 比例最高。

## Arthas

阿里出的在线诊断工具，生产可用。

### 安装与启动

```bash
# 下载
curl -O https://arthas.aliyun.com/arthas-boot.jar

# 启动（attach 到指定 PID）
java -jar arthas-boot.jar <pid>
```

### 常用命令

```bash
# 方法调用耗时（找慢方法）
trace com.example.MyService doSomething

# 反编译（看 JIT 后的字节码）
jad com.example.MyService

# 方法入参出参
watch com.example.MyService doSomething '{params, returnObj}' -x 2

# 方法调用路径
stack com.example.MyService doSomething

# 看线程占用
thread -n 3

# 看 JVM 信息
dashboard
jvm

# 看哪个方法在创建对象
profiler start --event alloc
profiler stop --format flame
```

## VisualVM

本地调优工具，看实时概览。不适合生产。

### 主要功能

- 概览：CPU / 堆 / 线程 / 类
- 内存：堆直方图、GC 活动
- 线程：线程状态、死锁检测
- CPU Profiler：方法级采样（开销大）
- 内存 Profiler：分配采样

### 用法

```bash
jvisualvm  # JDK 8 自带

# JDK 9+ 单独下载
# https://visualvm.github.io/
```

## JProfiler / YourKit

商业工具，本地深度调优。功能更全，但需付费。

**何时用**：
- 复杂的内存泄漏分析
- 需要交互式 SQL 查询分析
- 需要深度的方法级 Profiling

**生产环境不用**：开销大，影响性能。

## 性能指标体系

### 核心指标

| 指标 | 含义 | 计算 |
|---|---|---|
| **吞吐量**（QPS/TPS） | 每秒处理数 | 总请求数 / 总时间 |
| **平均延迟** | 请求平均耗时 | 总耗时 / 请求数 |
| **P50** | 中位数延迟 | 排序后第 50 百分位 |
| **P90** | 90% 请求耗时 | 排序后第 90 百分位 |
| **P99** | 99% 请求耗时 | 排序后第 99 百分位 |
| **P999** | 99.9% 请求耗时 | 排序后第 99.9 百分位 |
| **GC 吞吐量** | GC 时间占比 | GC 总耗时 / 总时间 |
| **GC 停顿时间** | STW 时间 | 各次 GC 停顿 |

### 指标陷阱

**只看平均延迟**：被长尾掩盖。
- 平均 50ms，可能 99% 用户 < 10ms，1% 用户 5 秒
- 看 P99 / P999 才反映真实体验

**只看吞吐量**：可能靠堆积请求。
- 高 QPS 但请求堆积 → 平均延迟飙升
- 必须配合延迟看

**只测一次**：不可信。
- 用统计显著的多次测量（JMH 默认 5+5）
- 看 ± Error 置信区间

### 延迟 vs 吞吐量

- **吞吐量优先**：批处理、ETL、离线计算
- **延迟优先**：实时服务、用户交互、金融交易
- **二者平衡**：通常用 SLA 约束（如 P99 < 100ms）

## 性能测试方法论

### 测前确认

- 测什么？（吞吐量 / 延迟 / 内存分配）
- 用什么工具？（JMH / Profiling / 集成测试）
- 在哪个环境测？（本地 / 预发 / 生产）
- 基线是什么？（无优化版本的性能）

### 测中保证

- **隔离**：`@Fork` 隔离 JVM，避免 JIT 状态污染
- **充分预热**：`@Warmup` 让 JIT 编译完成
- **多次测量**：5+ 次迭代，看置信区间
- **不变量控制**：每次只改一个变量

### 测后验证

- 对比基线：优化前后同一指标对比
- 显著性：差异是否在置信区间外
- 生产验证：微基准结果不一定代真实负载

## 实战 Profiling 流程

### 1. 定位 CPU 瓶颈

```bash
# JFR 30 秒采样
jcmd <pid> JFR.start duration=30s filename=/tmp/cpu.jfr

# 或 async-profiler
./profiler.sh -d 30 -f flame.html <pid>

# 用 JMC 看火焰图，找最宽的方法
```

### 2. 定位分配热点

```bash
./profiler.sh -d 30 -e alloc -f alloc.html <pid>

# 或 JFR：JFR 启动时加 -XX:StartFlightRecording=filename=/tmp/alloc.jfr,settings=profile
```

找高频 `new` 的方法。

### 3. 定位锁等待

```bash
./profiler.sh -d 30 -e lock -f lock.html <pid>

# 或 JFR：看 jdk.JavaMonitorWait 事件
```

### 4. 定位 GC 频繁

```bash
# JFR：看 jdk.GarbageCollection 事件
```

**jstat / jcmd 实时 GC 诊断**：归 jvm-troubleshoot skill，见 `../../jvm-troubleshoot/references/diagnostic_commands.md` 和 `gc_tuning_guide.md`。

### 5. 在线 trace

```bash
# Arthas trace
trace com.example.MyService doSomething

# 找最慢的方法栈
```

## 参考文档

- JMH 官方：`https://openjdk.org/projects/code-tools/jmh/`
- JMH 样例：`https://github.com/openjdk/jmh/tree/main/jmh-samples/src/main/java/org/openjdk/jmh/samples`
- async-profiler：`https://github.com/async-profiler/async-profiler`
- JFR / JMC：`https://github.com/openjdk/jmc`
- Arthas：`https://arthas.aliyun.com/`
- VisualVM：`https://visualvm.github.io/`
- 火焰图原理（Brendan Gregg）：`https://www.brendangregg.com/flamegraphs.html`
