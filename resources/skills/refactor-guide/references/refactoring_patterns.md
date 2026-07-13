# 重构手法详解

本文件补充 SKILL.md 的"重构动作清单"，给每个手法"重构前/重构后"代码对比和步骤。用户问"某手法怎么做"时按需读取。

## 1. 提取方法（Extract Method）

**动机**：方法太长，一段代码做了独立的事情，方法名能表达意图。

**重构前**：
```java
public void printOrder(Order order) {
    System.out.println("Order: " + order.getId());
    System.out.println("Customer: " + order.getCustomerName());
    System.out.println("Total: " + order.getTotal());

    double tax = order.getTotal() * 0.1;
    double discount = order.getTotal() * 0.05;
    System.out.println("Tax: " + tax);
    System.out.println("Discount: " + discount);
    System.out.println("Final: " + (order.getTotal() + tax - discount));
}
```

**重构后**：
```java
public void printOrder(Order order) {
    printOrderHeader(order);
    printOrderPricing(order);
}

private void printOrderHeader(Order order) {
    System.out.println("Order: " + order.getId());
    System.out.println("Customer: " + order.getCustomerName());
    System.out.println("Total: " + order.getTotal());
}

private void printOrderPricing(Order order) {
    double tax = calculateTax(order.getTotal());
    double discount = calculateDiscount(order.getTotal());
    System.out.println("Tax: " + tax);
    System.out.println("Discount: " + discount);
    System.out.println("Final: " + (order.getTotal() + tax - discount));
}

private double calculateTax(double total) { return total * 0.1; }
private double calculateDiscount(double total) { return total * 0.05; }
```

**步骤**：
1. 创建新方法，名字表达"做什么"而非"怎么做"
2. 把代码段搬进去，注意局部变量是否要作为参数传入
3. 原位置替换为方法调用
4. 跑测试

## 2. 内联方法（Inline Method）

**动机**：方法体比方法名还清楚，或方法只被调用一次，去掉间接层。

**重构前**：
```java
public double getRating() {
    return moreThanFiveLateDeliveries() ? 2 : 1;
}
private boolean moreThanFiveLateDeliveries() {
    return numberOfLateDeliveries > 5;
}
```

**重构后**：
```java
public double getRating() {
    return numberOfLateDeliveries > 5 ? 2 : 1;
}
```

**步骤**：
1. 确认方法不被多态覆写
2. 找到所有调用点
3. 用方法体替换调用
4. 删除方法
5. 跑测试

## 3. 提取类（Extract Class）

**动机**：一个类做了两个职责，违反单一职责。

**重构前**：
```java
public class Person {
    private String name;
    private String officeAreaCode;
    private String officeNumber;

    public String getTelephoneNumber() {
        return "(" + officeAreaCode + ") " + officeNumber;
    }
    // name 相关方法 + office 相关方法混在一起
}
```

**重构后**：
```java
public class Person {
    private String name;
    private TelephoneNumber officeTelephone;

    public String getTelephoneNumber() {
        return officeTelephone.getTelephoneNumber();
    }
}

public class TelephoneNumber {
    private String areaCode;
    private String number;

    public String getTelephoneNumber() {
        return "(" + areaCode + ") " + number;
    }
}
```

**步骤**：
1. 定义新类，把相关字段搬过去
2. 旧类持有新类引用（组合）
3. 旧类方法 delegate 到新类
4. 逐步迁移调用方直接用新类
5. 跑测试

## 4. 搬移函数（Move Method）

**动机**：方法在 A 类，但用的数据都在 B 类，应该搬到 B。

**重构前**：
```java
public class Account {
    private AccountType type;
    private int daysOverdrawn;

    public double overdraftCharge() {
        // 用 type 的数据，不用 Account 的其他字段
        if (type.isPremium()) {
            double result = 10;
            if (daysOverdrawn > 7) result += (daysOverdrawn - 7) * 0.85;
            return result;
        }
        return daysOverdrawn * 1.75;
    }
}
```

**重构后**：
```java
public class AccountType {
    public double overdraftCharge(int daysOverdrawn) {  // 搬到这里
        if (isPremium()) {
            double result = 10;
            if (daysOverdrawn > 7) result += (daysOverdrawn - 7) * 0.85;
            return result;
        }
        return daysOverdrawn * 1.75;
    }
}

public class Account {
    private AccountType type;
    private int daysOverdrawn;

    public double overdraftCharge() {
        return type.overdraftCharge(daysOverdrawn);  // delegate
    }
}
```

**步骤**：
1. 在目标类创建新方法，复制逻辑，调整参数
2. 旧方法改为 delegate 调用
3. 逐步让调用方直接调新方法
4. 旧方法无人调用后删除
5. 跑测试

## 5. 以多态替代条件（Replace Conditional with Polymorphism）

**动机**：switch/if-else 根据类型分支，每加一个类型要改所有分支。

**重构前**：
```java
public double getSpeed() {
    switch (type) {
        case EUROPEAN: return getBaseSpeed();
        case AFRICAN: return getBaseSpeed() - getLoadFactor() * numberOfCoconuts;
        case NORWEGIAN: return isNailed ? 0 : getBaseSpeed(voltage);
        default: throw new IllegalStateException();
    }
}
```

**重构后**：
```java
abstract class Bird {
    abstract double getSpeed();
}
class European extends Bird {
    double getSpeed() { return getBaseSpeed(); }
}
class African extends Bird {
    double getSpeed() { return getBaseSpeed() - getLoadFactor() * numberOfCoconuts; }
}
class Norwegian extends Bird {
    double getSpeed() { return isNailed ? 0 : getBaseSpeed(voltage); }
}
```

**步骤**：
1. 为每个类型创建子类
2. 用工厂方法替代构造
3. 把每个分支搬到对应子类
4. 父类方法改 abstract
5. 跑测试

## 6. 以策略替代 switch（Strategy Pattern）

**动机**：switch 分支会扩展，且不想每次加分支都改类。

**重构前**：
```java
public double calculate(PriceType type, double base) {
    switch (type) {
        case NORMAL: return base;
        case DISCOUNT: return base * 0.9;
        case VIP: return base * 0.8;
        default: throw new IllegalArgumentException();
    }
}
```

**重构后**：
```java
interface PriceStrategy { double calculate(double base); }

class NormalPrice implements PriceStrategy {
    public double calculate(double base) { return base; }
}
class DiscountPrice implements PriceStrategy {
    public double calculate(double base) { return base * 0.9; }
}
class VipPrice implements PriceStrategy {
    public double calculate(double base) { return base * 0.8; }
}

// 注册到 Map，新策略只加不改
Map<PriceType, PriceStrategy> strategies = Map.of(
    NORMAL, new NormalPrice(),
    DISCOUNT, new DiscountPrice(),
    VIP, new VipPrice()
);
public double calculate(PriceType type, double base) {
    return strategies.get(type).calculate(base);
}
```

**与多态替代条件的区别**：策略模式用组合，运行时切换；多态用继承，编译时确定。

## 7. 封装参数对象（Introduce Parameter Object）

**动机**：参数太多且常一起出现。

**重构前**：
```java
public Order createOrder(String customerId, String productId, int qty,
                         double price, String address, String coupon) { ... }
```

**重构后**：
```java
public class OrderRequest {
    private String customerId;
    private String productId;
    private int qty;
    private BigDecimal price;  // 顺带把 double 改成 BigDecimal
    private String address;
    private String coupon;
    // builder
}
public Order createOrder(OrderRequest req) { ... }
```

**注意**：不要造"参数袋"（一个类装所有方法的参数），每个方法有自己的参数对象。

## 8. 以查询替代临时变量（Replace Temp with Query）

**动机**：临时变量赋值后只读一次，用查询方法更清晰。

**重构前**：
```java
double basePrice = quantity * itemPrice;
if (basePrice > 1000) {
    return basePrice * 0.95;
} else {
    return basePrice * 0.98;
}
```

**重构后**：
```java
if (basePrice() > 1000) {
    return basePrice() * 0.95;
} else {
    return basePrice() * 0.98;
}
private double basePrice() {
    return quantity * itemPrice;
}
```

**注意**：如果计算很重或会被多次调用，保留临时变量更好（性能）。

## 9. 引入解释变量（Introduce Explaining Variable）

**动机**：复杂表达式难以理解，拆成有名字的变量。

**重构前**：
```java
if ((platform.toUpperCase().indexOf("MAC") > -1) &&
    (browser.toUpperCase().indexOf("IE") > -1) &&
    wasInitialized() && resize > 0) {
    // do something
}
```

**重构后**：
```java
boolean isMacOs = platform.toUpperCase().indexOf("MAC") > -1;
boolean isIEBrowser = browser.toUpperCase().indexOf("IE") > -1;
boolean wasResized = resize > 0;
if (isMacOs && isIEBrowser && wasInitialized() && wasResized) {
    // do something
}
```

**与"以查询替代临时变量"区别**：解释变量是表达式太复杂时拆；替代临时变量是变量只用一次时提成方法。
