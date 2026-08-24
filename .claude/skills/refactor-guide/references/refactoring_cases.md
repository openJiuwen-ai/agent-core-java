# Real Refactoring Cases

This file provides 4 end-to-end cases, each containing "current state -> problem -> refactoring steps -> verification -> result." Read on demand when users ask "give me a refactoring case" or "how to refactor this kind of problem."

## Case 1: Splitting a 1000-Line Service Class (L2 Structural Refactoring)

### Current State

`OrderService` is 1100 lines, containing 5 responsibilities: order creation, payment, logistics, notification, and reporting. Changing any single piece of logic requires searching through 1000 lines, making it easy to introduce errors.

### Problem

- Severe Single Responsibility violation
- Tests are hard to write: testing "create order" requires mocking payment/logistics/notification dependencies
- Multiple people editing the same file causes frequent conflicts

### Refactoring Steps

**Phase 1: Add Characterization Tests**

First, write a set of characterization tests for each of the 5 responsibilities to be split, recording current behavior.

```java
@Test
void characterize_createOrder() {
    Order o = svc.create(new OrderRequest("alice", "sku1", 2));
    assertEquals("PENDING", o.getStatus());
    assertNotNull(o.getId());
    verify(notifier).notifyCustomer("alice", "order created");
}
```

**Phase 2: Extract Class**

Split into 5 classes by responsibility; old Service temporarily delegates:

```java
public class OrderService {
    private final OrderCreator creator;
    private final PaymentProcessor payment;
    private final LogisticsTracker logistics;
    private final NotificationService notifier;
    private final ReportGenerator reports;

    public Order create(OrderRequest req) { return creator.create(req); }
    public void pay(Order o) { payment.pay(o); }
    // ... delegate
}
```

Commit once + run characterization tests after extracting each class.

**Phase 3: Migrate Callers**

Gradually change `orderService.create(...)` to `orderCreator.create(...)`, migrating in separate PRs.

**Phase 4: Delete Old Service Delegates**

After all callers have migrated, `OrderService` only contains composition and delegation, and can be deleted.

### Verification

- All characterization tests green (behavior unchanged)
- Each new class has independent tests (testability improved)
- No conflicts in multi-person development

### Result

1100 lines -> 5 classes of ~200 lines each, each with a single responsibility.

---

## Case 2: Static Calls to Dependency Injection (L3 Interface Refactoring)

### Current State

Business code calls `DateUtil.now()` and `ConfigLoader.load()` everywhere; static methods cannot be mocked, so tests either skip or write integration tests connecting to real dependencies.

### Problem

- Low unit test coverage, connecting to real DB/file system
- Slow tests, CI reluctant to run them

### Refactoring Steps

**Phase 1: Extract Interface**

Wrap statics into interfaces:

```java
public interface TimeProvider { long now(); }
public interface ConfigProvider { Config load(); }

public class SystemTimeProvider implements TimeProvider {
    public long now() { return System.currentTimeMillis(); }
}
public class FileConfigProvider implements ConfigProvider {
    public Config load() { return ConfigLoader.load(); }
}
```

**Phase 2: New Signature + Old Signature Coexist**

```java
public class OrderService {
    private final TimeProvider time;
    private final ConfigProvider config;

    // New constructor (for production)
    public OrderService(TimeProvider time, ConfigProvider config) {
        this.time = time; this.config = config;
    }

    // Old constructor (backward compatible, defaults to System implementation)
    public OrderService() {
        this(new SystemTimeProvider(), new FileConfigProvider());
    }

    public Order create() {
        return new Order(time.now(), config.load().getDefaultCurrency());
    }
}
```

**Phase 3: Migrate Callers**

Callers gradually change from `new OrderService()` to `new OrderService(timeProvider, configProvider)`.

**Phase 4: Delete Old Constructor**

After all callers have migrated, delete the no-arg constructor, enforcing injection.

### Verification

```java
@Test
void create_usesFixedTime() {
    OrderService svc = new OrderService(() -> 1700000000000L, () -> testConfig);
    Order o = svc.create();
    assertEquals(1700000000000L, o.getCreatedAt());
}
```

Unit tests no longer connect to real dependencies; CI is 5x faster.

### Result

All static dependencies changed to injection; unit test coverage 40% -> 85%.

---

## Case 3: Monolith to Microservices (L4 Architecture Refactoring)

### Current State

Orders, inventory, and payments are in the same Spring Boot application; deployment is coupled; changing inventory requires restarting the entire application.

### Problem

- Deployment coupling: small modules require full releases
- Poor resource isolation: report queries drag down order creation
- Unclear team boundaries: everyone edits the same repository

### Refactoring Steps

**Phase 1: Set Up New Service, No Traffic**

Create a new `inventory-service` standalone application; first move inventory logic there (no traffic, independently testable).

```
monolith/
└── inventory/ (old implementation, kept)
inventory-service/ (new implementation, independently deployed)
```

**Phase 2: Strangler Fig Integration**

Change inventory calls in the monolith to dual-write + shadow:

```java
public void updateStock(String sku, int qty) {
    legacyInventoryRepo.update(sku, qty);              // Old implementation, still in use
    inventoryServiceClient.update(sku, qty);            // New service, shadow call
    // Shadow only records results, does not affect return
}
```

Compare data consistency on both sides; fix new service first if issues are found.

**Phase 3: Canary Traffic Shift**

Add feature flag, shift to new service by SKU or percentage:

```java
public void updateStock(String sku, int qty) {
    if (featureFlag.useNewInventory(sku)) {  // Canary
        inventoryServiceClient.update(sku, qty);
    } else {
        legacyInventoryRepo.update(sku, qty);
    }
}
```

During canary period, monitor new service latency, error rate, and data consistency.

**Phase 4: Decommission Old Implementation**

After canary reaches 100% and is stable, delete `monolith/inventory/`; old code is fully decommissioned.

### Verification

- Each phase independently testable and rollbackable
- Data consistency 99.99%+ during canary period
- New service latency < old implementation

### Result

Split completed in 3 months; inventory service independently deployed; team boundaries clear.

---

## Case 4: Add Tests Then Refactor (Legacy Code Without Tests)

### Current State

`PricingEngine` is 500 lines, zero tests, pure legacy code. Need to add a new pricing rule but afraid to change it.

### Problem

- No tests; cannot tell if changes break existing behavior
- Methods internally `new` dependencies (`new DateUtil()`, `new DiscountCalculator()`), cannot mock

### Refactoring Steps

**Phase 1: Find Seam Points**

The `compute` method internally `new`s `DiscountCalculator`; this is a seam point -- can extract interface for injection.

```java
// Original code
public double compute(Order order) {
    DiscountCalculator dc = new DiscountCalculator(order);  // Hard new
    double base = order.getAmount();
    double discount = dc.calculate();
    return base - discount + TaxUtil.compute(base - discount);  // static
}
```

**Phase 2: Write Characterization Tests (Break Dependencies First)**

Use "Parameterize Method" to break hard dependencies:

```java
// Extract injectable version
public double compute(Order order) {
    return compute(order, new DiscountCalculator(order), TaxUtil::compute);
}

// Version with dependencies, testable
double compute(Order order, DiscountCalculator dc, Function<Double, Double> taxFn) {
    double base = order.getAmount();
    double discount = dc.calculate();
    return base - discount + taxFn.apply(base - discount);
}
```

Write characterization tests, recording all boundaries:

```java
@Test
void characterize_compute_normal() {
    double r = engine.compute(new Order(100), new DiscountCalculator(0.1), t -> t * 0.1);
    assertEquals(99.0, r);  // 100 - 10 + 9
}

@Test
void characterize_compute_zeroDiscount() {
    double r = engine.compute(new Order(100), new DiscountCalculator(0), t -> 0);
    assertEquals(100.0, r);
}

@Test
void characterize_compute_zeroAmount() {
    double r = engine.compute(new Order(0), new DiscountCalculator(0.1), t -> 0);
    assertEquals(0.0, r);
}
```

**Phase 3: Small-Step Refactoring**

With characterization tests as a safety net, begin refactoring:
- Extract method: extract tax calculation into `calculateTax(double)`
- Extract constant: `0.1` into `DISCOUNT_RATE`
- Simplify conditions: use early returns to reduce nesting

Commit + run characterization tests at each step; if green, continue.

**Phase 4: Add Formal Tests**

After behavior stabilizes, add formal tests covering new logic (no longer just characterization tests):

```java
@Test
void compute_vipCustomer_getsExtraDiscount() {
    Order o = new Order(100, CustomerType.VIP);
    double r = engine.compute(o);
    assertEquals(80.0, r);  // VIP gets extra 10% off
}
```

**Phase 5: Add New Feature**

With tests in place, safely add new pricing rules.

### Verification

- All characterization tests green (behavior unchanged)
- New tests cover new rules
- Zero incidents during refactoring

### Result

Zero tests -> 80% coverage; adding new rules is now safe and testable.

## Common Case Process

| Phase | Action | Key Point |
|---|---|---|
| 1. Current state assessment | Review code scale, test coverage, dependency coupling | Determine refactoring level L1-L4 |
| 2. Add tests | Add tests if they exist; write characterization tests if none | Break dependencies before writing tests |
| 3. Small-step refactoring | One atomic action per step | Commit + test |
| 4. Verification | Full test suite + integration tests | Behavior unchanged |
| 5. Wrap-up | Delete dead code, delete characterization tests | Refactoring complete |
