# Legacy Code Refactoring

This file addresses how to safely refactor "old code without tests," based on "Working Effectively with Legacy Code." Read on demand when users say "I'm afraid to change code without tests" or "how to add tests to old code."

## Core Concept: Seam

**Seam**: A point where behavior can be altered without modifying the code. The key to legacy code refactoring is finding seam points and injecting tests.

| Seam Type | Example | Testing Approach |
|---|---|---|
| **Method parameter** | `process(data, parser)` | Pass mock parser |
| **Constructor injection** | `new Service(repo)` | Pass mock repo |
| **Method override** | Subclass overrides `protected` method | Test the subclass |
| **Interface extraction** | Change static call to interface | Pass mock interface |

## Characterization Test

Record current behavior (even if it has bugs), do not change logic. After refactoring, run characterization tests; if behavior is unchanged, it is safe.

### Scenario 1: Pure Functional Logic

```java
// Original code
public class TaxCalculator {
    public double calculate(double amount, String type) {
        if ("VIP".equals(type)) return amount * 0.8;
        if (amount > 1000) return amount * 0.9;
        return amount;
    }
}

// Characterization test: record all current boundary behaviors
@Test
void characterize_taxCalc() {
    TaxCalculator calc = new TaxCalculator();
    assertEquals(80.0, calc.calculate(100, "VIP"));       // VIP 20% off
    assertEquals(800.0, calc.calculate(1000, "VIP"));      // VIP 20% off (regardless of amount)
    assertEquals(900.0, calc.calculate(1000, "NORMAL"));   // Non-VIP over 1000 gets 10% off
    assertEquals(100.0, calc.calculate(100, "NORMAL"));    // Non-VIP under 1000 full price
    assertEquals(100.0, calc.calculate(100, null));         // type null treated as normal
    assertEquals(0.0, calc.calculate(0, "VIP"));           // Boundary: 0
    assertEquals(0.0, calc.calculate(-100, "VIP"));        // Negative (record bug as-is)
}
```

### Scenario 2: Logic with Side Effects (Use Spy to Record Calls)

```java
// Original code: has logger and DB calls
public class OrderService {
    public void place(Order order) {
        log.info("placing " + order.getId());
        db.save(order);
        email.send(order.getCustomerEmail(), "Order placed");
    }
}

// Characterization test: use spy to record calls, do not verify "right or wrong", only verify "what was called"
@Test
void characterize_place_callsLoggerDbAndEmail() {
    // Wrap dependencies with spies
    OrderService svc = new OrderService(logSpy, dbSpy, emailSpy);
    svc.place(new Order("o1", "alice@example.com", 100));

    // Record current behavior: what logger called, what db saved, what email sent
    verify(logSpy).info("placing o1");
    assertThat(dbSpy.saved).containsExactly(new Order("o1", "alice@example.com", 100));
    assertThat(emailSpy.sent).hasSize(1);
    assertThat(emailSpy.sent.get(0).getTo()).isEqualTo("alice@example.com");
    assertThat(emailSpy.sent.get(0).getBody()).isEqualTo("Order placed");
}
```

### Scenario 3: Complex Conditional Branches (Record Output for Each Branch)

```java
// Use parameterized test to cover all branches
@ParameterizedTest
@CsvSource({
    "100, VIP, 80.0",
    "1000, VIP, 800.0",
    "1000, NORMAL, 900.0",
    "100, NORMAL, 100.0",
    "0, VIP, 0.0",
    "-100, VIP, -80.0"  // Record negative too, even if buggy
})
void characterize_branches(double amount, String type, double expected) {
    assertEquals(expected, calc.calculate(amount, type));
}
```

## Dependency-Breaking Techniques

Legacy code is untestable mainly because dependencies are hardcoded. 6 techniques for breaking dependencies:

### 1. Extract Interface

**Applicable to**: Static calls or hardcoded `new` dependencies that cannot be injected with mocks.

```java
// Before refactoring: hard dependency on DateUtil.now(), cannot mock
public class OrderService {
    public Order create() {
        return new Order(DateUtil.now());  // static, untestable
    }
}

// After refactoring: extract interface, constructor injection
public interface TimeProvider {
    long now();
}
public class OrderService {
    private final TimeProvider time;
    public OrderService(TimeProvider time) { this.time = time; }
    public Order create() {
        return new Order(time.now());  // Can inject mock
    }
}
// Production code: new OrderService(new SystemTimeProvider())
// Test code: new OrderService(() -> 1700000000000L)  // Fixed time
```

### 2. Parameterize Method

**Applicable to**: Method internally `new`s a dependency; change the dependency to a parameter.

```java
// Before refactoring
public Result process(String input) {
    Parser parser = new JsonParser();  // Hard new, cannot swap
    return parser.parse(input);
}

// After refactoring
public Result process(String input) {
    return process(input, new JsonParser());  // Delegate to parameterized version
}
public Result process(String input, Parser parser) {  // Testable
    return parser.parse(input);
}
// Test: svc.process("{}", new MockParser())
```

### 3. Subclass and Override

**Applicable to**: Method calls an untestable dependency; use subclass to override method for isolation.

```java
// Before refactoring
public class ReportService {
    public String generate() {
        Data data = fetchFromDB();  // protected method, connects to DB
        return format(data);
    }
    protected Data fetchFromDB() {
        return realDb.query(...);  // Untestable
    }
}

// Test: override fetchFromDB
class TestableReportService extends ReportService {
    @Override
    protected Data fetchFromDB() {
        return new Data("test");  // No DB connection
    }
}
@Test
void generate_formatsData() {
    TestableReportService svc = new TestableReportService();
    assertEquals("formatted: test", svc.generate());
}
```

### 4. Link Seam

**Applicable to**: Dependency is a static method that cannot be overridden. Replace implementation via classpath.

```java
// Before refactoring: static call
public class Service {
    public void doWork() {
        Config cfg = ConfigLoader.load();  // static, reads from file
        // ...
    }
}

// Test: replace ConfigLoader with same-named class on test classpath
// src/test/java/.../ConfigLoader.java
public class ConfigLoader {
    public static Config load() {
        return new Config("test-value");  // Test version returns fixed value
    }
}
```

**Note**: This is a last resort; it is highly disruptive. Use with caution.

### 5. Instance Delegation

**Applicable to**: Too many static method calls; wrap statics as instances.

```java
// Before refactoring
public double calc(Order o) {
    double base = PricingEngine.compute(o);      // static
    double tax = TaxUtil.calculate(base);        // static
    return base + tax;
}

// After refactoring: wrap as instances
public class PricingService {
    public double compute(Order o) { return PricingEngine.compute(o); }  // Wrapper
}
public class TaxService {
    public double calculate(double base) { return TaxUtil.calculate(base); }
}
// Inject into Service
public double calc(Order o, PricingService pricing, TaxService tax) {
    double base = pricing.compute(o);
    return base + tax.calculate(base);
}
```

### 6. Wrap Method / Wrap Class

**Applicable to**: Cannot modify the original method, but need to add logic before/after (logging/caching/validation).

```java
// Before refactoring
public void save(Order o) {
    db.insert(o);
}

// Wrap method (do not modify original method)
public void saveWithLog(Order o) {
    log.info("saving " + o.getId());
    save(o);  // Original method untouched
    log.info("saved " + o.getId());
}
```

## Legacy Code Refactoring Process

1. **Find seam points**: Where can tests be injected (parameters/constructor/override/interface)
2. **Write characterization tests**: Record current behavior at seam points
3. **Break dependencies**: Use one of the 6 techniques above to break hard dependencies
4. **Add tests**: After breaking dependencies, formal tests can be written
5. **Small-step refactoring**: Run characterization tests at each step; behavior must not change
6. **Delete characterization tests**: After refactoring is complete and formal tests provide coverage, characterization tests can be removed

## Key Principles

- **Legacy code = code without tests** (Michael Feathers' definition), not "old code"
- **Add tests first, then change code**: The order must not be reversed
- **Characterization tests do not judge right or wrong**: Record the current state, even bugs are recorded; fix bugs after refactoring
- **Only break the minimum necessary dependencies**: Do not over-engineer for "testability"; break" just enough to test
