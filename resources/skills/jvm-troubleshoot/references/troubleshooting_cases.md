# 真实排查案例

本文件补充 SKILL.md 的故障排查，给 4 个端到端案例，每个含"现象 → 诊断 → 根因 → 修复"。用户问"给我一个排查案例"或"这类故障怎么定位"时按需读取。

## 案例 1：堆内存泄漏（静态 Map 持续增长）

### 现象

应用运行 3 天后响应变慢，最终报 `OutOfMemoryError: Java heap space` 重启。重启后恢复正常，但 3 天后再次 OOM。

### 诊断

**1. 拿 dump**（应用加了 `-XX:+HeapDumpOnOutOfMemoryError`）：
```bash
ls -lh /var/log/dumps/  # 找到 java_pid12345.hprof
```

**2. MAT 分析**：
- 打开 Leak Suspects Report → 报告显示 `java.util.HashMap` 占 1.8GB（占堆 45%）
- 看 Dominator Tree → 找到最大的 HashMap，它的 GC Root 是 `com.example.cache.UserCache` 的 `static` 字段

**3. 确认增长**（重启后观察）：
```bash
jmap -histo:live <pid> | grep -i hashmap | head
# 运行 1 天后再看，HashMap 实例数和 bytes 持续增长
```

### 根因

`UserCache` 是个静态 HashMap，业务代码 `put` 进去后从不 `remove`，缓存无淘汰策略：
```java
public class UserCache {
    private static final Map<String, User> CACHE = new HashMap<>();  // 永不淘汰
    public static void put(String id, User u) { CACHE.put(id, u); }  // 只 put 不 remove
}
```

### 修复

**临时止血**：重启，或加缓存上限：
```java
private static final Map<String, User> CACHE =
    new LinkedHashMap<String, User>(1000, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, User> eldest) {
            return size() > 10000;  // 上限 1 万
        }
    };
```

**根治**：用 Caffeine / Guava Cache 替代手写缓存，配置 TTL 和最大容量：
```java
private static final Cache<String, User> CACHE = Caffeine.newBuilder()
    .maximumSize(10_000)
    .expireAfterWrite(1, TimeUnit.HOURS)
    .build();
```

---

## 案例 2：CPU 100%（正则回溯爆炸）

### 现象

接口 `/api/validate` 突然 CPU 100%，响应超时。其他接口正常。重启后暂时恢复，但访问该接口后又 100%。

### 诊断

**1. 找进程**：
```bash
top  # PID 12345 占 CPU 99%
```

**2. 找线程**：
```bash
top -Hp 12345
# TID 12350 占 CPU 95%
printf "%x\n" 12350  # 输出 305e
```

**3. 看线程栈**：
```bash
jstack 12345 | grep 305e -A 30
```

输出：
```
"http-nio-8080-exec-5" #25 daemon prio=5
   at java.util.regex.Pattern$Curly.match0(Pattern.java:4274)
   at java.util.regex.Pattern$GroupHead.match(Pattern.java:4660)
   at java.util.regex.Pattern$Branch.match(Pattern.java:4604)
   ...
   at java.util.regex.Matcher.matches(Matcher.java:2496)
   at com.example.Validator.validate(Validator.java:25)
```

**4. 看正则**：
```java
// Validator.java:25
String EMAIL_REGEX = "^([a-zA-Z0-9_\\-\\.]+)@([a-zA-Z0-9_\\-\\.]+)\\.([a-zA-Z]{2,5})$";
// 输入 "aaaaaa...aaaaaa@bbb"（超长前缀）触发回溯爆炸
```

### 根因

正则 `([a-zA-Z0-9_\-\.]+)+` 这种嵌套量词（`(...+)+`）会导致回溯爆炸。输入超长字符串时，正则引擎尝试所有可能组合，时间复杂度指数级。

### 修复

**临时止血**：限制输入长度：
```java
if (input.length() > 100) throw new IllegalArgumentException("input too long");
```

**根治**：简化正则，去掉嵌套量词：
```java
// 用原子组（JDK 不支持，换写法）或简化
String EMAIL_REGEX = "^[a-zA-Z0-9_.+-]+@[a-zA-Z0-9-]+\\.[a-zA-Z]{2,}$";
// 或用预编译 + 超时保护
Pattern EMAIL = Pattern.compile("^[a-zA-Z0-9_.+-]+@[a-zA-Z0-9-]+\\.[a-zA-Z]{2,}$");
```

**额外**：用 RE2J（线性时间正则库）替代 `java.util.regex`，彻底避免回溯爆炸。

---

## 案例 3：线程死锁（synchronized 嵌套锁顺序不一致）

### 现象

应用所有请求超时，但 CPU 占用 0%。`/health` 接口也超时。看起来像应用卡死。

### 诊断

**1. 看线程状态分布**：
```bash
jstack <pid> > thread.txt
grep "java.lang.Thread.State" thread.txt | sort | uniq -c
```

输出：
```
  120 BLOCKED
   30 WAITING
    5 RUNNABLE
```

大量 BLOCKED 说明锁竞争。

**2. 找死锁**：
```bash
grep -A 20 "Found Java deadlock" thread.txt
```

输出：
```
Found 1 deadlock.
====================
"transfer-thread-1" deadlock waiting to lock <0x000000076b4f01e0>
    at com.example.AccountService.transfer(AccountService.java:50)
    - waiting to lock <0x000000076b4f01e0>
    - locked <0x000000076b4f0230>
"transfer-thread-2" deadlock waiting to lock <0x000000076b4f0230>
    at com.example.AccountService.transfer(AccountService.java:50)
    - waiting to lock <0x000000076b4f0230>
    - locked <0x000000076b4f01e0>
```

**3. 看代码**：
```java
public void transfer(Account from, Account to, BigDecimal amount) {
    synchronized (from) {        // 先锁 from
        synchronized (to) {       // 再锁 to
            // 转账
        }
    }
}
// 线程 1: transfer(A, B) → 锁 A，等 B
// 线程 2: transfer(B, A) → 锁 B，等 A
```

### 根因

嵌套 `synchronized` 锁顺序不一致。线程 1 锁 A→B，线程 2 锁 B→A，互相等待。

### 修复

**根治**：统一锁顺序（按 hashCode 或 id 排序）：
```java
public void transfer(Account a, Account b, BigDecimal amount) {
    // 按 id 排序，保证总是先锁 id 小的
    Account first = a.getId() < b.getId() ? a : b;
    Account second = a.getId() < b.getId() ? b : a;
    synchronized (first) {
        synchronized (second) {
            // 转账
        }
    }
}
```

**替代**：用 `ReentrantLock.tryLock(timeout)`：
```java
if (fromLock.tryLock(1, TimeUnit.SECONDS) && toLock.tryLock(1, TimeUnit.SECONDS)) {
    try { /* 转账 */ } finally { toLock.unlock(); fromLock.unlock(); }
}
```

---

## 案例 4：Metaspace 泄漏（CGLIB 动态代理）

### 现象

应用每隔几小时 Full GC，最终报 `OutOfMemoryError: Metaspace`。重启后恢复，但循环出现。

### 诊断

**1. 确认 Metaspace 增长**：
```bash
jstat -gccause <pid> 1000
# M 列持续增长，Full GC 后不降
# GCC 列显示 "Metadata GC Threshold"
```

**2. 看 ClassLoader**：
```bash
jmap -clstats <pid>
# 同一个业务类被几十个不同的 ClassLoader 加载
# ClassLoader 数量持续增长
```

**3. 找源头**：

看代码哪里用 CGLIB 或 ByteBuddy 生成代理类：
```java
// 每次调用都生成新代理类，没缓存
public Object createProxy(Object target) {
    Enhancer enhancer = new Enhancer();
    enhancer.setSuperclass(target.getClass());
    enhancer.setCallback(new MyInterceptor());
    return enhancer.create();  // 每次生成新 Class！
}
```

### 根因

CGLIB `Enhancer.create()` 每次调用都生成新的 Class 对象，存入 Metaspace。没有缓存，频繁调用导致 Metaspace 持续增长。

### 修复

**临时止血**：调大 Metaspace：
```
-XX:MaxMetaspaceSize=1g  # 临时调大，治标不治本
```

**根治**：缓存代理类：
```java
private static final Map<Class<?>, Object> PROXY_CACHE = new ConcurrentHashMap<>();

public Object createProxy(Object target) {
    return PROXY_CACHE.computeIfAbsent(target.getClass(), clazz -> {
        Enhancer enhancer = new Enhancer();
        enhancer.setSuperclass(clazz);
        enhancer.setCallback(new MyInterceptor());
        return enhancer.create();
    });
}
```

**或**：用 JDK 动态代理（`Proxy.newProxyInstance`）替代 CGLIB，JDK 代理对相同接口会复用 Class。

## 排查案例的通用流程

不管哪种故障，大体都是：

1. **现象确认**：应用什么表现？错误信息是什么？
2. **进程级**：`top` / `jps` 确认进程状态
3. **线程级**：`top -Hp` + `jstack` 找异常线程
4. **堆/GC 级**：`jstat` + `jmap` 看内存和 GC
5. **定位代码**：从异常栈找到业务代码行
6. **根因**：理解为什么会这样（看代码逻辑）
7. **止血**：重启 / 调参 / 临时限制
8. **根治**：改代码 / 加缓存 / 加上限 / 换实现
