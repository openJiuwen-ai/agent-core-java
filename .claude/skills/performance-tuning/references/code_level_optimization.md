# Code-Level Performance Optimization

This file supplements SKILL.md's code-level performance content, providing in-depth coverage of collections/Stream/reflection/locks/escape analysis details. Read on demand when users ask "is this coding pattern fast" or "why is this code slow".

## Collection Selection Details

### List Selection

| Implementation | Underlying | Random Access | Head Insert | Tail Insert | Middle Insert | Memory |
|---|---|---|---|---|---|---|
| `ArrayList` | Array | O(1) | O(n) | Amortized O(1) | O(n) | Compact |
| `LinkedList` | Doubly-linked list | O(n) | O(1) | O(1) | O(1) (known position) | Large (node overhead) |
| `CopyOnWriteArrayList` | Array (copy-on-write) | O(1) | O(n) | O(n) | O(n) | Doubles on write |
| `ArrayDeque` | Circular array | O(1) | O(1) | O(1) | O(n) | Compact |

**Selection decision**:
- **Default to `ArrayList`**: Best for 90% of scenarios
- **Frequent head insert/delete**: `ArrayDeque` (use as List or as Deque)
- **Very few writes, many reads**: `CopyOnWriteArrayList`
- **Don't use `LinkedList`**: In 99% of scenarios it's worse than `ArrayList` or `ArrayDeque`; the only fit is "frequent middle insert at known position where array shifting is unacceptable"

**ArrayList performance details**:
- `new ArrayList<>()` initial capacity 10, first add triggers resize to 10
- Resize factor 1.5x: `oldCapacity + (oldCapacity >> 1)`
- Known size: `new ArrayList<>(expectedSize)` avoids multiple resizes
- `subList()` returns a view; modifications affect the original List; using subList after structural changes to the original List throws `ConcurrentModificationException`

### Map Selection

| Implementation | Underlying | null key | null val | Concurrent | Notes |
|---|---|---|---|---|---|
| `HashMap` | Array + linked list + red-black tree | Yes | Yes | No | Default |
| `LinkedHashMap` | HashMap + doubly-linked list | Yes | Yes | No | Order preservation / LRU |
| `TreeMap` | Red-black tree | No | Yes | No | Sorted |
| `ConcurrentHashMap` | CAS + synchronized per segment | No | No | Yes | High concurrency |
| `EnumMap` | Array | No | Yes | No | Fastest for enum keys |
| `IdentityHashMap` | Array (== comparison) | Yes | Yes | No | Reference equality |

**HashMap key parameters**:
- `loadFactor` default 0.75, space/time balance point
- `initialCapacity`: default 16; for known size use `new HashMap<>(expectedSize / 0.75 + 1)` to avoid resizing
- Treeification threshold: linked list >= 8 and array >= 64 -> treeify; < 6 -> degrade to linked list

**HashMap JDK evolution**:
- JDK7: Array + linked list, hash collision -> linked list, multi-threaded resize forms cycle -> infinite loop
- JDK8: Array + linked list + red-black tree (>=8 treeify), resize splits linked list, no infinite loop
- JDK8 hash optimization: high 16 bits XOR low 16 bits `(h ^ (h >>> 16))`, reduces collisions

**ConcurrentHashMap evolution**:
- JDK7: Segment locks (default 16 segments), concurrency = 16
- JDK8: CAS + synchronized per bucket (array element), concurrency = number of buckets
- JDK8+ treeification: collisions >= 8 convert to red-black tree, prevents hash collision attacks

### Set Selection

| Implementation | Underlying | Notes |
|---|---|---|
| `HashSet` | HashMap (fixed value) | Default |
| `LinkedHashSet` | LinkedHashMap | Preserves insertion order |
| `TreeSet` | TreeMap | Sorted |
| `EnumSet` | Bit vector | Fastest for enums |
| `CopyOnWriteArraySet` | CopyOnWriteArrayList | Many reads, very few writes |

### Queue Selection

| Implementation | Blocking | Bounded | Notes |
|---|---|---|---|
| `ArrayDeque` | No | No | Single-threaded first choice |
| `LinkedList` | No | No | Don't use (poor performance) |
| `PriorityQueue` | No | No | Priority queue (min-heap) |
| `ArrayBlockingQueue` | Yes | Yes | Bounded, commonly used in production |
| `LinkedBlockingQueue` | No (unbounded default) / Yes | No / Yes | Executors default |
| `ConcurrentLinkedQueue` | No | No | Lock-free CAS, high concurrency |
| `DelayQueue` | Yes | No | Delayed tasks |

**Thread pool queue selection**:
- Default `LinkedBlockingQueue` unbounded -> task accumulation OOM
- Bounded `ArrayBlockingQueue` -> paired with rejection policy
- Priority `PriorityBlockingQueue` -> tasks have priorities

### Large Collection Pitfalls

**Don't load large data into memory all at once**:
- Million-level data -> use cursor/pagination/Stream
- DB queries use `LIMIT` + `OFFSET` or cursor
- File processing uses `BufferedReader` / `Stream<String>`

**Collection iteration removal**:
- Don't use `for + remove` -> `ConcurrentModificationException`
- Use `iterator.remove()` or `list.removeIf(pred)` (JDK8+)

## Stream Performance Details

### Stream Is Not a Performance Optimization Tool

Stream's design goal is **readability + parallelization**, not performance. Simple iteration with Stream is mostly slower than for:

| Scenario | for | Stream | parallelStream |
|---|---|---|---|
| Small collection (< 1000) simple iteration | 1x | 1.2-2x slower | 5-10x slower |
| Large collection (> 10000) simple iteration | 1x | 1.1-1.5x slower | Depends |
| Large collection CPU-intensive transform | 1x | Comparable | May be faster |
| Large collection with IO | 1x | Comparable | **May be faster** |

**Reasons for being slow**:
- Boxing overhead (`Stream<Integer>` vs `IntStream`)
- Iterator/Consumer indirect invocation
- Stream creation overhead
- `parallelStream` ForkJoinPool splitting overhead

### Correct Stream Usage

**Use primitive streams to avoid boxing**:
```java
// Slow: boxed
list.stream().mapToInt(Integer::intValue).sum();

// Still slower than for, but faster than boxed stream
IntStream.range(0, list.size()).map(i -> list.get(i)).sum();
```

**Use collector instead of manual accumulation**:
```java
// Slow: string concatenation
list.stream().map(...).collect(StringBuilder::new, StringBuilder::append, StringBuilder::append);

// Fast
list.stream().map(...).collect(Collectors.joining(","));
```

**Correct groupingBy usage**:
```java
// Simple grouping
Map<Key, List<Item>> byKey = items.stream().collect(groupingBy(Item::key));

// Downstream collector reduces boxing
Map<Key, Long> countByKey = items.stream().collect(groupingBy(Item::key, counting()));

// Concurrent grouping
Map<Key, List<Item>> byKey = items.parallelStream().collect(groupingByConcurrent(Item::key));
```

### When to Use parallelStream

**Conditions for using parallelStream**:
- Data volume > 10000
- CPU-intensive (no IO)
- Tasks have no state dependencies
- Per-element processing time > 1us

**Anti-patterns**:
- `list.parallelStream().forEach(x -> db.save(x))` -> DB is the bottleneck, parallel is slower and overwhelms DB
- `list.parallelStream().filter(...).findFirst()` -> Sequential result, parallel is pointless
- Small collection parallelStream -> ForkJoin splitting overhead > benefit
- Using `ThreadLocal` inside `parallelStream` -> Thread switching loses context

**parallelStream shared ForkJoinPool**:
- All `parallelStream` share commonPool (CPU cores - 1)
- One task stuck -> entire JVM's parallel streams affected
- Custom pool: `ForkJoinPool custom = new ForkJoinPool(N); custom.submit(() -> list.parallelStream()...).get()`

## Reflection Optimization Details

### Why Reflection Is Slow

- Method table lookup before invocation
- Boxing/unboxing
- Parameter checking (unless `setAccessible(true)`)
- Security manager checks

### Optimization Path

**Level 0: Cache Method/Field objects**

```java
// Slow: reflective lookup each time
for (Item item : items) {
    Method m = item.getClass().getMethod("getName");
    String name = (String) m.invoke(item);
}

// Fast: cache
private static final Method GET_NAME = initMethod();
private static Method initMethod() {
    try {
        Method m = Item.class.getMethod("getName");
        m.setAccessible(true);  // Disable access checks
        return m;
    } catch (NoSuchMethodException e) { throw new RuntimeException(e); }
}
// Usage
String name = (String) GET_NAME.invoke(item);
```

**Level 1: setAccessible(true)**

JDK9+ module system requires `--add-opens` to access non-exported packages, otherwise `InaccessibleObjectException`.

**Level 2: MethodHandle**

```java
MethodHandles.Lookup lookup = MethodHandles.lookup();
MethodHandle mh = lookup.findVirtual(String.class, "length", MethodType.methodType(int.class));
int len = (int) mh.invoke("hello");  // Close to direct call
```

**Level 3: LambdaMetafactory**

Convert MethodHandle to `Function` Lambda, no reflection overhead at runtime:

```java
MethodHandles.Lookup lookup = MethodHandles.lookup();
MethodHandle mh = lookup.findVirtual(Item.class, "getName", MethodType.methodType(String.class));
Function<Item, String> getName = (Function<Item, String>) LambdaMetafactory.metafactory(
    lookup, "apply", MethodType.methodType(Function.class),
    MethodType.methodType(Object.class, Object.class),
    mh, MethodType.methodType(String.class, Item.class)
).getTarget().invoke();
// Usage: close to direct call
String name = getName.apply(item);
```

**Level 4: Bytecode generation**

- ByteBuddy / CGLIB: Generate subclass at runtime, invocation goes through direct bytecode
- One-time generation cost is high, invocation is extremely fast
- Suitable for frameworks (Spring AOP, Hibernate)

### Reflection Anti-patterns

- Reflective Method lookup on hot path each time -> cache it
- Reflective call to private without `setAccessible(true)` -> security check overhead
- JDK9+ without `--add-opens` -> module access failure

### Alternatives to Reflection

- **Records** (JDK14+): Auto-generated accessors, no reflection
- **Sealed class + pattern matching** (JDK17): Types known at compile time, no reflection
- **VarHandle** (JDK9+): Replaces `Field.set/get` for volatile/ordered access

## Lock Optimization Details

### 4 Levels of Lock Optimization

**Level 1: Lock Elision**

JIT determines through escape analysis that an object doesn't escape the method/thread -> auto-eliminates synchronized:

```java
// JIT determines sb doesn't escape -> lock elision
public String concat(String a, String b) {
    StringBuffer sb = new StringBuffer();  // Local, doesn't escape
    sb.append(a).append(b);
    return sb.toString();
}
```

**Trigger condition**: Escape analysis enabled `-XX:+DoEscapeAnalysis` (enabled by default since JDK8)

**Level 2: Lock Coarsening**

JIT merges adjacent synchronized blocks:

```java
// Before optimization: lock each iteration
for (int i = 0; i < n; i++) {
    synchronized(lock) { ... }
}

// After lock coarsening: merged outside loop
synchronized(lock) {
    for (int i = 0; i < n; i++) { ... }
}
```

**Trigger condition**: JIT determines loop's synchronized blocks can be merged.

**Level 3: Lock Granularity Optimization**

```java
// Coarse-grained: one lock for entire map
synchronized(map) { ... }

// Fine-grained: segmented locks (JDK7 ConcurrentHashMap)
// Finer: CAS + bucket-level synchronized (JDK8+ ConcurrentHashMap)
```

**Level 4: Lock-free**

- Counters: `LongAdder` / `AtomicLong`
- References: `AtomicReference` / `VarHandle`
- Parallel computation: `ForkJoinPool` / `CompletableFuture`

### synchronized vs ReentrantLock

| Dimension | synchronized | ReentrantLock |
|---|---|---|
| Performance | JDK6+ comparable | Slightly slower (except under high contention) |
| Interruptible | No | `lockInterruptibly()` |
| Timeout | No | `tryLock(timeout)` |
| Fairness | Unfair | Optional fair |
| Condition queues | 1 (wait/notify) | Multiple `Condition` |
| Lock release | Automatic (exit block) | Must `finally` explicit unlock |

**Selection**:
- Simple mutual exclusion -> `synchronized`
- Need timeout/interrupt/multiple conditions -> `ReentrantLock`
- Don't switch to `ReentrantLock` just for "performance"; JDK6+ synchronized is well-optimized

### StampedLock

Added in JDK8, optimistic read lock; faster than `ReentrantReadWriteLock` for read-many-write-few scenarios.

```java
StampedLock sl = new StampedLock();
long stamp = sl.tryOptimisticRead();  // Optimistic read, no CAS
int x = currentX;
int y = currentY;
if (!sl.validate(stamp)) {  // Validate if written during read
    stamp = sl.readLock();  // Upgrade to pessimistic read on failure
    try { x = currentX; y = currentY; }
    finally { sl.unlockRead(stamp); }
}
// Use x, y
```

**Notes**:
- Not reentrant! Same thread calling `readLock()` again will deadlock
- Optimistic read suits "many reads, very few writes"
- Write lock is exclusive, mutually exclusive with read lock

### LongAdder vs AtomicLong

| Scenario | AtomicLong | LongAdder |
|---|---|---|
| Low contention | Fast | Slightly slower (more fields) |
| High contention | Slow (CAS retries) | 5-10x faster (striped accumulation) |
| Memory | 1 long | Multiple Cells + base |

**Principle**: LongAdder splits a single counter into multiple Cells; threads disperse to different Cells for CAS, then `sum()` accumulates. The more contention, the faster.

**Scenario**: High-concurrency counters, statistics, rate limiting -> `LongAdder`

### Lock Optimization Key Points

**Reduce lock holding time**:
```java
// Slow: hold lock for entire method
synchronized(lock) {
    data = loadData();  // IO slow, but holding lock
    process(data);
}

// Fast: reduce lock scope
data = loadData();  // Not holding lock
synchronized(lock) {
    process(data);
}
```

**Lock separation**:
```java
// One big lock
synchronized(lock) { readA(); writeB(); }

// Separate into two locks
synchronized(lockA) { readA(); }
synchronized(lockB) { writeB(); }
```

**Avoid hotspot fields**:
- `AtomicInteger` increment under multi-core CPU high contention -> use `LongAdder`

## Escape Analysis and Stack Allocation

### Escape Analysis Levels

JIT determines through Escape Analysis whether an object escapes:

| Level | Description | JIT Behavior |
|---|---|---|
| NoEscape | Doesn't escape | Stack allocation + scalar replacement -> no heap, no GC |
| ArgEscape | Only passed as argument to callee | Heap allocation, but not visible to caller |
| MethodEscape | Escapes the method | Heap allocation |
| ThreadEscape | Escapes to other threads | Heap allocation, need to consider visibility |

### Scalar Replacement

Object doesn't escape -> JIT splits object fields into independent local variables:

```java
// Before optimization
class Point { int x, y; }
int sum() {
    Point p = new Point(1, 2);
    return p.x + p.y;
}

// After scalar replacement
int sum() {
    int x = 1, y = 2;  // Object disappears
    return x + y;
}
```

**Benefit**: Object doesn't enter heap, no GC pressure, no memory allocation overhead.

### Helping Escape Analysis

- Don't return `new` small objects on hot paths (encourage reuse)
- Don't assign temporary objects to static fields
- Don't create large objects in loops
- Don't let local objects escape into lambda/anonymous inner classes

### Anti-patterns

- `new` temporary objects on hot path each time -> escape analysis may eliminate, but don't rely on it
- Putting objects into ThreadLocal on hot path -> escapes to thread
- Putting objects into collections on hot path -> escapes

### Confirming JIT Behavior

Confirm whether escape analysis is effective:
```bash
# Startup parameters
-XX:+DoEscapeAnalysis -XX:+PrintEscapeAnalysis -XX:+PrintInlining
```

Note: Escape analysis requires JIT warmup; short-running applications may not benefit.

## String Optimization

### StringBuilder vs +

- `+` inside loop -> compiled to `new StringBuilder().append().append()`, new instance each iteration
- Outside loop or simple concatenation -> compiler optimizes to single StringBuilder
- Complex multi-line concatenation -> explicit `StringBuilder`

```java
// Slow: + inside loop
String s = "";
for (String x : list) s += x;  // New StringBuilder each iteration

// Fast: StringBuilder outside loop
StringBuilder sb = new StringBuilder();
for (String x : list) sb.append(x);
String s = sb.toString();
```

### String Constant Pool

- `intern()` puts into constant pool, same strings are reused
- JDK7+ constant pool is in heap, won't cause PermGen/Metaspace OOM
- Don't overuse `intern()`, may bloat the constant pool
- Comparison: `==` is unreliable, use `equals()`

### String Encoding

- `String` internally UTF-16, 2 bytes per character
- ASCII strings use `byte[]` + Latin-1 encoding (JDK9+ Compact Strings)
- For heavy ASCII string processing, consider direct `byte[]` manipulation

## Object Pooling and Reuse

### Don't Overuse Object Pools

JDK 17's young GC is very fast; allocation + reclamation is cheaper than pool maintenance overhead.

| Scenario | Use Pool | Don't Use Pool |
|---|---|---|
| Regular POJO | No | Yes |
| JDBC Connection | Yes | |
| Thread | Yes (ThreadPool) | |
| Large object (> few KB) | Depends | |
| Buffer / ByteBuffer | Yes (Netty Pool) | |

**Judgment**: Only use pools when allocation overhead > pool maintenance overhead. In most scenarios GC is faster than pools.

### ThreadLocal Reuse

```java
private static final ThreadLocal<SimpleDateFormat> TL = ThreadLocal.withInitial(
    () -> new SimpleDateFormat("yyyy-MM-dd")
);
// Reuse
TL.get().format(date);
```

Note: In thread pool scenarios, ThreadLocal must call `remove()`, otherwise data persists after thread reuse.

**JDK 17 alternative**: Use `DateTimeFormatter` (thread-safe, no ThreadLocal needed):
```java
private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
String s = FMT.format(LocalDate.now());
```

## JDK 17 New Features (Performance-Related)

### Records (JDK 14+ stable)

Compiler auto-generates equals/hashCode/toString, faster than reflection or Lombok:

```java
public record Point(int x, int y) {}
```

**Performance benefits**:
- No reflection overhead
- Compiler-generated `equals`/`hashCode` is faster than Lombok `@Data` (Lombok uses reflection in some scenarios)
- Immutable -> can be eliminated by escape analysis

**Applicable to**: DTOs, value objects, configuration classes.

### Sealed Class (JDK 17 stable)

Compile-time exhaustive matching, helps JIT devirtualization:

```java
public sealed interface Shape
    permits Circle, Rectangle, Triangle {}

public final class Circle implements Shape { ... }
public final class Rectangle implements Shape { ... }
public final class Triangle implements Shape { ... }

// Compile-time exhaustive
public double area(Shape s) {
    return switch (s) {
        case Circle c -> Math.PI * c.r() * c.r();
        case Rectangle r -> r.w() * r.h();
        case Triangle t -> t.b() * t.h() / 2;
    };
}
```

**Performance benefits**:
- All implementations known at compile time -> JIT monomorphic/bimorphic inlining
- Pattern matching is faster than `instanceof` chains
- Switch uses `tableswitch` (jump table), faster than `lookupswitch`

### Module System and --add-opens (JDK 17 Must-Know)

JDK 9+ module system; reflective access to non-exported packages requires `--add-opens`:

```
--add-opens java.base/java.lang=ALL-UNNAMED
--add-opens java.base/java.util=ALL-UNNAMED
--add-opens java.base/java.nio=ALL-UNNAMED
```

**Error keyword**: `InaccessibleObjectException` -> missing `--add-opens`.

**Typical scenarios**:
- Spring / Hibernate / ByteBuddy reflection -> need `--add-opens`
- Netty accessing NIO internals -> `--add-opens java.base/java.nio`
- `Unsafe` or reflection accessing `String.value` -> `--add-opens java.base/java.lang`

### Foreign Memory Access API (incubator)

JDK 17 incubator (`--add-modules jdk.incubator.foreign`), official replacement for off-heap memory access, replacing `ByteBuffer.allocateDirect` + `sun.misc.Unsafe`:

```java
import jdk.incubator.foreign.MemorySegment;
import jdk.incubator.foreign.ResourceScope;

try (ResourceScope scope = ResourceScope.newConfinedScope()) {
    MemorySegment seg = MemorySegment.allocateNative(100, scope);
    seg.set(ValueLayout.JAVA_INT, 0, 42);
    int val = seg.get(ValueLayout.JAVA_INT, 0);
}
```

**Benefits**:
- Faster than `ByteBuffer.allocateDirect` (no cleaner dependency)
- Safer than `Unsafe` (has bounds checking)
- Automatic release (ResourceScope)

**Note**: JDK 17 incubator, API may change. JDK 19+ stable.

### Vector API (incubator)

JDK 17 incubator (`--add-modules jdk.incubator.vector`), SIMD syntax, replacing hand-written loop vectorization:

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

**Benefit**: Explicit SIMD, more stable than C2 auto-vectorization. JDK 17 still incubator.

**Note**: JDK 17 incubator, API may change. JDK 19+ preview.

### Virtual Threads (JDK 21+, Not Available in JDK 17)

**Important**: Virtual threads are only stable in JDK 21+, not available in JDK 17.

JDK 17 alternatives: Use `CompletableFuture` / `Reactor` / `RxJava` for async orchestration.

After upgrading to JDK 21+:
```java
// JDK 21+
Thread.startVirtualThread(() -> { ... });

try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    IntStream.range(0, 10000).forEach(i ->
        executor.submit(() -> { handleRequest(i); return null; })
    );
}
```

**Benefits**: Million-level threads, 5-10x throughput for IO-intensive scenarios.
**Limitations**: No benefit for CPU-intensive; synchronized holding pins thread (JDK 21 solution: use `ReentrantLock`).

## Common Anti-Pattern Quick Reference

| Anti-Pattern | Problem | Correct Approach |
|---|---|---|
| `new` large object in loop | Frequent GC | Move outside loop, or use object pool |
| `LinkedList` as List | Poor performance | `ArrayList` |
| `HashMap` + `synchronized` | Coarse lock granularity | `ConcurrentHashMap` |
| `Vector` / `Hashtable` | All-method locking | `CopyOnWriteArrayList` / `ConcurrentHashMap` |
| Stream for simple iteration | Boxing overhead | for loop |
| `parallelStream` with IO inside | Blocks shared ForkJoinPool | Async + custom pool |
| Reflection without caching Method | Reflection overhead | static final cache |
| Shared `SimpleDateFormat` | Not thread-safe | ThreadLocal or `DateTimeFormatter` |
| `+` string concatenation in loop | New StringBuilder each time | Explicit StringBuilder |
| `==` for string comparison | Unreliable | `equals()` |
| JDK 17 reflection without `--add-opens` | `InaccessibleObjectException` | Add `--add-opens` JVM parameter |
| JDK 17 shared `SimpleDateFormat` | Not thread-safe | `DateTimeFormatter` (thread-safe) |
| JDK 17 waiting for JIT auto-vectorization | Unstable | Vector API incubator (explicit SIMD) |
| JDK 17 pooling large objects | GC is fast enough | Don't pool; escape analysis eliminates |
| JDK 17 using Lombok @Data | Reflection in some scenarios | Records (compiler-generated) |
| JDK 17 `instanceof` chain + cast | Multiple type checks | sealed + pattern matching |
| JDK 17 off-heap memory with `Unsafe` | Unsafe | Foreign Memory API (incubator) |
