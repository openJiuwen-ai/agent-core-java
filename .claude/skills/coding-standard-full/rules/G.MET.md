# G.MET Methods 方法

共 7 条规则。

## `G.MET.01 方法要简短--方法的代码块嵌套深度不应超过4层` 🟢 `common_standard_recommend`

复杂过长的方法往往意味着方法抽象层次不足或功能不够单一。建议方法要进行合理抽象分层，对功能不够单一的方法使用合理的手段进行重构，以提升代码的可读性、可维护性。

可以考虑从以下维度间接约束方法的尺寸和复杂度：

* 方法行数建议不超过50行（非空非注释）；
* 方法的参数，建议不超过5个；
* 方法最大代码块嵌套深度，建议不要超过4层。

方法参数

方法的参数过多，会使得该方法易于受外部（其他部分的代码）变化的影响，从而影响维护工作，同时也会增大测试的工作量。方法的参数个数如果超过5个，可以考虑如下优化方式：

* 对方法进行抽象或重构；
* 将相关参数合在一起，定义成类，用对象封装；
* 当构造方法含有多个参数时，尝试建造者（Builder）或工厂模式，JDK源码中有很多示例可供参考，例如Calendar.Builder，HttpClient.Builder。

代码块嵌套深度

方法的代码块嵌套深度指的是方法中的代码控制块（例如：if、for、while、switch等）之间互相包含的深度。方法本身算一层，try-catch不算一层嵌套。方法内的lambda表达式、局部类和匿名类嵌套层次以最内层方法来计算，不累计enclosing method的嵌套层次。使用卫语句可以有效的减少if相关的嵌套层次。

该规则有可配置参数，具体配置详见规则集中该规则的选项配置。

**修改建议：** 重构和简化方法。

✅ **正确示例：**

```java
private static boolean checkNum(List<Object> list) {
    ...
    if (list.size() <= ZERO) {
        return false;
    }
    if (list.size() == ONE) {
        return false;
    }
    if (list.size() == TWO) {
        return false;
    }
    return true;
}
```

❌ **错误示例：**

```java
private static boolean checkNum(List<Object> list) {
...
    if (list.size() <= ZERO) {
        return false;
    } else {
        if (list.size() == ONE) {
            return false;
        } else if (list.size() == TWO) {
            return false;
        } else {
            return true;
        }
    }
}
```

---

## `G.MET.01 方法要简短--方法行数不应超过50行` 🟢 `common_standard_recommend`

复杂过长的方法往往意味着方法**抽象层次不足**或**功能不够单一**。建议方法要进行合理抽象分层，对功能不够单一的方法使用合理的手段进行重构，以提升代码的可读性、可维护性。

工具会对方法中的代码行数超过maxMethodLength（默认值为50，支持动态配置）时进行告警。

该规则有可配置参数，具体配置详见规则集中该规则的选项配置。

**修改建议：** 将单个超大方法分解成多个方法。

✅ **正确示例：**

##### 场景1：超大方法
  ```java
  int getSum(){
      int a = getA()
      int b = getB()
      return a + b    
  }
  ```

❌ **错误示例：**

##### 场景1：超大方法
  ```java
  // 方法中代码行数超过50行
  int getSum(){
      // xxx
      ...
  }
  ```

---

## `G.MET.01 方法要简短--方法的参数不应超过5个` 🟢 `common_standard_recommend`

复杂过长的方法往往意味着方法抽象层次不足或功能不够单一。建议方法要进行合理抽象分层，对功能不够单一的方法使用合理的手段进行重构，以提升代码的可读性、可维护性。

可以考虑从以下维度间接约束方法的尺寸和复杂度：

* 方法行数建议不超过50行（非空非注释）；
* 方法的参数，建议不超过5个；
* 方法最大代码块嵌套深度，建议不要超过4层。

方法参数

方法的参数过多，会使得该方法易于受外部（其他部分的代码）变化的影响，从而影响维护工作，同时也会增大测试的工作量。方法的参数个数如果超过5个，可以考虑如下优化方式：

* 对方法进行抽象或重构；
* 将相关参数合在一起，定义成类，用对象封装；
* 当构造方法含有多个参数时，尝试建造者（Builder）或工厂模式，JDK源码中有很多示例可供参考，例如Calendar.Builder，HttpClient.Builder。

代码块嵌套深度

方法的代码块嵌套深度指的是方法中的代码控制块（例如：if、for、while、switch等）之间互相包含的深度。方法本身算一层，try-catch不算一层嵌套。方法内的lambda表达式、局部类和匿名类嵌套层次以最内层方法来计算，不累计enclosing method的嵌套层次。使用卫语句可以有效的减少if相关的嵌套层次。

该规则有可配置参数，具体配置详见规则集中该规则的选项配置。

**修改建议：** 重构和简化方法。

✅ **正确示例：**

```java
class Parameters {
    private int var1;
    private int var2;
    private int var3;
    private int var4;
    private int var5;
    private int var6;
    ...
}
public void doSomething(Parameters parameters) {
...
}
```

❌ **错误示例：**

```java
public void doSomething(int var1, int var2, int var3, int var4, int var5, int var6) {
...
}
```

---

## `G.MET.05 对于返回数组或者容器的方法，应返回长度为0的数组或者容器，代替返回null` 🟢 🔴[安全] `common_standard_recommend`

在方法返回值中，用长度为`0`的数组或者容器，来代替返回`null`，则上层调用代码在使用此返回的数组或者容器前，无需再判断是否为空，业务逻辑一气呵成，代码更简洁。

同时，也避免了程序员因为忘记了对返回值进行空指针检查，而导致的NullPointerException。

**修改建议：** 用长度为`0`的数组或者容器，来代替返回`null`,或者通过@Nullable标明返回值可以为`null`

✅ **正确示例：**

- 修复示例1：返回空的集合

  ```java
    public static List<String> decorate(String[] personDescs) {
        if (personDescs == null || personDescs.length == 0) {
            return Collections.emptyList(); // 返回空的集合，上层调用不需要判空，代码书写更流畅
        }
        List<String> personNames = new ArrayList<>(personDescs.length);
        for (String personDesc : personDescs) {
            String personName = getPersonName(personDesc);
            personNames.add(personName);
        }
        return personNames;
    }
  ```
- 修复示例2：通过`@Nullable`标明返回值可以为`null`

  ```java
    @Nullable
    public static List<String> decorate(String[] personDescs) {
        if (personDescs == null || personDescs.length == 0) {
            return null; // 返回null，上层代码需要对该返回值判null，否则会出现NPE
        }
        List<String> personNames = new ArrayList<>(personDescs.length);
        for (String personDesc : personDescs) {
            String personName = getPersonName(personDesc);
            personNames.add(personName);
        }
        return personNames;
    }
  ```

❌ **错误示例：**

  ```java
    public static List<String> decorate(String[] personDescs) {
        if (personDescs == null || personDescs.length == 0) {
            return null; // 返回null，上层代码需要对该返回值判null，否则会出现NPE
        }
        List<String> personNames = new ArrayList<>(personDescs.length);
        for (String personDesc : personDescs) {
            String personName = getPersonName(personDesc);
            personNames.add(personName);
        }
        return personNames;
    }
  ```

---

## `G.MET.03 不应把方法的参数当做临时变量` 🟢 `common_standard_recommend`

不应把方法的参数当做临时变量，因为每个变量/参数都有自己独特的功用，让一个变量承担多个职责，变量名将无法清晰表达其功能，会使程序难以理解。

**修改建议：** 在方法体中，应该避免对方法参数进行修改，包括重新赋值、自增/自减运算。如果需要，可以定义一个新的临时变量替代方法参数。

✅ **正确示例：**

- 修复示例：在方法中定义临时变量替代方法参数

```java
// 使用final能够帮助判断，避免意外修改。
int doSomething(final int basicSalary) {
    int performanced = basicSalary * getMultiplier(basicSalary);
    int bonused = performanced + getAdder(performanced);
    ...
    return bonused;
}
```

❌ **错误示例：**

- 错误示例：方法参数用作临时变量，在方法中对其进行了修改

```java
// 方法参数用作临时变量。
int doSomething(int inputData) {
    inputData = inputData * getMultiplier(inputData);
    inputData = inputData + getAdder(inputData);
    return inputData;
}
```

---

## `G.MET.06 使用Optional代替null作为返回值或者可能的缺失值；禁止对Optional对象赋值为null` 🟡 `common_standard_recommend`

使用`Optional`代替`null`作为返回值或者可能的缺失值；禁止对`Optional`对象赋值为`null`
工具检查场景:
- 检查是否将`null`赋值给`Optional`对象
- 检查是否将`Optional`对象和`null`进行比较（==或!=）
- 检查方法返回类型是否为`Optional<Integer>`、`Optional<Long>`或`Optional<Double>`
- 检查方法返回类型是否为`Optional<集合或数组>`

**修改建议：** **禁止对Optional对象赋值/返回为null，或与null比较**，例如: `Optional<Foo> foo = null;`；

不应该返回`Optional<Integer>`、`Optional<Long>`、`Optional<Double>`，而应该使用`OptionalInt`、`OptionalLong`、`OptionalDouble` ；

一般不应该返回`Optional<集合或数组>`，而用空集合或空数组替代。

✅ **正确示例：**

##### 场景1：对于返回集合类型，应返回空集合
- 修复示例：返回空的集合

  ```java
    public Optional<List<String>> doSomething() {
        ...
        return Collections.emptyList();
    }
  ```
##### 场景2：对于包装类型应该使用OptionalInt、OptionalLong、OptionalDouble
- 修复示例：使用OptionalInt

  ```java
    public OptionalInt doSomething() {
        ...
        return OptionalInt.of(1);
    }
  ```
##### 场景3：禁止对Optional对象赋值/返回为null
- 修复示例：返回Optional.empty()
  ```java
    public OptionalInt doSomething() {
        ...
        return Optional.empty();
    }
  ```
##### 场景4：禁止Optional与null进行比较
- 修复示例：使用Optional.empty()来做判空逻辑
  ```java
    public void doSomething(Optional<String> opt) {
        ...
        if(opt.isEmpty()) {
        ...
        }
    }
  ```

❌ **错误示例：**

##### 场景1：对于返回集合类型，应返回空集合
- 错误示例：使用了Optional<集合或数组>

  ```java
    public Optional<List<String>> doSomething() {
        ...
        return Optional.empty();
    }
  ```
##### 场景2：对于包装类型应该使用OptionalInt、OptionalLong、OptionalDouble
- 错误示例：使用了Optional<Integer>

  ```java
    public Optional<Integer> doSomething() {
        ...
        return Optional.of(1);
    }
  ```
##### 场景3：禁止对Optional对象赋值/返回为null
- 错误示例：返回值为Optional<>的方法返回了null

  ```java
    public Optional<String> doSomething() {
        ...
        return null;
    }
  ```
##### 场景4：禁止Optional与null进行比较
- 错误示例：将`Optional`对象和`null`进行比较（==或!=）

  ```java
    public void doSomething(Optional<String> opt) {
        ...
        if(opt==null) {
        ...
        }
    }
  ```

---

## `G.MET.04 谨慎使用可变数量参数` 🟡 🔴[安全] `common_standard_recommend`

在Java 5版本中初次引入可变数量参数（variable number of arguments）特性，该特性支持方法接受指定类型的零个到多个参数。使用可变数量参数时，要注意如下两类问题：
1. 不建议使用可变数据参数方法重写使用一个固定长度数组作为参数的方法，这样会导致代码可读性变差；
2. 可变类型参数的类型要明确，避免使用Object等模糊类型，方便Java编译器对参数类型进行检查。

**修改建议：** 1. 删除对可变数量参数方法进行重载的方法（参考场景1）。

2. 明确可变类型参数的类型，避免使用Object等模糊类型（参考场景2）。

✅ **正确示例：**

##### 场景1：对可变数量参数方法进行重写
```java
public double sum(double... values) {
    ...
}

// 删除对可变数量参数方法进行重写的方法。
```
##### 场景2：可变数量参数的类型为Object
```java
// 可变类型参数的类型明确，方便Java编译器对参数类型进行检查。
public void doSomething(String... args) {
    ...
}
```

❌ **错误示例：**

##### 场景1：对可变数量参数方法进行重写
- 错误示例：对可变数量参数方法进行了重载，可能会导致对于sum(23d, 32d)不确定实际执行的是哪个方法。
```java
public double sum(double... values) {
    ...
}

// 对可变数量参数方法sum()进行了重载。
public double sum(double value1, double value2) {
    ...
}
```
##### 场景2：可变数量参数的类型为Object
```java
// 可变类型使用Object等模糊类型，不方便Java编译器对参数类型进行检查。
public void doSomething(Object... args) {
    ...
}
```

---
