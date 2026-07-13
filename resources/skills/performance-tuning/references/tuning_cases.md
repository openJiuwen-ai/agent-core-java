# 性能调优实战案例

本文件补充 SKILL.md 的实战内容，提供 4 个端到端优化案例，对标 jvm-troubleshoot 的 troubleshooting_cases.md。用户问"怎么优化"或要参照完整调优流程时按需读取。

每个案例按"问题 → 测基线 → 定位 → 优化 → 验证"流程。

## 案例 1：Stream 改 for 循环

### 问题

日志解析服务处理 10 万条记录/秒，CPU 持续 70%，需降到 50% 以下。

代码示例：
```java
public int sum(List<Integer> values) {
    return values.stream()
        .filter(v -> v > 0)
        .mapToInt(Integer::intValue)
        .sum();
}
```

### 测基线（JMH）

```java
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
@State(Scope.Thread)
public class StreamBenchmark {

    @Param({"100", "1000", "10000"})
    int size;

    List<Integer> list;

    @Setup
    public void setup() {
        list = IntStream.range(0, size).boxed().collect(Collectors.toList());
    }

    @Benchmark
    public int stream() {
        return list.stream()
            .filter(v -> v > 0)
            .mapToInt(Integer::intValue)
            .sum();
    }

    @Benchmark
    public int forLoop() {
        int sum = 0;
        for (int v : list) {
            if (v > 0) sum += v;
        }
        return sum;
    }
}
```

### 结果（示例）

```
Benchmark                      (size)   Mode  Cnt     Score   Error  Units
StreamBenchmark.stream             100  thrpt    10    12.345 ± 0.5  ops/ms
StreamBenchmark.forLoop            100  thrpt    10    45.678 ± 1.2  ops/ms  ← 3.7x 快
StreamBenchmark.stream           10000  thrpt    10     1.234 ± 0.1  ops/ms
StreamBenchmark.forLoop          10000  thrpt    10     5.678 ± 0.3  ops/ms  ← 4.6x 快
```

### 定位

Stream 慢的原因：
1. `stream()` 创建 Stream 对象 → 开销
2. `filter` / `mapToInt` 装箱拆箱 → 装箱开销
3. 内部 Spliterator 迭代 → 间接调用

### 优化

```java
// 简单遍历用 for
public int sum(List<Integer> values) {
    int sum = 0;
    for (int v : values) {
        if (v > 0) sum += v;
    }
    return sum;
}
```

### 验证

- JMH 测：for 比 Stream 快 3.7-4.6x
- 生产 CPU 从 70% 降到 45%
- 注意：不是所有场景 for 都快，复杂链式 transform 用 Stream 可读性更好

### 反思

- Stream 不是性能优化手段，是可读性工具
- 简单遍历用 for，复杂 transform 用 Stream
- 别盲目 `parallelStream` —— 小集合反而慢

## 案例 2：HashMap 调容量 + 锁改 LongAdder

### 问题

限流计数器：1000 QPS + 200 并发线程 → `AtomicInteger` 自增在多核 CPU 上 CAS 重试，吞吐量瓶颈。

代码：
```java
public class RateLimiter {
    private final AtomicInteger count = new AtomicInteger(0);
    private final Map<String, Integer> stats = new HashMap<>();
    private final Object lock = new Object();

    public void increment() {
        count.incrementAndGet();
        synchronized(lock) {
            stats.put("total", count.get());
        }
    }
}
```

### 测基线（JMH）

```java
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
@State(Scope.Group)
public class CounterBenchmark {

    AtomicInteger atomic = new AtomicInteger();
    LongAdder adder = new LongAdder();

    @Benchmark
    @Group("atomic")
    @GroupThreads(8)
    public void atomicInc() {
        atomic.incrementAndGet();
    }

    @Benchmark
    @Group("adder")
    @GroupThreads(8)
    public void adderInc() {
        adder.increment();
    }
}
```

### 结果

```
Benchmark                          Mode  Cnt     Score    Error  Units
CounterBenchmark.atomic              thrpt    10    234.5  ± 12.3  ops/ms
CounterBenchmark.adder               thrpt    10   1234.5  ± 45.6  ops/ms  ← 5.3x 快
```

### 定位

- `AtomicInteger` 高竞争下 CAS 重试 → 自旋开销
- `synchronized + HashMap` 锁粒度粗，并发受限
- `HashMap` 默认容量 16 + loadFactor 0.75 → 频繁扩容

### 优化

```java
public class RateLimiter {
    // 高并发计数用 LongAdder
    private final LongAdder count = new LongAdder();

    // 高并发 map 用 ConcurrentHashMap
    private final ConcurrentHashMap<String, Long> stats = new ConcurrentHashMap<>(64);

    public void increment() {
        count.increment();
        stats.compute("total", (k, v) -> v == null ? 1 : v + 1);
    }
}
```

### 进一步：HashMap 调容量

```java
// 原代码
Map<String, Integer> stats = new HashMap<>();  // 默认容量 16

// 已知大小（如 100 个 key）→ 显式指定
Map<String, Integer> stats = new HashMap<>(100 / 0.75 + 1);  // 避免扩容
```

或更高并发场景：
```java
ConcurrentHashMap<String, Long> stats = new ConcurrentHashMap<>(64);
```

### 验证

- LongAdder vs AtomicInteger：5.3x 快
- ConcurrentHashMap vs HashMap + synchronized：并发 5x+
- HashMap 调容量后无扩容停顿

### 反思

- `AtomicInteger` 适合低竞争，`LongAdder` 适合高竞争
- `HashMap + synchronized` 高并发死路一条 → `ConcurrentHashMap`
- HashMap 已知大小 → 显式容量，避免扩容

## 案例 3：反射改 MethodHandle

### 问题

序列化框架每秒处理 5 万对象，反射 `Method.invoke` 是瓶颈，CPU 占 40%。

代码：
```java
public class Serializer {
    public Object getField(Object obj) throws Exception {
        Method m = obj.getClass().getMethod("getValue");  // 每次反射获取
        return m.invoke(obj);
    }
}
```

### 测基线（JMH）

```java
@State(Scope.Thread)
public class ReflectionBenchmark {

    Method method;
    MethodHandle mh;
    Function<MyObject, String> lambda;

    @Setup
    public void setup() throws Exception {
        method = MyObject.class.getMethod("getValue");
        method.setAccessible(true);  // 关闭访问检查

        MethodHandles.Lookup lookup = MethodHandles.lookup();
        mh = lookup.findVirtual(MyObject.class, "getValue",
            MethodType.methodType(String.class));

        // LambdaMetafactory 转 Function
        // （简化：实际代码略复杂，见 code_level_optimization.md）
    }

    @Benchmark
    public String reflectNoCache() throws Exception {
        // 反模式：每次反射获取 Method
        Method m = obj.getClass().getMethod("getValue");
        return (String) m.invoke(obj);
    }

    @Benchmark
    public String reflectCached() throws Exception {
        return (String) method.invoke(obj);
    }

    @Benchmark
    public String methodHandle() throws Throwable {
        return (String) mh.invoke(obj);
    }
}
```

### 结果

```
Benchmark                          Mode  Cnt     Score    Error  Units
ReflectionBenchmark.reflectNoCache    thrpt    10    45.6  ± 2.3  ops/μs
ReflectionBenchmark.reflectCached      thrpt    10   234.5  ± 12.3  ops/μs  ← 5x
ReflectionBenchmark.methodHandle       thrpt    10  1234.5  ± 45.6  ops/μs  ← 27x
```

### 定位

反射慢的原因：
1. 每次 `getMethod` 扫方法表 → 大开销
2. `invoke` 内部参数检查（除非 `setAccessible(true)`）
3. 装箱/拆箱
4. JIT 难优化（虚调用）

### 优化

```java
public class Serializer {
    private static final MethodHandle MH;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            MH = lookup.findVirtual(MyObject.class, "getValue",
                MethodType.methodType(String.class));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public Object getField(Object obj) throws Throwable {
        return MH.invoke(obj);
    }
}
```

**进一步**（LambdaMetafactory）：
```java
// 把 MethodHandle 转成 Lambda，运行时无反射开销
Function<MyObject, String> getter = (Function<MyObject, String>) LambdaMetafactory.metafactory(
    lookup, "apply", MethodType.methodType(Function.class),
    MethodType.methodType(Object.class, Object.class),
    MH, MethodType.methodType(String.class, MyObject.class)
).getTarget().invoke();

// 使用：接近直接调用
String val = getter.apply(obj);
```

### 验证

- 反射缓存 Method：5x 提速
- MethodHandle：27x 提速（vs 未缓存）
- LambdaMetafactory：接近直接调用

### 反思

- 反射必缓存 Method/Field
- MethodHandle 比 `Method.invoke` 快 5-10x
- LambdaMetafactory 接近直接调用
- 大型框架（Spring/Hibernate）用 ByteBuddy/CGLIB 字节码生成，更快但复杂

## 案例 4：JVM 参数调优（容器内微服务）

### 问题

Spring Boot 3 微服务（JDK 17），部署在 4GB 内存容器内。启动后 P99 延迟 300ms，目标降到 100ms 以内。

### 测基线

- 启动时间：25s
- P99：300ms
- GC 日志：Full GC 5 分钟一次，Young GC 200ms 停顿
- `jcmd <pid> VM.flags`：默认 G1，堆默认值（约 1GB）

### 定位

1. **堆太小**：容器 4GB，JVM 默认按物理机算 → 堆约 1GB，频繁 GC
2. **没预热**：JIT 没编译到层 4，方法都走解释执行
3. **GC 日志没开**：调试困难

### 优化

**第 1 步：容器内堆比例 + GC 日志**

```
-XX:+UseContainerSupport
-XX:InitialRAMPercentage=50.0
-XX:MaxRAMPercentage=75.0
-XX:+UseG1GC
-XX:MaxGCPauseMillis=200
-XX:MetaspaceSize=256m
-XX:MaxMetaspaceSize=512m
-Xlog:gc*:file=/var/log/gc.log:time,uptime:filecount=10,filesize=10M
-XX:+HeapDumpOnOutOfMemoryError
-XX:HeapDumpPath=/var/log/dumps/
-XX:+AlwaysPreTouch
-XX:+DisableExplicitGC
```

**变化**：
- 堆从 1GB → 3GB（容器 4GB × 75%）
- GC 日志开了
- AlwaysPreTouch 启动时预触内存，减少运行时缺页停顿

**第 2 步：JFR 监控**

```
-XX:StartFlightRecording=filename=/var/log/jfr/app.jfr,maxage=1h,maxsize=100M,settings=profile
-XX:FlightRecorderOptions=stackdepth=64
```

**第 3 步：应用层优化**

- 预热：启动后用 mock 流量打 5 分钟
- 日志框架：异步日志（Logback `AsyncAppender`）
- HTTP client：连接池（OkHttp）

### 验证

| 指标 | 基线 | 优化后 |
|---|---|---|
| 启动时间 | 25s | 22s（预热占 5s） |
| P99 延迟 | 300ms | 85ms |
| Full GC | 5 分钟一次 | 0（消失） |
| Young GC 停顿 | 200ms | 80ms |
| 堆占用 | 90%（频繁 GC） | 60% |

**JFR 分析**：发现还有 5% 时间在 `Object.wait`（连接池等待），调大 HikariCP 连接池 10 → 20，P99 进一步降到 70ms。

### 反思

- 容器内必用 `UseContainerSupport` + RAM 百分比
- GC 日志 + JFR 必开，无监控无优化
- 预热对长跑服务有用，对短跑应用用 GraalVM
- 别一次调太多参数，分阶段验证

## 案例 5：逃逸分析失效场景

### 问题

日志格式化方法，每次调用创建 `StringBuilder`，本应被逃逸分析消除，但 GC 日志显示分配热点。

代码：
```java
public String format(LogRecord record) {
    StringBuilder sb = new StringBuilder();
    sb.append(record.getTimestamp());
    sb.append(" ");
    sb.append(record.getLevel());
    sb.append(" ");
    sb.append(record.getMessage());
    return sb.toString();
}
```

### 定位

逃逸分析应判定 `sb` 不逃逸 → 标量替换。但 JFR 显示 `StringBuilder` 仍在堆上分配。

**原因**：方法被 C2 编译后，`sb` 虽然不逃逸出方法，但 `sb.toString()` 创建新 String 对象，该 String 逃逸出方法 → JIT 保守不消除 `sb`。

### 验证 JIT 行为

```bash
# 启动时加
-XX:+UnlockDiagnosticVMOptions -XX:+PrintInlining -XX:+PrintEscapeAnalysis
```

输出：
```
 EscapeAnalysis:
   StringBuilder sb = new -- ArgEscape (toString returns)
```

### 优化

**方案 1**：直接返回字符串拼接（编译器自动用 StringBuilder，但更简单）：
```java
public String format(LogRecord record) {
    return record.getTimestamp() + " " + record.getLevel() + " " + record.getMessage();
}
```

**方案 2**：方法变 final，让 JIT 更激进内联：
```java
public final String format(LogRecord record) { ... }
```

**方案 3**：用 `String.format`（性能更差，避免）

### 验证

- JFR：StringBuilder 分配热点消失
- GC 频率降低 20%

### 反思

- 逃逸分析不是万能，复杂场景可能失效
- 简单字符串拼接用 `+`，编译器优化更彻底
- 关键路径用 `-XX:+PrintEscapeAnalysis` 验证

## 案例 6：ZGC 切换（大堆低延迟）

### 问题

缓存服务堆 32GB，G1 Young GC 停顿 300ms，业务 P99 卡到 500ms。SLA 要求 P99 < 100ms。

### 测基线

- G1：Young GC 停顿 300ms，Full GC 1 小时一次 1.5s
- P99 延迟：500ms（GC 期间堆积请求）

### 优化

```
-Xms32g -Xmx32g
-XX:+UseZGC                       # JDK 17 非分代 ZGC
-XX:ZAllocationSpikeTolerance=2
-XX:ConcGCThreads=8              # 16 核机器，半数给 GC
-XX:MetaspaceSize=512m
-XX:MaxMetaspaceSize=1g
-Xlog:gc*:file=/var/log/gc.log:time,uptime:filecount=20,filesize=50M
```

### 验证

| 指标 | G1 | ZGC |
|---|---|---|
| GC 停顿 | 300ms | < 5ms |
| P99 延迟 | 500ms | 80ms |
| GC 吞吐量 | 95% | 98% |
| CPU 占用 | 60% | 75%（并发 GC 占多） |

**注意**：JDK 17 ZGC 非分代，CPU 占用稍高。若堆更大（64GB+）对吞吐敏感，升级 JDK 21 用分代 ZGC。

### 反思

- 大堆 + 低延迟 → ZGC
- ZGC 不是免费的，CPU 占用升高换停顿降低
- JDK 17 非分代 ZGC；JDK 21+ 分代 ZGC 性能更好

## 通用调优方法论

### 流程

1. **测基线**：JMH / JFR / GC 日志拿到当前数据
2. **定位瓶颈**：火焰图 / JFR 找热点
3. **优化**：每次只改一个变量
4. **验证**：同基线对比，看置信区间
5. **生产验证**：微基准结果不代真实负载，A/B 测试

### 反模式

- ❌ 没测就调 → 多数无收益
- ❌ 改多个变量 → 不知道哪个有效
- ❌ 微基准就上线 → 不代真实负载
- ❌ 优化过头 → 5% 收益花 50% 时间，不值

### 收益排序（一般情况）

1. **算法层**：O(n²) → O(n log n) 收益 >> 其他
2. **数据结构层**：ArrayList vs LinkedList，HashMap vs TreeMap
3. **代码层**：循环外提、对象重用、StringBuilder 缓存
4. **JVM 层**：GC 参数、堆大小
5. **硬件层**：CPU / 内存 / SSD

从上往下做，下层收益递减。

## 参考文档

- 项目内事后排查案例：`../../jvm-troubleshoot/references/troubleshooting_cases.md`
- 项目内 JMH 详解：`jmh_profiling.md`
- 项目内代码级优化：`code_level_optimization.md`
- JMH 官方样例：`https://github.com/openjdk/jmh/tree/main/jmh-samples/src/main/java/org/openjdk/jmh/samples`
