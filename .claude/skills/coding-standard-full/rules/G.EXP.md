# G.EXP Expressions 表达式

共 6 条规则。

## `G.EXP.03 条件表达式?:的第2和第3个操作数应使用相同的类型` 🟡 🔴[安全] `common_standard_recommend`

条件运算符?:使用第1个操作数的布尔值决定后续表达式哪个被执行。但是Java语言有相当复杂的规则去判定表达式的结果类型，不一致的操作数类型，可能导致意料之外的类型转换。如第2和第3个操作数在类型对齐时，可能会因为自动拆箱导致NullPointerException。

**修改建议：** 三目运算符(`?:`)的第2和第3个操作数类型要保持一致。

✅ **正确示例：**

##### 场景1：表达式?:的第2和第3个操作数类型不一致
  ```java
  char ch = 'A';
  int value = 50;
  boolean condition = ...; 
  System.out.println(condition ? ch : ((char) value));  // condition为true时，输出A ； 为false 时，输出 2
  Integer integer = null;
  System.out.print(condition ? integer : Integer.valueOf(value)); // condition为true时，输出 null
  ```

❌ **错误示例：**

##### 场景1：表达式?:的第2和第3个操作数类型不一致
  ```java
  char ch = 'A';
  int value = 50;
  boolean condition = ...; 
  System.out.println(condition ? ch : value); // condition为true时，输出65，自动将char类型转为int类型 ；为false 时，输出 50
  Integer integer = null;
  System.out.print(condition ? integer : value); // condition为true时，抛出NullPointerException
  ```

---

## `G.EXP.06 代码中不应使用断言（assert）` 🟡 🔴[安全] `security_standard_recommend`

使用assert不当。

默认情况下，断言（assert）是被禁用的，可以通过-ea或-da选项开启或关闭。断言（assert）的判断条件为false时会抛出 AssertionError ，表示程序遇到了一个不可恢复的错误，对该错误不做处理会导致程序异常退出。断言（assert）只适用于开发调试阶段的问题定位。以下两种场景不应使用断言：

* 运行态错误检查：如下常见的运行态错误检查，不应使用断言（assert），否则可能因为运行态错误触发 AssertionError 导致程序异常终止或因为断言（assert）禁用而导致错误未处理。

1. 无效的用户输入（如环境变量、命令行参数等）;

2. IO错误（如文件操作、网络通信等）;

3. 权限不足（如文件权限、用户权限等）;

4. Java虚拟机运行时错误（如堆栈溢出等）;

5. 系统资源耗尽（如文件句柄数不足等）。

* 逻辑代码执行：在断言（assert）中执行业务逻辑代码，会导致程序因为断言（assert）的启用/禁用产生不同的逻辑。

**修改建议：** 不应使用断言。

✅ **正确示例：**

##### 场景1：使用了断言的代码。
- 修复示例1：使用其他方法代替断言，如if语句：
```java
String data = null;
...
if (data != null) {
    ...  
}
```

❌ **错误示例：**

##### 场景1：使用了断言的代码。
- 错误示例：使用断言。

```java
String data = null;
...
assert data != null;
...
```

---

## `G.EXP.02 用括号明确表达式的操作顺序，避免过分依赖默认优先级` 🟢 🔴[安全] `common_standard_recommend`

涉及多种操作符混合使用并且优先级容易混淆的场景，建议使用括号明确表达式操作顺序。
```java
foo = a + b + c;    // 运算符相同，不需要括号
if (a && b && c)    // 运算符相同，不需要括号
foo = 1 << (2 + 3); // 运算符不同，优先级易混淆，需要括号
```

**修改建议：** 用括号明确表达式的操作顺序，避免过分依赖默认优先级

✅ **正确示例：**

##### 场景1：未用括号明确表达式的操作顺序，可能导致误读
  ```java
  System.out.println(1 << (2 + 3)); // 运算符不同，优先级易混淆，需要括号
  ```

❌ **错误示例：**

##### 场景1：未用括号明确表达式的操作顺序，可能导致误读
  ```java
  System.out.println(1 << 2 + 3);
  ```

---

## `G.EXP.04 表达式的比较，应该遵循左侧倾向于变化、右侧倾向于不变的原则--表达式比较左变右不变` 🟡 `common_standard_recommend`

当变量或方法调用与常量比较时，如果常量放左边，如`if (MAX == v)`不符合阅读习惯，而`if (MAX > v)`更是难于理解。

应该按人的正常阅读、表达习惯，将常量放右边。由于现代IDE都有较为强大的NullPointerException检测能力，可以考虑显式地注解`@NotNull`。

1. 对于`==`，变量放在左边，null或常量放在右边;
2. 如果变量明显不会为null，例如new、单例、非空注解后，可用`obj.equals("foo")`；如果必须使用null，或者这个变量有可能是null，应该使用`Objects.equals(variable, "foo")`或者显式用if判断或`"foo".equals(obj)`;
3. 描述区间时，前半段表达式常量在左，也是允许的，如`if (MIN < bar && bar < MAX)`。

**修改建议：** 应该把常量放在比较表达式的右边

✅ **正确示例：**

##### 场景1：常量放在比较表达式的右边
  ```java
  if (var1 < CONST_VAR) {
      ...
  }
  ```

❌ **错误示例：**

##### 场景1：常量放在比较表达式的左边
  ```java
  if (CONST_VAR > var1) {
      ...
  }
  ```

---

## `G.EXP.01 不要在单个表达式中对相同的变量赋值超过一次` 🟠 🔴[安全] `common_standard_rule`

对相同的变量进行多次赋值的表达式会产生混淆，并且容易产生非预期的行为。清晰的变量赋值会使代码更易懂，也更能保证程序按预期运行。

**修改建议：** 不要在单个表达式中对相同的变量赋值超过一次

✅ **正确示例：**

##### 场景1：使用count对循环计数，而实际count最终结果却为0
  ```java
  int count = 0;
  for (int i = 0; i < 100; i++) {
      ...
      count++;
  }
  System.out.println(count); // count的值为100   
  ```

❌ **错误示例：**

##### 场景1：使用count对循环计数，而实际count最终结果却为0
  ```java
  int count = 0;
  for (int i = 0; i < 100; i++) {
      ...
      count = count++;
  }
  System.out.println(count); // count的值为0
  ```

---

## `G.EXP.04 表达式的比较，应该遵循左侧倾向于变化、右侧倾向于不变的原则--使用equals方法进行字符串比较` 🟡 `common_standard_recommend`

判断两个字符串的内容是否相等，应该使用`equals()`方法，而不能使用`==`运算符。`==`运算符会比较两个字符串的指针地址是否相同，而非字符串中的内容是否相同。

**修改建议：** 使用`equals()`方法比较两个字符串的内容是否相同；当存在String常量时，常量要在放在表达式的左侧。

✅ **正确示例：**

##### 场景1：使用equals比较字符串
  ```java
  String localVariable = "yes";
  System.out.println(STRING_CONSTANT.equals(localVariable));

  System.out.println(!STRING_CONSTANT.equals(localVariable));
  ```

##### 场景2：常量放在比较表达式的左边
  ```java
  String localVariable = "yes";
  System.out.println("world".equals(localVariable));
  ```
  ```java
  String localVariable = "yes";
  System.out.println(STRING_CONSTANT.equals(localVariable));
  ```

❌ **错误示例：**

##### 场景1：不要使用 == 和 != 比较字符串

  ```java
  String localVariable = "yes";
  System.out.println(localVariable == STRING_CONSTANT);
  System.out.println(STRING_CONSTANT == localVariable);

  System.out.println(localVariable != STRING_CONSTANT);
  System.out.println(STRING_CONSTANT != localVariable);
  ```

##### 场景2：常量放在比较表达式的右边
  ```java
  String localVariable = "yes";
  System.out.println(localVariable.equals("world"));
  ```

  ```java
  String localVariable = "yes";
  System.out.println(localVariable.equals(STRING_CONSTANT));
  ```

---
