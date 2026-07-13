# 代码级性能优化

本文件补充 SKILL.md 的代码级性能内容，深入讲集合/Stream/反射/锁/逃逸分析细节。用户问"这个写法快不快"或"为什么这么写慢"时按需读取。

## 集合选型详解

### List 选型

| 实现 | 底层 | 随机访问 | 头部插 | 尾部插 | 中间插 | 内存 |
|---|---|---|---|---|---|---|
| `ArrayList` | 数组 | O(1) | O(n) | 均摊 O(1) | O(n) | 紧凑 |
| `LinkedList` | 双向链表 | O(n) | O(1) | O(1) | O(1)（已知位置） | 大（节点开销） |
| `CopyOnWriteArrayList` | 数组（写时复制） | O(1) | O(n) | O(n) | O(n) | 写时翻倍 |
| `ArrayDeque` | 循环数组 | O(1) | O(1) | O(1) | O(n) | 紧凑 |

**选型决策**：
- **默认用 `ArrayList`**：90% 场景最佳
- **频繁头部插删**：`ArrayDeque`（当 List 用，或当 Deque）
- **写极少读多**：`CopyOnWriteArrayList`
- **别用 `LinkedList`**：在 99% 场景不如 `ArrayList` 或 `ArrayDeque`，唯一适合的是"频繁已知位置中间插"且不接受数组搬移

**ArrayList 性能细节**：
- `new ArrayList<>()` 初始容量 10，第一次 add 触发扩容到 10
- 扩容 1.5 倍：`oldCapacity + (oldCapacity >> 1)`
- 已知大小：`new ArrayList<>(expectedSize)` 避免多次扩容
- `subList()` 返回视图，修改影响原 List；原 List 结构改后用 subList 抛 `ConcurrentModificationException`

### Map 选型

| 实现 | 底层 | null key | null val | 并发 | 备注 |
|---|---|---|---|---|---|
| `HashMap` | 数组 + 链表 + 红黑树 | ✓ | ✓ | 否 | 默认 |
| `LinkedHashMap` | HashMap + 双向链表 | ✓ | ✓ | 否 | 保序 / LRU |
| `TreeMap` | 红黑树 | × | ✓ | 否 | 排序 |
| `ConcurrentHashMap` | CAS + synchronized 分段 | × | × | 是 | 高并发 |
| `EnumMap` | 数组 | × | ✓ | 否 | enum key 最快 |
| `IdentityHashMap` | 数组（== 比较） | ✓ | ✓ | 否 | 引用相等 |

**HashMap 关键参数**：
- `loadFactor` 默认 0.75，空间/时间平衡点
- `initialCapacity`：默认 16，已知大小用 `new HashMap<>(expectedSize / 0.75 + 1)` 避免扩容
- 树化阈值：链表 ≥ 8 且数组 ≥ 64 → 树化；< 6 退化为链表

**HashMap JDK 演进**：
- JDK7：数组 + 链表，哈希碰撞 → 链表，多线程扩容成环 → 死循环
- JDK8：数组 + 链表 + 红黑树（≥8 树化），扩容时拆分链表，无死循环
- JDK8 hash 优化：高 16 位异或低 16 位 `(h ^ (h >>> 16))`，减少碰撞

**ConcurrentHashMap 演进**：
- JDK7：Segment 分段锁（默认 16 段），并发度 = 16
- JDK8：CAS + synchronized 锁单个桶（数组元素），并发度 = 桶数
- JDK8+ 树化：冲突 ≥ 8 转红黑树，避免哈希碰撞攻击

### Set 选型

| 实现 | 底层 | 备注 |
|---|---|---|
| `HashSet` | HashMap（value 固定） | 默认 |
| `LinkedHashSet` | LinkedHashMap | 保插入序 |
| `TreeSet` | TreeMap | 排序 |
| `EnumSet` | 位向量 | enum 最快 |
| `CopyOnWriteArraySet` | CopyOnWriteArrayList | 读多写极少 |

### Queue 选型

| 实现 | 阻塞 | 有界 | 备注 |
|---|---|---|---|
| `ArrayDeque` | 否 | 否 | 单线程首选 |
| `LinkedList` | 否 | 否 | 别用（性能差） |
| `PriorityQueue` | 否 | 否 | 优先队列（小顶堆） |
| `ArrayBlockingQueue` | 是 | 是 | 有界，生产常用 |
| `LinkedBlockingQueue` | 否（无界默认）/ 是 | 否 / 是 | Executors 默认用 |
| `ConcurrentLinkedQueue` | 否 | 否 | 无锁 CAS，高并发 |
| `DelayQueue` | 是 | 否 | 延迟任务 |

**线程池队列选型**：
- 默认 `LinkedBlockingQueue` 无界 → 任务堆积 OOM
- 有界 `ArrayBlockingQueue` → 配合拒绝策略
- 优先级 `PriorityBlockingQueue` → 任务有优先级

### 大集合避坑

**别一次加载大数据到内存**：
- 百万级数据 → 用游标/分页/Stream
- DB 查询用 `LIMIT` + `OFFSET` 或游标
- 文件处理用 `BufferedReader` / `Stream<String>`

**集合迭代删除**：
- 别 `for + remove` → `ConcurrentModificationException`
- 用 `iterator.remove()` 或 `list.removeIf(pred)`（JDK8+）

## Stream 性能详解

### Stream 不是性能优化手段

Stream 设计目标是**可读性 + 并行化**，不是性能。简单遍历用 Stream 多数比 for 慢：

| 场景 | for | Stream | parallelStream |
|---|---|---|---|
| 小集合（< 1000）简单遍历 | 1x | 1.2-2x 慢 | 5-10x 慢 |
| 大集合（> 10000）简单遍历 | 1x | 1.1-1.5x 慢 | 视情况 |
| 大集合 CPU 密集 transform | 1x | 持平 | 可能快 |
| 大集合带 IO | 1x | 持平 | **可能更快** |

**慢的原因**：
- 装箱开销（`Stream<Integer>` vs `IntStream`）
- 迭代器/Consumer 间接调用
- 流创建开销
- `parallelStream` ForkJoinPool 拆分开销

### Stream 正确用法

**用原始流避免装箱**：
```java
// 慢：装箱
list.stream().mapToInt(Integer::intValue).sum();

// 比 for 还是慢，但比 boxed stream 快
IntStream.range(0, list.size()).map(i -> list.get(i)).sum();
```

**用 collector 而非手动累积**：
```java
// 慢：字符串拼接
list.stream().map(...).collect(StringBuilder::new, StringBuilder::append, StringBuilder::append);

// 快
list.stream().map(...).collect(Collectors.joining(","));
```

**groupingBy 正确用法**：
```java
// 简单分组
Map<Key, List<Item>> byKey = items.stream().collect(groupingBy(Item::key));

// 下游 collector 减少装箱
Map<Key, Long> countByKey = items.stream().collect(groupingBy(Item::key, counting()));

// 并发分组
Map<Key, List<Item>> byKey = items.parallelStream().collect(groupingByConcurrent(Item::key));
```

### parallelStream 何时用

**用 parallelStream 的条件**：
- 数据量 > 10000
- CPU 密集（无 IO）
- 任务无状态依赖
- 单元素处理时间 > 1μs

**反模式**：
- ❌ `list.parallelStream().forEach(x -> db.save(x))` → DB 是瓶颈，并行更慢且压垮 DB
- ❌ `list.parallelStream().filter(...).findFirst()` → 顺序结果，并行无意义
- ❌ 小集合 parallelStream → ForkJoin 拆分开销 > 收益
- ❌ `parallelStream` 内部用 `ThreadLocal` → 线程切换丢失上下文

**parallelStream 共享 ForkJoinPool**：
- 所有 `parallelStream` 共用 commonPool（CPU 核数 - 1）
- 一个任务卡住 → 整个 JVM 的并行流都受影响
- 自定义池：`ForkJoinPool custom = new ForkJoinPool(N); custom.submit(() -> list.parallelStream()...).get()`

## 反射优化详解

### 反射慢的原因

- 调用前查方法表
- 装箱/拆箱
- 参数检查（除非 `setAccessible(true)`）
- 安全管理器检查

### 优化路径

**Level 0：缓存 Method/Field 对象**

```java
// 慢：每次反射获取
for (Item item : items) {
    Method m = item.getClass().getMethod("getName");
    String name = (String) m.invoke(item);
}

// 快：缓存
private static final Method GET_NAME = initMethod();
private static Method initMethod() {
    try {
        Method m = Item.class.getMethod("getName");
        m.setAccessible(true);  // 关闭访问检查
        return m;
    } catch (NoSuchMethodException e) { throw new RuntimeException(e); }
}
// 使用
String name = (String) GET_NAME.invoke(item);
```

**Level 1：setAccessible(true)**

JDK9+ 模块系统需 `--add-opens` 才能访问非 export 包，否则 `InaccessibleObjectException`。

**Level 2：MethodHandle**

```java
MethodHandles.Lookup lookup = MethodHandles.lookup();
MethodHandle mh = lookup.findVirtual(String.class, "length", MethodType.methodType(int.class));
int len = (int) mh.invoke("hello");  // 接近直接调用
```

**Level 3：LambdaMetafactory**

把 MethodHandle 转成 `Function` Lambda，运行时无反射开销：

```java
MethodHandles.Lookup lookup = MethodHandles.lookup();
MethodHandle mh = lookup.findVirtual(Item.class, "getName", MethodType.methodType(String.class));
Function<Item, String> getName = (Function<Item, String>) LambdaMetafactory.metafactory(
    lookup, "apply", MethodType.methodType(Function.class),
    MethodType.methodType(Object.class, Object.class),
    mh, MethodType.methodType(String.class, Item.class)
).getTarget().invoke();
// 使用：接近直接调用
String name = getName.apply(item);
```

**Level 4：字节码生成**

- ByteBuddy / CGLIB：运行时生成子类，调用走直接字节码
- 一次性生成开销大，调用极快
- 适合框架（Spring AOP、Hibernate）

### 反射反模式

- ❌ 热路径每次反射获取 Method → 缓存
- ❌ 反射调 private 不 `setAccessible(true)` → 安全检查开销
- ❌ JDK9+ 不开 `--add-opens` → 模块访问失败

### 替代反射

- **Records**（JDK14+）：自动生成访问器，无反射
- ** sealed class + 模式匹配**（JDK17）：编译时已知类型，无反射
- **VarHandle**（JDK9+）：替代 `Field.set/get` 的 volatile/有序访问

## 锁优化详解

### 锁优化的 4 个层次

**层次 1：锁消除（Lock Elision）**

JIT 通过逃逸分析判定对象不逃逸出方法/线程 → 自动消除 synchronized：

```java
// JIT 判定 sb 不逃逸 → 锁消除
public String concat(String a, String b) {
    StringBuffer sb = new StringBuffer();  // 局部，不逃逸
    sb.append(a).append(b);
    return sb.toString();
}
```

**触发条件**：开逃逸分析 `-XX:+DoEscapeAnalysis`（JDK8+ 默认开）

**层次 2：锁粗化（Lock Coarsening）**

JIT 把相邻 synchronized 块合并：

```java
// 优化前：每次循环都锁
for (int i = 0; i < n; i++) {
    synchronized(lock) { ... }
}

// 锁粗化后：合并到循环外
synchronized(lock) {
    for (int i = 0; i < n; i++) { ... }
}
```

**触发条件**：JIT 判定循环内同步块可合并。

**层次 3：锁粒度优化**

```java
// 粗粒度：整个 map 一把锁
synchronized(map) { ... }

// 细粒度：分段锁（JDK7 ConcurrentHashMap）
// 更细：CAS + 桶级 synchronized（JDK8+ ConcurrentHashMap）
```

**层次 4：无锁**

- 计数器：`LongAdder` / `AtomicLong`
- 引用：`AtomicReference` / `VarHandle`
- 并行计算：`ForkJoinPool` / `CompletableFuture`

### synchronized vs ReentrantLock

| 维度 | synchronized | ReentrantLock |
|---|---|---|
| 性能 | JDK6+ 接近 | 略慢（除非高竞争） |
| 可中断 | 否 | `lockInterruptibly()` |
| 超时 | 否 | `tryLock(timeout)` |
| 公平 | 非公平 | 可选公平 |
| 条件队列 | 1 个（wait/notify） | 多个 `Condition` |
| 锁释放 | 自动（出块） | 必须 `finally` 显式 unlock |

**选型**：
- 简单互斥 → `synchronized`
- 需要超时/中断/多条件 → `ReentrantLock`
- 不要为了"性能"硬切 `ReentrantLock`，JDK6+ synchronized 优化后够用

### StampedLock

JDK8 加的乐观读锁，读多写少场景比 `ReentrantReadWriteLock` 快。

```java
StampedLock sl = new StampedLock();
long stamp = sl.tryOptimisticRead();  // 乐观读，无 CAS
int x = currentX;
int y = currentY;
if (!sl.validate(stamp)) {  // 校验期间是否被写
    stamp = sl.readLock();  // 失败升级为悲观读
    try { x = currentX; y = currentY; }
    finally { sl.unlockRead(stamp); }
}
// 使用 x, y
```

**注意**：
- 不可重入！同线程重复 `readLock()` 会死锁
- 乐观读适合"读多写极少"
- 写锁独占，与读锁互斥

### LongAdder vs AtomicLong

| 场景 | AtomicLong | LongAdder |
|---|---|---|
| 低竞争 | 快 | 略慢（多字段） |
| 高竞争 | 慢（CAS 重试） | 快 5-10x（分段累加） |
| 内存 | 1 long | 多个 Cell + base |

**原理**：LongAdder 把单个 counter 拆成多个 Cell，线程分散到不同 Cell CAS，最后 `sum()` 累加。竞争越激烈越快。

**场景**：高并发计数器、统计、限流 → `LongAdder`

### 锁优化要点

**减少锁持有时间**：
```java
// 慢：整个方法都持锁
synchronized(lock) {
    data = loadData();  // IO 慢，但持锁
    process(data);
}

// 快：缩小持锁范围
data = loadData();  // 不持锁
synchronized(lock) {
    process(data);
}
```

**锁分离**：
```java
// 一把大锁
synchronized(lock) { readA(); writeB(); }

// 分离为两把
synchronized(lockA) { readA(); }
synchronized(lockB) { writeB(); }
```

**避免热点字段**：
- `AtomicInteger` 自增在多核 CPU 高竞争 → 用 `LongAdder`

## 逃逸分析与栈上分配

### 逃逸分析级别

JIT 通过逃逸分析（Escape Analysis）判定对象是否逃逸：

| 级别 | 描述 | JIT 行为 |
|---|---|---|
| NoEscape | 不逃逸 | 栈上分配 + 标量替换 → 不占堆，无 GC |
| ArgEscape | 仅作为参数传给 callee | 堆分配，但调用方不见 |
| MethodEscape | 逃逸出方法 | 堆分配 |
| ThreadEscape | 逃逸到其他线程 | 堆分配，需考虑可见性 |

### 标量替换（Scalar Replacement）

对象不逃逸 → JIT 把对象字段拆为独立局部变量：

```java
// 优化前
class Point { int x, y; }
int sum() {
    Point p = new Point(1, 2);
    return p.x + p.y;
}

// 标量替换后
int sum() {
    int x = 1, y = 2;  // 对象消失
    return x + y;
}
```

**收益**：对象不进堆，无 GC 压力，无内存分配开销。

### 帮助逃逸分析

- 别在热路径返回 `new` 的小对象（鼓励复用）
- 别把临时对象赋值给 static 字段
- 别在循环里创建大对象
- 别让局部对象逃逸进 lambda/匿名内部类

### 反模式

- ❌ 热路径每次 `new` 临时对象 → 逃逸分析可能能消除，但别依赖
- ❌ 热路径把对象放进 ThreadLocal → 逃逸到线程
- ❌ 热路径把对象塞进集合 → 逃逸

### JIT 行为确认

确认逃逸分析是否生效：
```bash
# 启动参数
-XX:+DoEscapeAnalysis -XX:+PrintEscapeAnalysis -XX:+PrintInlining
```

注意：逃逸分析需要 JIT 预热，短跑应用可能没生效。

## 字符串优化

### StringBuilder vs +

- `+` 在循环里 → 编译成 `new StringBuilder().append().append()`，循环每次都 new
- 循环外或简单拼接 → 编译器优化为单次 StringBuilder
- 复杂多行拼接 → 显式 `StringBuilder`

```java
// 慢：循环内 +
String s = "";
for (String x : list) s += x;  // 每次新建 StringBuilder

// 快：循环外 StringBuilder
StringBuilder sb = new StringBuilder();
for (String x : list) sb.append(x);
String s = sb.toString();
```

### 字符串常量池

- `intern()` 放入常量池，相同字符串复用
- JDK7+ 常量池在堆，不会 PermGen/Metaspace OOM
- 别滥用 `intern()`，可能让常量池膨胀
- 比较：`==` 不可靠，用 `equals()`

### 字符串编码

- `String` 内部 UTF-16，每字符 2 字节
- ASCII 字符串用 `byte[]` + Latin-1 编码（JDK9+ Compact Strings）
- 大量 ASCII 字符串处理考虑 `byte[]` 直接操作

## 对象池与重用

### 别滥用对象池

JDK 17 的 young GC 很快，分配 + 回收比对象池维护开销小。

| 场景 | 用池 | 不用池 |
|---|---|---|
| 普通 POJO | × | ✓ |
| JDBC Connection | ✓ | |
| 线程 | ✓（ThreadPool） | |
| 大对象（> 几 KB） | 视情况 | |
| Buffer / ByteBuffer | ✓（Netty Pool） | |

**判断**：分配开销 > 池维护开销才用池。多数场景 GC 比池快。

### ThreadLocal 重用

```java
private static final ThreadLocal<SimpleDateFormat> TL = ThreadLocal.withInitial(
    () -> new SimpleDateFormat("yyyy-MM-dd")
);
// 复用
TL.get().format(date);
```

注意：线程池场景 ThreadLocal 需 `remove()`，否则线程复用后数据残留。

**JDK 17 替代方案**：用 `DateTimeFormatter`（线程安全，无需 ThreadLocal）：
```java
private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
String s = FMT.format(LocalDate.now());
```

## JDK 17 新特性（性能相关）

### Records（JDK 14+ stable）

编译器自动生成 equals/hashCode/toString，比反射或 Lombok 快：

```java
public record Point(int x, int y) {}
```

**性能收益**：
- 无反射开销
- 编译器生成 `equals`/`hashCode` 比 Lombok `@Data` 快（Lombok 部分场景用反射）
- 不可变 → 可被逃逸分析消除

**适用**：DTO、值对象、配置类。

### Sealed Class（JDK 17 stable）

编译时穷尽匹配，帮助 JIT 去虚化：

```java
public sealed interface Shape
    permits Circle, Rectangle, Triangle {}

public final class Circle implements Shape { ... }
public final class Rectangle implements Shape { ... }
public final class Triangle implements Shape { ... }

// 编译时穷尽
public double area(Shape s) {
    return switch (s) {
        case Circle c -> Math.PI * c.r() * c.r();
        case Rectangle r -> r.w() * r.h();
        case Triangle t -> t.b() * t.h() / 2;
    };
}
```

**性能收益**：
- 编译时已知所有实现 → JIT 单态化 / 双态化内联
- pattern matching 比 `instanceof` 链快
- switch 用 `tableswitch`（跳转表），比 `lookupswitch` 快

### 模块系统与 --add-opens（JDK 17 必踩）

JDK 9+ 模块系统，反射访问非 export 包需 `--add-opens`：

```
--add-opens java.base/java.lang=ALL-UNNAMED
--add-opens java.base/java.util=ALL-UNNAMED
--add-opens java.base/java.nio=ALL-UNNAMED
```

**报错关键词**：`InaccessibleObjectException` → 缺 `--add-opens`。

**典型场景**：
- Spring / Hibernate / ByteBuddy 反射 → 需 `--add-opens`
- Netty 访问 NIO 内部 → `--add-opens java.base/java.nio`
- `Unsafe` 或反射访问 `String.value` → `--add-opens java.base/java.lang`

### Foreign Memory Access API（incubator）

JDK 17 incubator（`--add-modules jdk.incubator.foreign`），堆外内存访问的官方替代方案，替代 `ByteBuffer.allocateDirect` + `sun.misc.Unsafe`：

```java
import jdk.incubator.foreign.MemorySegment;
import jdk.incubator.foreign.ResourceScope;

try (ResourceScope scope = ResourceScope.newConfinedScope()) {
    MemorySegment seg = MemorySegment.allocateNative(100, scope);
    seg.set(ValueLayout.JAVA_INT, 0, 42);
    int val = seg.get(ValueLayout.JAVA_INT, 0);
}
```

**收益**：
- 比 `ByteBuffer.allocateDirect` 快（无 cleaner 依赖）
- 比 `Unsafe` 安全（有边界检查）
- 自动释放（ResourceScope）

**注意**：JDK 17 incubator，API 可能变。JDK 19+ stable。

### Vector API（incubator）

JDK 17 incubator（`--add-modules jdk.incubator.vector`），SIMD 写法，替代手写循环向量化：

```java
import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorSpecies;

static final VectorSpecies<Float> SPECIES = FloatVector.SPECIES_256;

void vectorAdd(float[] a, float[] b, float[] c) {
    int i = 0;
    int upper = SPECIES.loopBound(a.length);
    for (; i < upper; i += SPECIES.length()) {
        FloatVector va = FloatVector.fromArray(SPECIES, a, i);
        FloatVector vb = FloatVector.fromArray(SPECIES, b, i);
        va.add(vb).intoArray(c, i);
    }
    for (; i < a.length; i++) c[i] = a[i] + b[i];
}
```

**收益**：明确 SIMD 化，比 C2 自动向 Vector 化稳定。JDK 17 仍 incubator。

**注意**：JDK 17 incubator，API 可能变。JDK 19+ preview。

### 虚拟线程（JDK 21+，JDK 17 无）

**重要**：虚拟线程在 JDK 21+ 才 stable，JDK 17 不可用。

JDK 17 替代方案：用 `CompletableFuture` / `Reactor` / `RxJava` 异步编排。

升级到 JDK 21+ 后：
```java
// JDK 21+
Thread.startVirtualThread(() -> { ... });

try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    IntStream.range(0, 10000).forEach(i ->
        executor.submit(() -> { handleRequest(i); return null; })
    );
}
```

**收益**：百万级线程，IO 密集场景吞吐量 5-10x。
**限制**：CPU 密集无收益；synchronized 持有期间 pin 线程（JDK 21 解决方案用 `ReentrantLock`）。

## 常用反模式速查

| 反模式 | 问题 | 正确做法 |
|---|---|---|
| 在循环里 `new` 大对象 | 频繁 GC | 提到循环外，或用对象池 |
| `LinkedList` 当 List 用 | 性能差 | `ArrayList` |
| `HashMap` + `synchronized` | 锁粒度粗 | `ConcurrentHashMap` |
| `Vector` / `Hashtable` | 全方法锁 | `CopyOnWriteArrayList` / `ConcurrentHashMap` |
| Stream 简单遍历 | 装箱开销 | for 循环 |
| `parallelStream` 内部 IO | 阻塞共享 ForkJoinPool | 异步 + 自定义池 |
| 反射不缓存 Method | 反射开销 | static final 缓存 |
| `SimpleDateFormat` 共享 | 线程不安全 | ThreadLocal 或 `DateTimeFormatter` |
| `+` 字符串拼接在循环 | 每次新建 StringBuilder | 显式 StringBuilder |
| `==` 比字符串 | 不可靠 | `equals()` |
| JDK 17 反射不开 `--add-opens` | `InaccessibleObjectException` | JVM 参数加 `--add-opens` |
| JDK 17 用 `SimpleDateFormat` 共享 | 线程不安全 | `DateTimeFormatter`（线程安全） |
| JDK 17 等 JIT 自动向量化 | 不稳定 | Vector API incubator（明确 SIMD） |
| JDK 17 大对象池化 | GC 已够快 | 不池化，逃逸分析消除 |
| JDK 17 用 Lombok @Data | 部分场景反射 | Records（编译器生成） |
| JDK 17 `instanceof` 链 + cast | 多次类型检查 | sealed + pattern matching |
| JDK 17 堆外内存用 `Unsafe` | 不安全 | Foreign Memory API（incubator） |
