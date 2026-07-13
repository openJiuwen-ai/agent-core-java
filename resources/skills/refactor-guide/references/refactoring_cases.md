# 真实重构案例

本文件给 4 个端到端案例，每个含"现状 → 问题 → 重构步骤 → 验证 → 结果"。用户问"给我一个重构案例"或"这类问题怎么重构"时按需读取。

## 案例 1：1000 行 Service 类拆分（L2 结构重构）

### 现状

`OrderService` 1100 行，包含订单创建、支付、物流、通知、报表 5 个职责。改任何一个逻辑都要在 1000 行里找，容易改错。

### 问题

- 单一职责严重违反
- 测试难写：测"创建订单"要 mock 支付/物流/通知依赖
- 多人改同一个文件频繁冲突

### 重构步骤

**阶段 1：补特征测试**

先对要拆的 5 个职责各写一组特征测试，记录当前行为。

```java
@Test
void characterize_createOrder() {
    Order o = svc.create(new OrderRequest("alice", "sku1", 2));
    assertEquals("PENDING", o.getStatus());
    assertNotNull(o.getId());
    verify(notifier).notifyCustomer("alice", "order created");
}
```

**阶段 2：提取类（Extract Class）**

按职责拆成 5 个类，旧 Service 暂时 delegate：

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

每提取一个类，commit 一次 + 跑特征测试。

**阶段 3：迁移调用方**

逐步把 `orderService.create(...)` 改成 `orderCreator.create(...)`，分 PR 迁移。

**阶段 4：删旧 Service 的 delegate**

调用方全部迁移后，`OrderService` 只剩组合和 delegate，可以删掉。

### 验证

- 特征测试全绿（行为不变）
- 新类各有独立测试（可测性提升）
- 多人开发无冲突

### 结果

1100 行 → 5 个类各 200 行，每个类职责单一。

---

## 案例 2：static 调用改为依赖注入（L3 接口重构）

### 现状

业务代码到处调 `DateUtil.now()` 和 `ConfigLoader.load()`，static 方法无法 mock，测试只能跳过或写集成测试连真实依赖。

### 问题

- 单测覆盖率低，连真实 DB/文件系统
- 测试慢，CI 不愿跑

### 重构步骤

**阶段 1：提取接口（Extract Interface）**

把 static 包装成接口：

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

**阶段 2：新签名 + 旧签名并存**

```java
public class OrderService {
    private final TimeProvider time;
    private final ConfigProvider config;

    // 新构造（生产用）
    public OrderService(TimeProvider time, ConfigProvider config) {
        this.time = time; this.config = config;
    }

    // 旧构造（向后兼容，default 到 System 实现）
    public OrderService() {
        this(new SystemTimeProvider(), new FileConfigProvider());
    }

    public Order create() {
        return new Order(time.now(), config.load().getDefaultCurrency());
    }
}
```

**阶段 3：迁移调用方**

调用方逐步从 `new OrderService()` 改成 `new OrderService(timeProvider, configProvider)`。

**阶段 4：删旧构造**

所有调用方迁移后，删无参构造，强制注入。

### 验证

```java
@Test
void create_usesFixedTime() {
    OrderService svc = new OrderService(() -> 1700000000000L, () -> testConfig);
    Order o = svc.create();
    assertEquals(1700000000000L, o.getCreatedAt());
}
```

单测不再连真实依赖，CI 快 5 倍。

### 结果

static 依赖全部改注入，单测覆盖率 40% → 85%。

---

## 案例 3：单体拆微服务（L4 架构重构）

### 现状

订单、库存、支付在同一个 Spring Boot 应用里，部署耦合，改库存要重启整个应用。

### 问题

- 部署耦合：小模块也要全量发布
- 资源隔离差：报表查询拖垮订单创建
- 团队边界不清：所有人改同一个仓库

### 重构步骤

**阶段 1：新服务搭建，不接流量**

新建 `inventory-service` 独立应用，先把库存逻辑搬过去（不接流量，独立可测）。

```
monolith/
└── inventory/（旧实现，保留）
inventory-service/（新实现，独立部署）
```

**阶段 2：Strangler Fig 接入**

单体里的库存调用改成双写 + 影子：

```java
public void updateStock(String sku, int qty) {
    legacyInventoryRepo.update(sku, qty);              // 旧实现，仍用
    inventoryServiceClient.update(sku, qty);            // 新服务，影子调用
    // 影子只记录结果，不影响返回
}
```

对比两边数据一致性，发现问题先修新服务。

**阶段 3：灰度切流量**

加 feature flag，按 SKU 或百分比切到新服务：

```java
public void updateStock(String sku, int qty) {
    if (featureFlag.useNewInventory(sku)) {  // 灰度
        inventoryServiceClient.update(sku, qty);
    } else {
        legacyInventoryRepo.update(sku, qty);
    }
}
```

灰度期间监控新服务的延迟、错误率、数据一致性。

**阶段 4：下线旧实现**

灰度 100% 稳定后，删 `monolith/inventory/`，旧代码彻底下线。

### 验证

- 每阶段独立可测可回滚
- 灰度期间数据一致性 99.99%+
- 新服务延迟 < 旧实现

### 结果

3 个月完成拆分，库存服务独立部署，团队边界清晰。

---

## 案例 4：补测试再重构（无测试的遗留代码）

### 现状

`PricingEngine` 500 行，零测试，纯遗留代码。要加新计价规则，不敢改。

### 问题

- 没测试，改了不知道有没有破坏现有行为
- 方法内部 new 依赖（`new DateUtil()`、`new DiscountCalculator()`），无法 mock

### 重构步骤

**阶段 1：找接缝点**

`compute` 方法内部 new 了 `DiscountCalculator`，这是接缝点——可以提取接口注入。

```java
// 原代码
public double compute(Order order) {
    DiscountCalculator dc = new DiscountCalculator(order);  // 硬 new
    double base = order.getAmount();
    double discount = dc.calculate();
    return base - discount + TaxUtil.compute(base - discount);  // static
}
```

**阶段 2：打特征测试（先解依赖）**

用"参数化方法"打破硬依赖：

```java
// 提取可注入版本
public double compute(Order order) {
    return compute(order, new DiscountCalculator(order), TaxUtil::compute);
}

// 包含依赖的版本，可测
double compute(Order order, DiscountCalculator dc, Function<Double, Double> taxFn) {
    double base = order.getAmount();
    double discount = dc.calculate();
    return base - discount + taxFn.apply(base - discount);
}
```

打特征测试，记录所有边界：

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

**阶段 3：小步重构**

特征测试保底后，开始重构：
- 提取方法：把 tax 计算提成 `calculateTax(double)`
- 提取常量：`0.1` 提成 `DISCOUNT_RATE`
- 简化条件：用早返回减少嵌套

每步 commit + 跑特征测试，绿就继续。

**阶段 4：补正式测试**

行为稳定后，补覆盖新逻辑的正式测试（不再只是特征测试）：

```java
@Test
void compute_vipCustomer_getsExtraDiscount() {
    Order o = new Order(100, CustomerType.VIP);
    double r = engine.compute(o);
    assertEquals(80.0, r);  // VIP 多 10% off
}
```

**阶段 5：加新功能**

测试到位后，安全地加新计价规则。

### 验证

- 特征测试全绿（行为未变）
- 新测试覆盖新规则
- 重构期间零事故

### 结果

零测试 → 80% 覆盖率，后续加新规则安全可测。

## 案例通用流程

| 阶段 | 动作 | 关键 |
|---|---|---|
| 1. 现状评估 | 看代码规模、测试覆盖、依赖耦合 | 确定重构级别 L1-L4 |
| 2. 补测试 | 有测试直接补；无测试打特征测试 | 先解依赖再打测试 |
| 3. 小步重构 | 每次一个原子动作 | commit + 测试 |
| 4. 验证 | 全量测试 + 集成测试 | 行为不变 |
| 5. 收尾 | 删死代码、删特征测试 | 重构完成 |
