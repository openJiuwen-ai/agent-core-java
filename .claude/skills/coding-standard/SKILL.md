---
name: coding-standard
description: Huawei CodeArts Check Java coding standards (full reference). Invoke when you need detailed rule descriptions, OK/BAD code examples, or extended rules (concurrency, SQL, collections, performance). Use /coding-standard to load. Keywords: G.NAM, G.ERR, G.CON, G.LOG, G.FMT, code review, rule details, code example. Not applicable to: non-Java code, runtime debugging.
---

# Java Coding Standards — Full Reference

This skill includes the Java rule set from Huawei Cloud CodeArts Check service. Rule IDs follow the format `G.<prefix>.<number>`, where the prefix corresponds to a topic category. There are 102 rules in total, grouped into 18 topics, each with OK and BAD code examples.

## Engine & Category Notes

| Engine | Category | Description |
|---|---|---|
| `fixbotengine-java` | Quality | Huawei's in-house Java inspection engine, covering style/standards/performance (91 rules) |
| `codemars` | Security | Huawei security defect inspection engine (7 rules) |
| `secbrella` | Security | Huawei general security inspection engine (4 rules) |

> **Category inference**: Rules from the `codemars` / `secbrella` engines are classified as "Security"; all others are classified as "Quality". Security rules carry higher risk if violated and should be prioritized during Code Review.

## Rule Details

Each rule includes OK and BAD examples, grouped by prefix.

### G.CMT Comments

#### `G.CMT.01` Public or protected elements must have Javadoc comments

#### `G.CMT.02` Top-level public class Javadoc must include functional description + creation date/version

#### `G.CMT.03` Method Javadoc must include functional description, with @param/@return/@throws in order

#### `G.CMT.04` Do not write empty but formatted method header comments

#### `G.CMT.05` File header comments must include copyright/license information

#### `G.CMT.06` Blank line or space between comments and code; space between comment delimiter and content

#### `G.CMT.07` Production code delivered to customers should not contain TODO/FIXME

### G.COL Collections & Generics

#### `G.COL.02` Prefer generic collections over arrays

#### `G.COL.03` Use bounds to restrict available types in generic classes

#### `G.COL.04` Do not use remove/add to modify a collection inside a foreach loop

### G.CON Concurrency

#### `G.CON.01` [Security] Avoid synchronization pitfalls when accessing shared variables

Do not use: (1) synchronized on high-level concurrency objects (Lock/Condition); (2) class objects as locks (ambiguous ownership); (3) reusable objects as locks (Boolean, Integer cache, String constants); (4) public lock objects (DoS risk — use private lock); (5) instance locks to protect static shared data.

#### `G.CON.05` [Security] Do not override a thread-safe method with a non-thread-safe method

Do not override a thread-safe method with a non-thread-safe method

```java
// OK example
// Parent class synchronized method, subclass also uses synchronized
@Override public synchronized void put(String k, String v) { ... }
// BAD example
// Parent class synchronized, subclass does not add synchronized
@Override public void put(String k, String v) { ... }  // Breaks thread safety
```

#### `G.CON.06` Use new concurrency utilities instead of wait/notify

#### `G.CON.07` When creating a new thread, you must specify a thread name

#### `G.CON.08` Use Thread.setUncaughtExceptionHandler to register an uncaught exception handler

#### `G.CON.09` Do not rely on thread scheduler, thread priority, or yield() — results are non-portable

#### `G.CON.10` Thread interruption should be handled cooperatively by business code; use Thread.interrupt with caution

#### `G.CON.11` [Security] Do not use Thread.stop() to terminate a thread — it releases all locks unsafely

#### `G.CON.12` Avoid creating new threads without control; use thread pools instead

### G.CTL Control Flow

#### `G.CTL.01` Do not perform assignments or complex evaluations in control condition expressions

#### `G.CTL.02` Conditional statements with else-if branches should have an else branch at the end

#### `G.CTL.03` switch statements must have a default branch

#### `G.CTL.05` Avoid modifying the loop control variable inside the loop body

### G.DCL Declarations

#### `G.DCL.01` Declare one variable per line

#### `G.DCL.02` Declare local variables close to their first use to minimize scope

#### `G.DCL.03` C-style array declarations are prohibited (brackets after the variable name)

#### `G.DCL.04` Avoid relying on ordinal() for enum constant ordering

#### `G.DCL.05` Do not define mutable objects as constants

### G.EDV XML Security

#### `G.EDV.05` [Security] Prevent XML External Entity (XXE) attacks when parsing external XML

#### `G.EDV.06` [Security] Prevent XML Entity Expansion attacks when parsing external XML

#### `G.EDV.07` [Security] Do not use unsafe XSLT transformations on XML files

### G.ERR Exception Handling

#### `G.ERR.01` Do not ignore exceptions with an empty catch block

Do not ignore exceptions with an empty catch block

```java
// OK example
try { ... } catch (IOException e) { log.error("File read failed", e); throw new RuntimeException(e); }
// BAD example
try { ... } catch (IOException e) {}  // Exception is swallowed
```

#### `G.ERR.02` Do not catch the base exception classes Throwable, Exception, or RuntimeException directly

Do not catch Throwable/Exception/RuntimeException directly

```java
// OK example
catch (FileNotFoundException e) { ... }
// BAD example
catch (Exception e) { ... }  // Too broad
```

#### `G.ERR.03` Do not catch RuntimeExceptions that can be handled by pre-checks (e.g., NullPointerException, IndexOutOfBoundsException)

#### `G.ERR.04` [Security] Prevent leaking sensitive information through exceptions

Prevent leaking sensitive information through exceptions

```java
// OK example
catch (AuthException e) { log.error("Authentication failed", e); throw new BizException("Authentication failed"); }
// BAD example
catch (AuthException e) { throw new RuntimeException("Password error: " + password); }  // Leaks password
```

#### `G.ERR.05` Exceptions thrown by a method should match its level of abstraction

#### `G.ERR.06` [Security] When throwing a new exception in catch, pass the original exception as the cause

When throwing a new exception in catch, pass the original exception as the cause

```java
// OK example
catch (SQLException e) { throw new ServiceException("Query failed", e); }
// BAD example
catch (SQLException e) { throw new ServiceException("Query failed"); }  // Loses root cause
```

#### `G.ERR.07` A method should not throw more than 5 exceptions; document each in @throws

#### `G.ERR.08` [Security] Do not use return/break/continue/throw to cause finally to exit abnormally

Do not use return/break/continue/throw to cause finally to exit abnormally

```java
// OK example
try { return compute(); }
finally { cleanup(); }  // finally only does cleanup
// BAD example
try { return compute(); }
finally { return -1; }  // finally uses return, overriding the return value
```

#### `G.ERR.09` [Security] Do not call System.exit() to terminate the JVM

Do not call System.exit() to terminate the JVM

```java
// OK example
throw new StartupException("Cannot start, please check configuration");  // Let the caller handle it
// BAD example
System.exit(1);  // Forced termination, affects the caller
```

#### `G.ERR.10` Eliminate unchecked exceptions as much as possible; do not use SuppressWarning on an entire class

#### `G.ERR.11` GeneralSecurityException and its subclasses should be logged

### G.EXP Expressions

#### `G.EXP.01` Do not assign to the same variable more than once in a single expression

#### `G.EXP.02` Use parentheses to clarify expression evaluation order; do not rely on default precedence

#### `G.EXP.03` The 2nd and 3rd operands of a conditional expression ?: should be the same type

#### `G.EXP.04` Expression comparisons: left side tends to vary, right side tends to be constant; use equals for String comparison

#### `G.EXP.06` [Security] Assertions (assert) should not be used in code

Assertions (assert) should not be used in code

```java
// OK example
if (input == null) throw new IllegalArgumentException("input null");
// BAD example
assert input != null;  // Disabled by default in production, has no effect
```

### G.FIO File IO

#### `G.FIO.01` [Security] File paths constructed from external data must be validated and normalized before use

File paths constructed from external data must be validated and normalized before use

```java
// OK example
Path base = Paths.get("/safe/base").normalize().toAbsolutePath();
Path resolved = base.resolve(userInput).normalize();
if (!resolved.startsWith(base)) throw new SecurityException("Path traversal");
// BAD example
File f = new File(userInput);  // Not validated, possible path traversal
```

#### `G.FIO.02` [Security] ZipInputStream entries must be security-checked before extraction (path traversal + zip bomb)

#### `G.FIO.03` [Security] Use int return type for single byte/char reads from streams (InputStream.read/Reader.read)

#### `G.FIO.04` [Security] Prevent external process blocking on input/output streams (drain stdout/stderr)

### G.FMT Formatting

#### `G.FMT.01` Source file encoding must be UTF-8

#### `G.FMT.02` A source file contains copyright, package, import, top-level class in order, separated by blank lines

#### `G.FMT.03` Import order: static imports first, then Android, Huawei, commercial, open-source, net/org, Java — grouped by blank lines

#### `G.FMT.04` Class member order: class variables, static init blocks, instance variables, instance init blocks, constructors, methods — separated by blank lines

#### `G.FMT.05` Braces must be used in conditional statements and loop blocks

#### `G.FMT.06` Non-empty blocks: left brace at end of line, right brace on a new line

#### `G.FMT.07` Avoid empty blocks; when an empty block is necessary, use a consistent brace-newline style

Avoid empty blocks; when necessary, use a consistent brace-newline style

```java
// OK example
// Add a comment explaining why it is empty
if (cond) {
    // Intentionally left empty, see ISSUE-123
}
// BAD example
if (cond) {}  // Empty block without comment
```

#### `G.FMT.08` Use spaces for indentation, 4 spaces per level (no tabs)

#### `G.FMT.09` No more than one statement per line

#### `G.FMT.10` Line width should not exceed 120 narrow characters

#### `G.FMT.11` Line breaks should start before the operator

#### `G.FMT.12` Reduce unnecessary blank lines; keep code compact

#### `G.FMT.13` Use spaces to highlight keywords and important information

#### `G.FMT.14` Do not insert extra spaces to vertically align code

#### `G.FMT.15` Enum constants separated by commas; line breaks optional

#### `G.FMT.16` When a case block ends without break, a comment must explain the fall-through

#### `G.FMT.17` Each annotation on a class, method, or class field should be on its own line

#### `G.FMT.18` Block comment indentation should match the surrounding code

#### `G.FMT.19` Class and member modifiers should follow the order recommended by the Java Language Specification

#### `G.FMT.20` Numeric literals should have appropriate suffixes; long types should use L

### G.LOG Logging

#### `G.LOG.01` Use the Facade pattern for logging (SLF4J), not Log4j/Logback directly

#### `G.LOG.02` Logger instances must be declared as private static final

#### `G.LOG.03` Logs must be leveled (DEBUG/INFO/WARN/ERROR)

#### `G.LOG.04` Products not exclusively sold in Chinese-speaking regions must not use Chinese in log messages

### G.MET Methods

#### `G.MET.01` Methods should be short; parameters should not exceed 5

#### `G.MET.03` Method parameters should not be used as temporary variables

#### `G.MET.04` Use varargs with caution

#### `G.MET.05` Methods returning arrays or collections should return empty collections, not null

#### `G.MET.06` Use Optional instead of null for possibly missing return values; assigning null to Optional is prohibited

#### `G.MET.07` [Security] Do not ignore method return values

### G.NAM Naming

#### `G.NAM.01` Identifiers should not exceed 64 characters, consisting of letters, digits, and underscores

#### `G.NAM.02` Package names should be lowercase, with dots separating levels

#### `G.NAM.03` Class, enum, and interface names should use PascalCase

Class, enum, and interface names use PascalCase

```java
// OK example
public class OrderService {}
// BAD example
public class order_service {}  // Underscore style
```

#### `G.NAM.04` Method names should use camelCase

Method names use camelCase

```java
// OK example
public String getUserName() {}
// BAD example
public String get_user_name() {}
```

#### `G.NAM.05` Constant names should be ALL_UPPERCASE with underscores separating words

Constant names are ALL_UPPERCASE with underscores between words

```java
// OK example
static final int MAX_RETRY = 3;
// BAD example
static final int maxRetry = 3;
```

#### `G.NAM.06` Variables use camelCase

#### `G.NAM.07` Avoid boolean variable names with negative meanings

#### `G.NAM.08` Boolean variables should start with a verb expressing a true/false meaning

### G.OBJ Classes & Objects

#### `G.OBJ.01` Avoid defining public and non-final class fields

#### `G.OBJ.02` Do not call overridable methods in a parent class constructor

#### `G.OBJ.03` When there are multiple constructors, reuse them as much as possible

#### `G.OBJ.04` Avoid reusing names across unrelated variables or concepts; avoid hiding/shadowing/obscuring

#### `G.OBJ.05` Avoid overloading methods with the same name between primitive and wrapper types

#### `G.OBJ.06` When overriding equals, you must also override hashCode

When overriding equals, you must also override hashCode

```java
// OK example
@Override public boolean equals(Object o) { ... }
@Override public int hashCode() { ... }
// BAD example
@Override public boolean equals(Object o) { ... }  // No hashCode, HashMap will break
```

#### `G.OBJ.07` Override methods must have @Override annotation

#### `G.OBJ.08` Implement the Singleton pattern correctly

#### `G.OBJ.09` [Security] Use class name to call static methods, not instances or expressions

#### `G.OBJ.10` Remove redundant modifiers from interface definitions

### G.OTH Other

#### `G.OTH.01` [Security] Use cryptographically secure random numbers in security contexts

#### `G.OTH.02` [Security] Use SSLSocket instead of Socket for secure data exchange

#### `G.OTH.03` Unused code segments (including imports) should be deleted, not commented out

#### `G.OTH.04` [Security] Do not include public network addresses (IP/URL/email) in code

#### `G.OTH.05` [Security] Remove invalid or never-executed code

```java
// OK example
// Only keep code that will execute
// BAD example
if (false) { deadCode(); }  // Never executes
return; doCleanup();  // Dead code after return
```

### G.PRM Performance

#### `G.PRM.01` Use Collection.toArray(T[]) for collection-to-array; after Java 11, use toArray(IntFunction)

#### `G.PRM.02` Use System.arraycopy or Arrays.copyOf for array copying

#### `G.PRM.04` Do not repeatedly pre-compile the same regular expression

#### `G.PRM.05` Do not create unnecessary objects

Do not create unnecessary objects

```java
// OK example
Boolean enabled = Boolean.TRUE;  // Or directly boolean enabled = true;
String s = "literal";
// BAD example
Boolean enabled = new Boolean(true);  // New object each time
String s = new String("literal");
```

#### `G.PRM.07` IO operations must close resources in try-with-resources or finally

#### `G.PRM.08` Explicit GC calls are prohibited (except for passwords/RMI, etc.), especially in frequent/periodic logic

#### `G.PRM.09` The Finalizer mechanism is prohibited

#### `G.PRM.10` Do not create temporary variables just for a return statement

### G.SEC Security

#### `G.SEC.01` [Security] Security-check methods must be declared private or final

#### `G.SEC.02` [Security] Custom ClassLoader overriding getPermissions() must call super.getPermissions() first

#### `G.SEC.04` [Security] Use a security manager to protect sensitive operations

### G.SER Serialization

#### `G.SER.01` Avoid implementing Serializable when possible

#### `G.SER.02` Classes implementing Serializable should explicitly declare serialVersionUID

#### `G.SER.04` Do not directly serialize information pointing to system resources

#### `G.SER.05` [Security] Do not serialize non-static inner classes

#### `G.SER.07` [Security] Prevent deserialization from bypassing constructor security checks

### G.TYP Types

#### `G.TYP.03` Do not use floating-point numbers as loop counters

#### `G.TYP.04` Use BigDecimal for exact calculations; do not use float/double

#### `G.TYP.05` Do not use == directly for floating-point equality; do not use equals for wrapper types

#### `G.TYP.06` Do not compare with NaN; use isNaN()

#### `G.TYP.07` Do not hardcode line separators or file path separators; use constants

#### `G.TYP.08` String case conversion and number formatting must specify Locale.ROOT or Locale.ENGLISH

#### `G.TYP.09` Character-to-byte conversions must specify the encoding

#### `G.TYP.11` Prefer primitive types over wrapper types; use wrapper types judiciously

#### `G.TYP.12` Perform type conversions explicitly; avoid implicit conversions

#### `G.TYP.13` Use instanceof to check before downcasting a reference type

## Extended Rules (High-frequency issues not covered by the Huawei 102 rules)

The following are issues that frequently appear in Java Code Reviews but are not covered by the Huawei CodeArts Check rule set. Rule IDs use the `X.` prefix to distinguish them from `G.*`. Rules with OK/BAD examples are listed separately; the rest are in tables.

### Testing Standards

| Rule ID | Rule Summary |
|---|---|
| `X.TST.01` | Test methods must not share state (`static` fields); each test should be independent |
| `X.TST.02` | Tests must have effective assertions, not just `assertNotNull`; assertions should cover core business logic |
| `X.TST.03` | Avoid mocking static methods (PowerMock); prefer refactoring code to make dependencies injectable |
| `X.TST.04` | Integration tests (depending on DB/HTTP) must be tagged with `@Tag("integration")` or similar, separated from unit tests |
| `X.TST.05` | Test class names should correspond to the class under test: `UserService` -> `UserServiceTest`; method names `methodName_scenario_expected` |
| `X.TST.06` | Test three-part structure: Given (setup) -> When (execute) -> Then (assert), separated by blank lines |

### SQL & Database

| Rule ID | Rule Summary |
|---|---|
| `X.SQL.01` | SQL concatenation is prohibited; use PreparedStatement or parameterized queries |
| `X.SQL.02` | Queries on large tables must be paginated (`LIMIT/OFFSET` or `WHERE id > lastId` cursor pagination) |
| `X.SQL.03` | `WHERE` condition fields must have indexes to avoid full table scans; composite indexes must follow the leftmost prefix rule |
| `X.SQL.04` | `SELECT *` is prohibited; specify column names explicitly to reduce IO and avoid mapping errors when columns change |
| `X.SQL.05` | Use batch for bulk operations, not single inserts in a loop (`addBatch()` or MyBatis `<foreach>`) |
| `X.SQL.06` | Minimize transaction scope; do not make RPC calls or perform expensive computations inside transactions |
| `X.SQL.07` | Avoid large/long transactions that hold locks and connection pool resources; use `@Transactional(timeout=...)` to set an upper bound |

#### `X.SQL.01` SQL concatenation is prohibited; use PreparedStatement or parameterized queries

```java
// OK example
// PreparedStatement parameterized
String sql = "SELECT id, name FROM user WHERE name = ? AND status = ?";
try (PreparedStatement ps = conn.prepareStatement(sql)) {
    ps.setString(1, name);
    ps.setInt(2, status);
    try (ResultSet rs = ps.executeQuery()) { ... }
}
// MyBatis: use #{} not ${}
// <select id="findByName">SELECT ... WHERE name = #{name}</select>
// BAD example
// String concatenation, SQL injection risk
String sql = "SELECT id, name FROM user WHERE name = '" + name + "' AND status = " + status;
Statement st = conn.createStatement();
ResultSet rs = st.executeQuery(sql);  // name containing ' OR '1'='1 enables injection
// MyBatis: ${} is string concatenation, equally dangerous
// <select id="findByName">SELECT ... WHERE name = '${name}'</select>
```

### Concurrency Details (not covered by Huawei G.CON)

| Rule ID | Rule Summary |
|---|---|
| `X.CON.03` | `synchronized` must not lock String literals, Integer cache objects, or Boolean; lock `new Object()` or a dedicated lock object |
| `X.CON.05` | `HashMap` concurrent writes by multiple threads can cause infinite loops (JDK7 linked list cycle) or data loss; use `ConcurrentHashMap` |

#### `X.CON.01` SimpleDateFormat is not thread-safe; use DateTimeFormatter or ThreadLocal in multi-threaded contexts

```java
// OK example
// 1: DateTimeFormatter (JDK8+, thread-safe)
private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
String s = LocalDate.now().format(FMT);

// OK example 2: ThreadLocal wrapper (compatible with legacy code)
private static final ThreadLocal<SimpleDateFormat> TL =
    ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd"));
String s = TL.get().format(new Date());
// BAD example
// static SimpleDateFormat shared across threads
private static final SimpleDateFormat FMT = new SimpleDateFormat("yyyy-MM-dd");
// Multiple threads calling FMT.format() will overwrite each other's Calendar internal state, causing incorrect results
// Symptom: occasionally parsing 1970 or throwing NumberFormatException
```

#### `X.CON.02` Double-checked locking must use volatile to prevent instruction reordering

```java
// OK example
// instance with volatile
public class Singleton {
    private static volatile Singleton instance;
    public static Singleton getInstance() {
        if (instance == null) {
            synchronized (Singleton.class) {
                if (instance == null) {
                    instance = new Singleton();
                }
            }
        }
        return instance;
    }
}
// BAD example
// Missing volatile
public class Singleton {
    private static Singleton instance;  // Missing volatile
    public static Singleton getInstance() {
        if (instance == null) {           // Thread B may read a half-initialized object
            synchronized (Singleton.class) {
                if (instance == null) {
                    instance = new Singleton();  // Instruction reordering: assignment before field initialization
                }
            }
        }
        return instance;  // Other threads get an "initialized" object whose fields are still null
    }
}
```

#### `X.CON.04` Use computeIfAbsent instead of get+put with ConcurrentHashMap to ensure atomicity

```java
// OK example
// computeIfAbsent atomic operation
ConcurrentMap<String, List<String>> map = new ConcurrentHashMap<>();
map.computeIfAbsent(key, k -> new ArrayList<>()).add(value);  // Done in one step

// OK example: putIfAbsent (alternative to get+put)
List<String> list = new ArrayList<>();
List<String> old = map.putIfAbsent(key, list);
if (old != null) { list = old; }
list.add(value);
// BAD example
// get + put is not atomic; data loss under concurrent access
ConcurrentMap<String, List<String>> map = new ConcurrentHashMap<>();
List<String> list = map.get(key);      // Threads A and B both get null
if (list == null) {
    list = new ArrayList<>();
    map.put(key, list);                 // Threads A and B each put one; A's is overwritten
}
list.add(value);  // Thread A adds to its own list, but map contains B's
```

#### `X.CON.06` Thread pools must be created explicitly with ThreadPoolExecutor; Executors convenience methods are prohibited

```java
// OK example
// Explicit ThreadPoolExecutor with controllable parameters
ExecutorService pool = new ThreadPoolExecutor(
    8,                              // corePoolSize
    16,                             // maxPoolSize
    60L, TimeUnit.SECONDS,          // keepAliveTime
    new LinkedBlockingQueue<>(1000),// Bounded queue, prevents OOM
    new ThreadFactoryBuilder().setNameFormat("order-pool-%d").build(),  // Named threads
    new ThreadPoolExecutor.CallerRunsPolicy()  // Rejection policy: caller runs
);
// BAD example
// Executors convenience methods have significant risks
ExecutorService pool1 = Executors.newCachedThreadPool();   // Unbounded thread count, may create tens of thousands of threads
ExecutorService pool2 = Executors.newFixedThreadPool(8);   // Unbounded queue, task accumulation causes OOM
ExecutorService pool3 = Executors.newSingleThreadExecutor(); // Same as newFixedThreadPool(1), unbounded queue

// Problem: queues are SynchronousQueue (unbounded) / LinkedBlockingQueue (unbounded); task accumulation leads to OOM
```

### Collection Details (not covered by Huawei G.COL)

| Rule ID | Rule Summary |
|---|---|
| `X.COL.01` | `Arrays.asList()` returns a fixed-size list; `add/remove` throws `UnsupportedOperationException`; for a mutable list use `new ArrayList<>(Arrays.asList(...))` |
| `X.COL.02` | `subList()` returns a view; modifications affect the original list; for an independent copy use `new ArrayList<>(list.subList(...))` |
| `X.COL.03` | Use `isEmpty()` instead of `size() == 0` for collection emptiness checks; clearer semantics |
| `X.COL.04` | Using `LinkedList` with random access `get(i)` is O(n); use `ArrayList` for random access |

### Performance Details (not covered by Huawei G.PRM)

| Rule ID | Rule Summary |
|---|---|
| `X.PRM.01` | Do not call DB or RPC inside loops; use batch processing or preloading |
| `X.PRM.02` | Use `StringBuilder` for string concatenation in loops, not `+=` (creates a new object each time) |
| `X.PRM.03` | Do not traverse a `Stream` multiple times: `list.stream().count()` then `collect` again; complete in one pass |
| `X.PRM.04` | `String.matches("...")` recompiles the regex each time; use `Pattern.compile` with a static cache |
| `X.PRM.05` | Avoid creating large objects in hot paths (e.g., `new ArrayList<>(hugeCapacity)` inside a loop) |

### Dependencies & Build

| Rule ID | Rule Summary |
|---|---|
| `X.DEP.01` | Dependency versions should be unified in the parent pom's `<dependencyManagement>`; submodules should not specify version |
| `X.DEP.02` | Do not import large libraries like `commons-lang3` just to use one method; write it by hand or find a lightweight alternative |
| `X.DEP.03` | The `provided` scope is only for container-provided dependencies (e.g., Servlet API); do not use provided for runtime-needed dependencies |
| `X.DEP.04` | Avoid circular dependencies: A depends on B, B depends on A; refactor to extract a shared module |
