# G.TYP Types 类型

共 11 条规则。

## `G.TYP.11 基本类型优于包装类型，注意合理使用包装类型` 🟡 🔴[安全] `common_standard_recommend`

Java有两种类型，基本类型（Primitive type）和引用类型（Reference type）。基本类型如`boolean`、`int`、`double`，引用类型如`String`、`List`。每一种基本类型都有其对应的包装类型（Wrapper classes），如对应`int`的是`Integer`。
检查如下场景：
1，for循环变量使用封装类
2，封装类直接使用`==`比较

**修改建议：** 尽量直接使用基本类型

✅ **正确示例：**

- 修复示例：for循环变量应该使用基本类型

  ```java
  for(int i = 0; i < 10; i++) {
      i++;
  }
  ```
- 修复示例：封装类的比较应该使用equals()方法

  ```java
  Integer var1=1;
  Integer var2=2;
  if (var.equals(var2)) {
    ...
  }
  ```

❌ **错误示例：**

- 错误示例：for循环变量使用封装类

  ```java
  for(Integer i = 0; i < 10; i++) {
      i++;
  }
  ```
- 错误示例：封装类直接使用`==`比较

  ```java
  Integer var1=1;
  Integer var2=2;
  if (var == var2) {
    ...
  }
  ```

---

## `G.TYP.12 明确地进行类型转换，避免依赖隐式类型转换` 🟢 🔴[安全] `common_standard_recommend`

明确的类型转换表明程序员知道混合运算中所涉及的不同类型。通过明确的类型转换引导程序员考虑数据类型转换导致的数据截断、数据精度损失问题，提升系统的可靠性。

除了常见的将取值范围宽的类型转为取值范围较窄的类型导致数据截断问题之外，还要考虑如下两类问题：

1） **意外地**浮点数转换截取会导致误差被逐步放大；
2） 将整数转为浮点数时可能存在精度损失问题，包括`int`、`long`转`float`，`long`转`double`这三种场景。

- **在运算符的右边，要小心地使用更宽的操作数。尽量不要把复合赋值运算符应用于`byte`、`short`、`char`类型的变量。**

**修改建议：** 直接明确进行类型转换

✅ **正确示例：**

##### 场景1：计算时产生隐式转换
  ```java
  short value1 = 459;
  int value2 = 5781;
  long value3 = 4664382371590666666L;

  float value4 = (float) value1 / 13.0f;  // 计算结果为 35.307693
  double value5 = (double) value2 / 30.0d; // 计算结果为 192.7
  BigDecimal bd = new BigDecimal(value3);
  BigDecimal bd2 = bd.multiply(new BigDecimal(2)); // 计算结果为9328764743181333332
  ```

❌ **错误示例：**

##### 场景1：计算时产生隐式转换
  ```java
  short value1 = 459;
  int value2 = 5781;
  long value3 = 4664382371590666666L;

  float value4 = value1 / 13;  // 计算结果为35.0(截断)
  double value5 = value2 / 30; // 计算结果为192.0(截断)
  double value6 = value3 * 2;  // 计算结果为-9.1179793305282181E18
  double value7 = (double) value3 *2; // 计算结果为9.328764743181332E18(截断)
  ```

---

## `G.TYP.09 字符与字节的互相转换操作，要指明正确的编码方式` 🟡 `common_standard_rule`

Java虚拟机采用编码方式默认与操作系统的字符编码方式相同，String的编码方式、`String.getBytes()`默认采用Java虚拟机编码。当跨平台实现字符与字节之间的转换，可能会导致乱码，所以字符与字节之间转换时要明确指定编码方式，推荐优先采用UTF-8编码。

本地化的自然语言文本（非ASCII）的比较、排序、查找，用java.text.Collator。

**修改建议：** 字符与字节的互相转换操作，要指明编码方式，推荐优先采用UTF_8编码。

✅ **正确示例：**

##### 场景1：字符与字节的互相转换操作未指明编码方式
  ```java
  String data = "123ABC中国";
  byte[] buf = data.getBytes(StandardCharsets.UTF_8);
  // 跨平台传输buf
  ...
  String result = new String(buf, StandardCharsets.UTF_8);
  ```
##### 场景2：读取文件时，指定对应的编码方式
  ```java
  String line;
  try (FileInputStream fis = new FileInputStream(fileName);
      InputStreamReader isr = new InputStreamReader(fis, StandardCharsets.UTF_8);
      BufferedReader br = new BufferedReader(isr)) {
      line = br.readLine();
      ...
  }
  ```

❌ **错误示例：**

##### 场景1：字符与字节的互相转换操作未指明编码方式
  ```java
  String data = "123ABC中国";
  byte[] buf = data.getBytes();
  // 跨平台传输buf
  ...
  String result = new String(buf);
  ```
##### 场景2：读取文件时，指定对应的编码方式
  ```java
  String line;
  try (FileReader fr = new FileReader(fileName);
      BufferedReader br = new BufferedReader(fr)) {
      line = br.readLine();
      ...
  }
  ```

---

## `G.TYP.05 浮点型数据判断相等不要直接使用==，浮点型包装类型不要用equals()或者 flt.compareTo(another) == 0作相等的比较` 🟠 🔴[安全] `common_standard_rule`

由于浮点数在计算机表示中存在精度的问题，数学上相等的数字，经过运算后，其浮点数表示可能不再相等，因而不能使用相等运算符==、equals()或者flt.compareTo(another) == 0等方法比较浮点数是否相等。另外，也不应该把浮点数作为HashMap的Key使用。

**修改建议：** 使用误差判等。

✅ **正确示例：**

考虑浮点数的精度问题，可在一定的误差范围内判定两个浮点数值相等。这个误差应根据实际需要进行定义。另外，对于符号不同的两个浮点数，即使在误差范围内也不应该判为相等。如下示例中，两个浮点数值误差在1e-6f内判为相等。
```java
private static final float EPSILON = 1e-6f;

float foo = ...;
float bar = ...;
if (Math.abs(foo - bar) < EPSILON) {
    ...
}
```
Float或Double包装类型可由BigDecimal代替做运算操作。

❌ **错误示例：**

```java
float f1 = 1.0f - 0.9f;
float f2 = 0.9f - 0.8f;
if (f1 == f2) {
    // 预期进入此代码块，执行其他业务逻辑
    // 但事实上 fl == f2 的结果为 false
}
Float flt1 = Float.valueOf(f1);
Float flt2 = Float.valueOf(f2);
if (flt1.equals(flt2)) {
    // 预期进入此代码块，执行其他业务逻辑
    // 但事实上 equals 的结果为 false
}
```

---

## `G.TYP.06 禁止尝试与NaN进行比较运算，相等操作使用Double或Float的isNaN()方法` 🟠 🔴[安全] `common_standard_rule`

当任意一个操作数是NaN（Not a Number）时，数值比较运算符<、<=、>、>=会返回false，运算符==会返回false，运算符!=会返回true。因为无序的特性常常会导致意外结果，所以不能直接与NaN进行比较。

**修改建议：** 用Double或Float的`isNaN()`方法判断浮点数是否为`NaN`。

✅ **正确示例：**

##### 场景1：数值直接与NaN比较
  ```java
  public class NanComparison {
      public void doSomething(double num) {
          // 如果num的值为0.0d，则Math.cos(infinity)返回NaN
          double result = Math.cos(1 / num);
          if (Double.isNaN(result)) {
              System.out.println("result is NaN");
          }
          ...
      }
  }
  ```

❌ **错误示例：**

##### 场景1：数值直接与NaN比较
  ```java
  public class NanComparison {
      public void doSomething(double num) {
          // 如果num的值为0.0d，则Math.cos(infinity)返回NaN
          double result = Math.cos(1 / num);
          if (result == Double.NaN) { // 相等比较总是false
              System.out.println("result is NaN");
          }
          ...
      }
  }
  ```

---

## `G.TYP.04 需要精确计算时使用BigDecimal，不要使用float和double` 🟡 🔴[安全] `common_standard_rule`

浮点数在一个范围很广的值域上提供了很好的近似，但是它不能产生精确的结果。二进制浮点数不能用有限的位数表示0.1，或者10的其他任何负次幂。

正的float大致能表示1.4e-45到3.4e38范围内的数，精度约6位有效数字。

正的double大致能表示4.9e-324到1.7e308范围内的数，精度约15位有效数字。

涉及精确的数值计算（货币、金融等），建议使用int、long、BigDecimal等；在构造BigDecimal时，使用浮点数容易导致精度损失，应该使用字符串格式的数值构造BigDecimal，即应该用BigDecimal (String val)，而不是BigDecimal (double val) 。

**修改建议：** 应当人工判断是否为精确计算浮点数场景，不应强制清理。

✅ **正确示例：**

```java
BigDecimal income = new BigDecimal("1.03");
BigDecimal expense = new BigDecimal("0.42");
System.out.println(income.subtract(expense));
```
上述示例中，输出结果是0.61。

❌ **错误示例：**

```java
System.out.println(1.03d - 0.42d);
```
上述示例中，输出结果是0.6100000000000001，而非预期的0.61。

---

## `G.TYP.03 禁止使用浮点数作为循环计数器` 🟠 🔴[安全] `common_standard_rule`

由于浮点数存在精度问题，用作循环计数器可能会导致非预期的结果（如循环次数与预期不符、导致死循环等）。

**修改建议：** 使用整数作为循环计数器。

✅ **正确示例：**

##### 场景1：浮点数作为循环计数器
  ```java
  for (int index = 2000000000; index < 2000000050; index++) {
      ...
  }
  ```

❌ **错误示例：**

##### 场景1：浮点数作为循环计数器
- 错误示例：由于浮点数的精度问题导致条件判断结果与预期不符：因为`(float) 2000000000 == 2000000050`结果为true，所以循环体不会执行。

  ```java
  for (float flt = (float) 2000000000; flt < 2000000050; flt++) {
      ...
  }
  ```

---

## `G.TYP.07 避免在代码中硬编码用于表示换行、文件路径分隔的字符--不要在代码中硬编码用于表示换行字符` 🟡 `common_standard_recommend`

换行符（回车“\\r”、换行“\\n”）在不同操作系统下是有差别的，代码中硬编码这两类字符，可能影响代码的可移植性。

**换行符的硬编码问题主要影响写文件（导致文件中的换行符与操作系统中的实际换行符不匹配）**，这类操作需要换行时，尽量用`PrintStream`、`PrintWriter`的`println()`来代替在字符串中使用硬编码换行符，也可以使用`System.lineSeparator()`获取运行时环境的换行符。对于读文件，针对不同操作系统下的文件应使用与之相匹配的换行符。另外，当文件的最终使用场景为某种固定操作系统时（例如文件仅用于linux环境下，不会在windows环境下使用），写文件时应该使用目标操作系统相对应的换行符。

**修改建议：** 用`PrintStream`、`PrintWriter`的`println()`来代替在字符串中使用硬编码换行符，也可以使用`System.lineSeparator()`获取运行时环境的换行符。

✅ **正确示例：**

##### 场景1：在代码中硬编码使用文件路径分隔的字符
  ```java
  System.out.println("Hello,world!");
  ```

❌ **错误示例：**

##### 场景1：在代码中硬编码使用文件路径分隔的字符
  ```java
  System.out.print("Hello,world!\n");
  ```

---

## `G.TYP.08 字符串大小写转换、数字格式化为西方数字时，必须加上Locale.ROOT或Locale.ENGLISH` 🟡 🔴[安全] `common_standard_rule`

字符串大小写转换时要考虑地区语言上的差异。`String`类的`toUpperCase()`、`toLowerCase()`方法、`format()`方法，如果不指定输入参数，则会按当前系统默认的编码模式转换，可能会导致非预期的转换结果。

字符对区域不敏感的，例如协议关键字、HTML的tags等优先用ROOT，字符对区域敏感或者强调英文习惯的应使用ENGLISH。

如果确实需要在本地化GUI显示本地语言数字文字，也允许使用：

- Locale.getDefault(Locale.Category.DISPLAY)
- mystr.getBytes(StandardCharsets.UTF_8)

**修改建议：** 数字格式化为西方数字或字符串大小写转换时，加上`Locale.ROOT`或`Locale.ENGLISH`；

✅ **正确示例：**

##### 场景1：数字格式化为西方数字场景
  ```java
  String testString = String.format(Locale.ROOT, "%d", 2);
  System.out.println(testString);
  ```
##### 场景2：字符串大小写转换场景
  ```java
  String testString = "i";
  System.out.println(testString.toUpperCase(Locale.ROOT));
  ```

❌ **错误示例：**

##### 场景1：数字格式化为西方数字场景
  ```java
  String testString = String.format("%d", 2);
  System.out.println(testString); // locale设置为ar-SA，2格式化后输出'  '
  ```
##### 场景2：字符串大小写转换场景
  ```java
  String testString = "i";
  System.out.println(testString.toUpperCase());
  ```

---

## `G.TYP.07 避免在代码中硬编码用于表示换行、文件路径分隔的字符--不要在代码中硬编码用于表示文件路径分隔的字符` 🟡 `common_standard_recommend`

文件路径分隔符（“\\”、“/”）在不同操作系统下是有差别的，代码中硬编码这两类字符，可能影响代码的可移植性。

**文件路径分隔符仅限于操作系统中的文件/文件夹的访问路径中**，不适用于url等路径中 （这类路径在不同操作系统下都是使用“/”作为分隔符）。文件路径分割符可以使用`java.io.File`中的`separator`或`pathSeparator`静态属性；另外，考虑到Windows环境下可以兼容“/”用作文件路径分隔符，代码中也可以统一使用“/”作为文件路径分隔符。

**修改建议：** 使用`java.io.File`中的`separator`或`pathSeparator`静态属性或统一使用`/`作为文件路径分隔符。

✅ **正确示例：**

##### 场景1：在代码中硬编码使用文件路径分隔的字符
- 修复示例1：使用`File.separator` 作为文件路径分隔符

```java
String filePath = path + File.separator + "temp.txt";
```
- 修复示例2：统一使用`/`作为文件路径分隔符

```java
String filePath = path + "/tmp/temp.txt";
```

❌ **错误示例：**

##### 场景1：在代码中硬编码使用文件路径分隔的字符
```java
String filePath = path + "\\\\temp.txt";
```

---

## `G.TYP.13 在引用类型向下转换前用instanceof进行判断` 🟡 `common_standard_recommend`

没有判断类型直接进行类型转换，可能会因类型不匹配而导致运行时异常`java.lang.ClassCastException`。简单的修改方法是在强制转换之前使用`instanceof`进行判断，确认转换操作的可行性，除此之外其他的类型检查方式也是可行的（如直接判断Class类型），只要能保证类型可正确转换即可。

当集合或数组中保存多种类型的对象，当遍历这些数据使用时可以使用instanceof对每个元素的类型进行判断。但是运行时类型检查是一项耗时的操作，另外还可能带来修改点过多、工作量巨大的问题，同时维护的工作量也会倍增。最佳实现方式是改善设计，使集合/数组中只有同一种类型的对象。

**修改建议：** 引用类型向下转换前用`instanceof`进行判断；除此之外其他的类型检查方式也是可行的（如直接判断Class类型），只要能保证类型可正确转换即可。

✅ **正确示例：**

##### 场景1：类型强转前，未判断类型是否兼容，当类型不匹配时会抛出`java.lang.ClassCastException`
  ```java
  public void doSomething(Object obj) {
      ...
      if(obj instanceof SomeResource) {
          SomeResource resouce = (SomeResource)obj;
          ...
      }
  }
  ```

❌ **错误示例：**

##### 场景1：类型强转前，未判断类型是否兼容，当类型不匹配时会抛出`java.lang.ClassCastException`
  ```java
  public void doSomething(Object obj) {
      ...
      SomeResource resouce = (SomeResource)obj;
      ...
  }
  ```

---
