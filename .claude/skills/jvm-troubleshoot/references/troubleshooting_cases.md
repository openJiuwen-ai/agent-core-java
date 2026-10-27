# Real Troubleshooting Cases

This file supplements the failure troubleshooting in SKILL.md with 4 end-to-end cases, each containing "symptom -> diagnosis -> root cause -> fix". Read as needed when a user asks "give me a troubleshooting case" or "how to locate this type of failure".

## Case 1: Heap Memory Leak (Static Map Continuously Growing)

### Symptom

Application becomes slow after running for 3 days, eventually reports `OutOfMemoryError: Java heap space` and restarts. After restart it recovers, but OOMs again after 3 days.

### Diagnosis

**1. Get dump** (application had `-XX:+HeapDumpOnOutOfMemoryError` enabled):
```bash
ls -lh /var/log/dumps/  # Find java_pid12345.hprof
```

**2. MAT analysis**:
- Open Leak Suspects Report -> report shows `java.util.HashMap` occupying 1.8GB (45% of heap)
- Check Dominator Tree -> find the largest HashMap, its GC Root is the `static` field of `com.example.cache.UserCache`

**3. Confirm growth** (observe after restart):
```bash
jmap -histo:live <pid> | grep -i hashmap | head
# Check again after 1 day, HashMap instance count and bytes keep growing
```

### Root Cause

`UserCache` is a static HashMap; business code `put`s into it but never `remove`s; the cache has no eviction policy:
```java
public class UserCache {
    private static final Map<String, User> CACHE = new HashMap<>();  // Never evicts
    public static void put(String id, User u) { CACHE.put(id, u); }  // Only put, no remove
}
```

### Fix

**Temporary stopgap**: Restart, or add a cache limit:
```java
private static final Map<String, User> CACHE =
    new LinkedHashMap<String, User>(1000, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, User> eldest) {
            return size() > 10000;  // Limit of 10,000
        }
    };
```

**Root fix**: Replace hand-written cache with Caffeine / Guava Cache, configure TTL and maximum size:
```java
private static final Cache<String, User> CACHE = Caffeine.newBuilder()
    .maximumSize(10_000)
    .expireAfterWrite(1, TimeUnit.HOURS)
    .build();
```

---

## Case 2: CPU 100% (Regex Catastrophic Backtracking)

### Symptom

Endpoint `/api/validate` suddenly hits CPU 100%, response times out. Other endpoints are normal. After restart it temporarily recovers, but hitting that endpoint again causes 100% CPU.

### Diagnosis

**1. Find process**:
```bash
top  # PID 12345 using 99% CPU
```

**2. Find thread**:
```bash
top -Hp 12345
# TID 12350 using 95% CPU
printf "%x\n" 12350  # Output: 305e
```

**3. Check thread stack**:
```bash
jstack 12345 | grep 305e -A 30
```

Output:
```
"http-nio-8080-exec-5" #25 daemon prio=5
   at java.util.regex.Pattern$Curly.match0(Pattern.java:4274)
   at java.util.regex.Pattern$GroupHead.match(Pattern.java:4660)
   at java.util.regex.Pattern$Branch.match(Pattern.java:4604)
   ...
   at java.util.regex.Matcher.matches(Matcher.java:249696)
   at com.example.Validator.validate(Validator.java:25)
```

**4. Check regex**:
```java
// Validator.java:25
String EMAIL_REGEX = "^([a-zA-Z0-9_\\-\\.]+)@([a-zA-Z0-9_\\-\\.]+)\\.([a-zA-Z]{2,5})$";
// Input "aaaaaa...aaaaaa@bbb" (very long prefix) triggers catastrophic backtracking
```

### Root Cause

A regex like `([a-zA-Z0-9_\-\.]+)+` with nested quantifiers (`(...+)+`) causes catastrophic backtracking. When given a very long input string, the regex engine tries all possible combinations, resulting in exponential time complexity.

### Fix

**Temporary stopgap**: Limit input length:
```java
if (input.length() > 100) throw new IllegalArgumentException("input too long");
```

**Root fix**: Simplify the regex, remove nested quantifiers:
```java
// Use atomic groups (not supported in JDK, use alternative) or simplify
String EMAIL_REGEX = "^[a-zA-Z0-9_.+-]+@[a-zA-Z0-9-]+\\.[a-zA-Z]{2,}$";
// Or use pre-compiled + timeout protection
Pattern EMAIL = Pattern.compile("^[a-zA-Z0-9_.+-]+@[a-zA-Z0-9-]+\\.[a-zA-Z]{2,}$");
```

**Additional**: Use RE2J (linear-time regex library) instead of `java.util.regex` to completely avoid catastrophic backtracking.

---

## Case 3: Thread Deadlock (Inconsistent Nested synchronized Lock Ordering)

### Symptom

All application requests time out, but CPU usage is 0%. `/health` endpoint also times out. Appears as if the application is frozen.

### Diagnosis

**1. Check thread state distribution**:
```bash
jstack <pid> > thread.txt
grep "java.lang.Thread.State" thread.txt | sort | uniq -c
```

Output:
```
  120 BLOCKED
   30 WAITING
    5 RUNNABLE
```

Many BLOCKED threads indicate lock contention.

**2. Find deadlock**:
```bash
grep -A 20 "Found Java deadlock" thread.txt
```

Output:
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

**3. Check code**:
```java
public void transfer(Account from, Account to, BigDecimal amount) {
    synchronized (from) {        // Lock from first
        synchronized (to) {       // Then lock to
            // Transfer
        }
    }
}
// Thread 1: transfer(A, B) -> locks A, waits for B
// Thread 2: transfer(B, A) -> locks B, waits for A
```

### Root Cause

Nested `synchronized` with inconsistent lock ordering. Thread 1 locks A->B, Thread 2 locks B->A, each waiting for the other.

### Fix

**Root fix**: Unify lock ordering (sort by hashCode or id):
```java
public void transfer(Account a, Account b, BigDecimal amount) {
    // Sort by id, guarantee always lock the one with smaller id first
    Account first = a.getId() < b.getId() ? a : b;
    Account second = a.getId() < b.getId() ? b : a;
    synchronized (first) {
        synchronized (second) {
            // Transfer
        }
    }
}
```

**Alternative**: Use `ReentrantLock.tryLock(timeout)`:
```java
if (fromLock.tryLock(1, TimeUnit.SECONDS) && toLock.tryLock(1, TimeUnit.SECONDS)) {
    try { /* Transfer */ } finally { toLock.unlock(); fromLock.unlock(); }
}
```

---

## Case 4: Metaspace Leak (CGLIB Dynamic Proxy)

### Symptom

Application has Full GC every few hours, eventually reports `OutOfMemoryError: Metaspace`. Recovers after restart, but the cycle repeats.

### Diagnosis

**1. Confirm Metaspace growth**:
```bash
jstat -gccause <pid> 1000
# M column keeps growing, does not drop after Full GC
# GCC column shows "Metadata GC Threshold"
```

**2. Check ClassLoader**:
```bash
jmap -clstats <pid>
# Same business class loaded by dozens of different ClassLoaders
# ClassLoader count keeps growing
```

**3. Find the source**:

Check where the code uses CGLIB or ByteBuddy to generate proxy classes:
```java
// Generates a new proxy class on every call, no caching
public Object createProxy(Object target) {
    Enhancer enhancer = new Enhancer();
    enhancer.setSuperclass(target.getClass());
    enhancer.setCallback(new MyInterceptor());
    return enhancer.create();  // Generates a new Class each time!
}
```

### Root Cause

CGLIB `Enhancer.create()` generates a new Class object on every call, stored in Metaspace. Without caching, frequent calls cause Metaspace to grow continuously.

### Fix

**Temporary stopgap**: Increase Metaspace:
```
-XX:MaxMetaspaceSize=1g  # Temporarily increase, symptomatic not root fix
```

**Root fix**: Cache proxy classes:
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

**Or**: Use JDK dynamic proxy (`Proxy.newProxyInstance`) instead of CGLIB; JDK proxy reuses the same Class for identical interfaces.

## General Troubleshooting Workflow for All Cases

Regardless of the failure type, the general approach is:

1. **Confirm symptom**: What is the application behavior? What is the error message?
2. **Process level**: `top` / `jps` to confirm process status
3. **Thread level**: `top -Hp` + `jstack` to find abnormal threads
4. **Heap/GC level**: `jstat` + `jmap` to check memory and GC
5. **Locate code**: Find the business code line from the abnormal stack
6. **Root cause**: Understand why this is happening (examine code logic)
7. **Stop the bleeding**: Restart / adjust parameters / temporary limits
8. **Root fix**: Change code / add caching / add limits / change implementation
