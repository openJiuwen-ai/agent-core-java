# Refactoring Techniques in Detail

This file supplements the "Refactoring Action List" in SKILL.md, providing "before/after" code comparison and steps for each technique. Read on demand when users ask "how to do a specific technique."

## 1. Extract Method

**Motivation**: Method is too long; a section of code does an independent thing; the method name can express intent.

**Before**:
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

**After**:
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

**Steps**:
1. Create a new method whose name expresses "what it does" rather than "how it does it"
2. Move the code segment into it; note whether local variables need to be passed as parameters
3. Replace the original location with a method call
4. Run tests

## 2. Inline Method

**Motivation**: The method body is clearer than the method name, or the method is only called once; remove the indirection.

**Before**:
```java
public double getRating() {
    return moreThanFiveLateDeliveries() ? 2 : 1;
}
private boolean moreThanFiveLateDeliveries() {
    return numberOfLateDeliveries > 5;
}
```

**After**:
```java
public double getRating() {
    return numberOfLateDeliveries > 5 ? 2 : 1;
}
```

**Steps**:
1. Confirm the method is not overridden polymorphically
2. Find all call sites
3. Replace calls with the method body
4. Delete the method
5. Run tests

## 3. Extract Class

**Motivation**: A class does two responsibilities, violating Single Responsibility.

**Before**:
```java
public class Person {
    private String name;
    private String officeAreaCode;
    private String officeNumber;

    public String getTelephoneNumber() {
        return "(" + officeAreaCode + ") " + officeNumber;
    }
    // name-related methods + office-related methods mixed together
}
```

**After**:
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

**Steps**:
1. Define a new class, move related fields to it
2. Old class holds a reference to the new class (composition)
3. Old class methods delegate to the new class
4. Gradually migrate callers to use the new class directly
5. Run tests

## 4. Move Method

**Motivation**: A method is in class A, but the data it uses is all in class B; it should be moved to B.

**Before**:
```java
public class Account {
    private AccountType type;
    private int daysOverdrawn;

    public double overdraftCharge() {
        // Uses type's data, not Account's other fields
        if (type.isPremium()) {
            double result = 10;
            if (daysOverdrawn > 7) result += (daysOverdrawn - 7) * 0.85;
            return result;
        }
        return daysOverdrawn * 1.75;
    }
}
```

**After**:
```java
public class AccountType {
    public double overdraftCharge(int daysOverdrawn) {  // Moved here
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

**Steps**:
1. Create a new method in the target class, copy the logic, adjust parameters
2. Change the old method to a delegate call
3. Gradually have callers invoke the new method directly
4. Delete the old method after no one calls it
5. Run tests

## 5. Replace Conditional with Polymorphism

**Motivation**: switch/if-else branches on type; adding a new type requires changing all branches.

**Before**:
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

**After**:
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

**Steps**:
1. Create a subclass for each type
2. Replace construction with a factory method
3. Move each branch to the corresponding subclass
4. Change the parent class method to abstract
5. Run tests

## 6. Replace Switch with Strategy Pattern

**Motivation**: Switch branches will expand, and you don't want to modify the class every time a branch is added.

**Before**:
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

**After**:
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

// Register in Map, new strategies are added without modification
Map<PriceType, PriceStrategy> strategies = Map.of(
    NORMAL, new NormalPrice(),
    DISCOUNT, new DiscountPrice(),
    VIP, new VipPrice()
);
public double calculate(PriceType type, double base) {
    return strategies.get(type).calculate(base);
}
```

**Difference from Replace Conditional with Polymorphism**: Strategy pattern uses composition, switchable at runtime; polymorphism uses inheritance, determined at compile time.

## 7. Introduce Parameter Object

**Motivation**: Too many parameters that frequently appear together.

**Before**:
```java
public Order createOrder(String customerId, String productId, int qty,
                         double price, String address, String coupon) { ... }
```

**After**:
```java
public class OrderRequest {
    private String customerId;
    private String productId;
    private int qty;
    private BigDecimal price;  // Also changed double to BigDecimal
    private String address;
    private String coupon;
    // builder
}
public Order createOrder(OrderRequest req) { ... }
```

**Note**: Do not create a "parameter bag" (one class holding parameters for all methods); each method should have its own parameter object.

## 8. Replace Temp with Query

**Motivation**: A temporary variable is assigned and only read once; a query method is clearer.

**Before**:
```java
double basePrice = quantity * itemPrice;
if (basePrice > 1000) {
    return basePrice * 0.95;
} else {
    return basePrice * 0.98;
}
```

**After**:
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

**Note**: If the computation is heavy or will be called multiple times, keeping the temporary variable is better (performance).

## 9. Introduce Explaining Variable

**Motivation**: Complex expressions are hard to understand; break them into named variables.

**Before**:
```java
if ((platform.toUpperCase().indexOf("MAC") > -1) &&
    (browser.toUpperCase().indexOf("IE") > -1) &&
    wasInitialized() && resize > 0) {
    // do something
}
```

**After**:
```java
boolean isMacOs = platform.toUpperCase().indexOf("MAC") > -1;
boolean isIEBrowser = browser.toUpperCase().indexOf("IE") > -1;
boolean wasResized = resize > 0;
if (isMacOs && isIEBrowser && wasInitialized() && wasResized) {
    // do something
}
```

**Difference from "Replace Temp with Query"**: Explaining variable splits when an expression is too complex; Replace Temp with Query promotes to a method when a variable is only used once.
