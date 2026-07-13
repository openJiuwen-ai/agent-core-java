# 遗留代码重构

本文件针对"没有测试的旧代码"如何安全重构，源自《修改代码的艺术》。用户说"代码没测试不敢改"或"老代码怎么加测试"时按需读取。

## 核心概念：接缝点（Seam）

**接缝点**：能在不修改代码的情况下改变行为的位置。遗留代码重构的关键是找到接缝点，注入测试。

| 接缝类型 | 例子 | 测试方式 |
|---|---|---|
| **方法参数** | `process(data, parser)` | 传 mock parser |
| **构造注入** | `new Service(repo)` | 传 mock repo |
| **方法重写** | 子类重写 `protected` 方法 | 测试子类 |
| **接口提取** | 把 static 调用改成接口 | 传 mock 接口 |

## 特征测试（Characterization Test）

记录当前行为（哪怕有 bug），不改逻辑。重构后跑特征测试，行为不变就安全。

### 场景 1：纯函数式逻辑

```java
// 原代码
public class TaxCalculator {
    public double calculate(double amount, String type) {
        if ("VIP".equals(type)) return amount * 0.8;
        if (amount > 1000) return amount * 0.9;
        return amount;
    }
}

// 特征测试：记录当前所有边界行为
@Test
void characterize_taxCalc() {
    TaxCalculator calc = new TaxCalculator();
    assertEquals(80.0, calc.calculate(100, "VIP"));       // VIP 8 折
    assertEquals(800.0, calc.calculate(1000, "VIP"));      // VIP 8 折（不看金额）
    assertEquals(900.0, calc.calculate(1000, "NORMAL"));   // 非 VIP 超 1000 打 9 折
    assertEquals(100.0, calc.calculate(100, "NORMAL"));    // 非 VIP 不超 1000 原价
    assertEquals(100.0, calc.calculate(100, null));         // type null 当普通
    assertEquals(0.0, calc.calculate(0, "VIP"));           // 边界 0
    assertEquals(0.0, calc.calculate(-100, "VIP"));        // 负数（记录 bug 也不改）
}
```

### 场景 2：有副作用的逻辑（用 spy 记录调用）

```java
// 原代码：有 logger 和 DB 调用
public class OrderService {
    public void place(Order order) {
        log.info("placing " + order.getId());
        db.save(order);
        email.send(order.getCustomerEmail(), "Order placed");
    }
}

// 特征测试：用 spy 记录调用，不验证"对错"，只验证"调了什么"
@Test
void characterize_place_callsLoggerDbAndEmail() {
    // 用 spy 包装依赖
    OrderService svc = new OrderService(logSpy, dbSpy, emailSpy);
    svc.place(new Order("o1", "alice@example.com", 100));

    // 记录当前行为：logger 调了什么、db 存了什么、email 发了什么
    verify(logSpy).info("placing o1");
    assertThat(dbSpy.saved).containsExactly(new Order("o1", "alice@example.com", 100));
    assertThat(emailSpy.sent).hasSize(1);
    assertThat(emailSpy.sent.get(0).getTo()).isEqualTo("alice@example.com");
    assertThat(emailSpy.sent.get(0).getBody()).isEqualTo("Order placed");
}
```

### 场景 3：复杂条件分支（记录每个分支的输出）

```java
// 用参数化测试覆盖所有分支
@ParameterizedTest
@CsvSource({
    "100, VIP, 80.0",
    "1000, VIP, 800.0",
    "1000, NORMAL, 900.0",
    "100, NORMAL, 100.0",
    "0, VIP, 0.0",
    "-100, VIP, -80.0"  // 负数也记录，即使有 bug
})
void characterize_branches(double amount, String type, double expected) {
    assertEquals(expected, calc.calculate(amount, type));
}
```

## 解依赖技术

遗留代码没法测，主要因为依赖硬编码。6 种打破依赖的手法：

### 1. 提取接口（Extract Interface）

**适用**：static 调用或 new 硬依赖，无法注入 mock。

```java
// 重构前：硬依赖 DateUtil.now()，无法 mock
public class OrderService {
    public Order create() {
        return new Order(DateUtil.now());  // static，不可测
    }
}

// 重构后：提取接口，构造注入
public interface TimeProvider {
    long now();
}
public class OrderService {
    private final TimeProvider time;
    public OrderService(TimeProvider time) { this.time = time; }
    public Order create() {
        return new Order(time.now());  // 可注入 mock
    }
}
// 生产代码：new OrderService(new SystemTimeProvider())
// 测试代码：new OrderService(() -> 1700000000000L)  // 固定时间
```

### 2. 参数化方法（Parameterize Method）

**适用**：方法内部 new 依赖，把依赖改成参数。

```java
// 重构前
public Result process(String input) {
    Parser parser = new JsonParser();  // 硬 new，无法换
    return parser.parse(input);
}

// 重构后
public Result process(String input) {
    return process(input, new JsonParser());  // delegate 到参数化版本
}
public Result process(String input, Parser parser) {  // 可测
    return parser.parse(input);
}
// 测试：svc.process("{}", new MockParser())
```

### 3. 子类化重写（Subclass and Override）

**适用**：方法调用了不可测的依赖，用子类重写方法隔离。

```java
// 重构前
public class ReportService {
    public String generate() {
        Data data = fetchFromDB();  // protected 方法，连 DB
        return format(data);
    }
    protected Data fetchFromDB() {
        return realDb.query(...);  // 不可测
    }
}

// 测试：重写 fetchFromDB
class TestableReportService extends ReportService {
    @Override
    protected Data fetchFromDB() {
        return new Data("test");  // 不连 DB
    }
}
@Test
void generate_formatsData() {
    TestableReportService svc = new TestableReportService();
    assertEquals("formatted: test", svc.generate());
}
```

### 4. 链接接缝（Link Seam）

**适用**：依赖是 static 方法，无法重写。用 classpath 替换实现。

```java
// 重构前：static 调用
public class Service {
    public void doWork() {
        Config cfg = ConfigLoader.load();  // static，从文件读
        // ...
    }
}

// 测试：用 test classpath 的同名类替换 ConfigLoader
// src/test/java/.../ConfigLoader.java
public class ConfigLoader {
    public static Config load() {
        return new Config("test-value");  // 测试版返回固定值
    }
}
```

**注意**：这是最后手段，破坏性大，慎用。

### 5. 实例化委托（Instance Delegation）

**适用**：static 方法调用太多，把 static 包装成实例。

```java
// 重构前
public double calc(Order o) {
    double base = PricingEngine.compute(o);      // static
    double tax = TaxUtil.calculate(base);        // static
    return base + tax;
}

// 重构后：包装成实例
public class PricingService {
    public double compute(Order o) { return PricingEngine.compute(o); }  // 包装
}
public class TaxService {
    public double calculate(double base) { return TaxUtil.calculate(base); }
}
// 注入到 Service
public double calc(Order o, PricingService pricing, TaxService tax) {
    double base = pricing.compute(o);
    return base + tax.calculate(base);
}
```

### 6. 包装（Wrap Method / Wrap Class）

**适用**：不能改原方法，但要在前后加逻辑（日志/缓存/校验）。

```java
// 重构前
public void save(Order o) {
    db.insert(o);
}

// 包装方法（不改原方法）
public void saveWithLog(Order o) {
    log.info("saving " + o.getId());
    save(o);  // 原 method 不动
    log.info("saved " + o.getId());
}
```

## 遗留代码重构流程

1. **找接缝点**：哪里能注入测试（参数/构造/重写/接口）
2. **打特征测试**：在接缝点记录当前行为
3. **解依赖**：用上述 6 种手法之一打破硬依赖
4. **补测试**：解依赖后，正式测试可写了
5. **小步重构**：每步跑特征测试，行为不变
6. **删特征测试**：重构完成，正式测试覆盖后，特征测试可删

## 关键原则

- **遗留代码 = 没测试的代码**（Michael Feathers 定义），不是"老代码"
- **先加测试，再改代码**：顺序不能反
- **特征测试不判断对错**：记录现状，哪怕 bug 也记下来，重构后再修 bug
- **解依赖只做最小必要**：不要为了"好测"过度改造，够测就行
