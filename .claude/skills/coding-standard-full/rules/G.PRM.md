# G.PRM Performance 性能

共 8 条规则。

## `G.PRM.01 将集合转为数组时使用Collection<T>.toArray(T[])方法；Java 11后使用Collection<T>.toArray(IntFunction<T[]>)` 🟡 `common_standard_rule`

对于将集合转为数组操作，Java 11引入了`Collection<T>.toArray(IntFunction<T[]> generator)`，它更好的原因是不需要创建临时数组，一方面节省空间，另一方面这样就不用去考虑`toArray(T[])`里的参数长度对方法行为以及结果的影响。

另外，java.util.stream中各Stream的`toArray()`、`toArray(IntFunction<A[]>)`也是常用的。

Java 11前`toArray(T[] a)`的参数应采用**零长度的数组**，这样可保证有更好的性能。数组容量大小产生的影响如下：

- 等于0，动态创建与size相同的数组，性能最好；
- 大于0但小于size，重新创建大小等于size的数组，增加GC负担；
- 等于size，在高并发情况下，数组创建完成之后，size正在变大的情况下，负面影响与上相同；
- 大于size，空间浪费，且在size处插入null值，存在NullPointerException隐患。

**修改建议：** 将集合转为数组时推荐使用`Collection<T>.toArray(T[])`方法，参数应采用**零长度的数组**；Java 11后使用`Collection<T>.toArray(IntFunction<T[]>)`

✅ **正确示例：**

##### 场景1：集合转数组操作
  ```java
  List<String> list = new ArrayList<>(DEFAULT_CAPACITY);
  list.add(getElm());
  ...
  String[] array = list.toArray(new String[0]);
  ```
- 修复示例2：Java11+

  ```java
  List<String> list = new ArrayList<>(DEFAULT_CAPACITY);
  list.add(getElm());
  ...
  String[] array = list.toArray(String[]::new);
  ```

❌ **错误示例：**

##### 场景1：集合转数组操作
  ```java
  List<String> list = new ArrayList<>(DEFAULT_CAPACITY);
  list.add(getElm());
  ...
  String[] array = list.toArray(new String[DEFAULT_CAPACITY + 1]);
  ```

---

## `G.PRM.02 使用System.arraycopy()或Arrays.copyOf()进行数组复制` 🟡 `common_standard_recommend`

在将一个数组对象复制成另外一个数组对象时，不要自己使用循环复制，可以使用Java提供的`System.arraycopy()`功能来复制数据对象，这样做可以避免出错，而且效率会更高。`java.util.Arrays.copyOf()`是对`System.arraycopy()`便利化封装。数组复制有如下特性：

- 对于一维数组，且数组元素为基本类型或String类型时，数组复制属于深复制，即复制后的数组与原始数组的元素互不影响；
- 对于多维数组，或一维数组中的元素是引用类型时，数组复制属于浅复制，即复制后的数组与原始数组的元素引用指向的是同一个对象。

**修改建议：** 使用`System.arraycopy()`或`Arrays.copyOf()`进行数组复制。

✅ **正确示例：**

##### 场景1： 使用for循环进行数组复制
- 修复示例1：使用`System.arraycopy()`进行数组复制

  ```java
  int[] src = {1, 2, 3, 4, 5};
  int[] dest = new int[5];
  System.arraycopy(src, 0, dest, 0, 5);
  ```

❌ **错误示例：**

##### 场景1： 使用for循环进行数组复制
  ```java
  int[] src = {1, 2, 3, 4, 5};
  int[] dest = new int[5];
  for (int i = 0; i < 5; i++) {
      dest[i] = src[i];
  }
  ```

---

## `G.PRM.04 不要对正则表达式进行频繁重复预编译` 🟡 `common_standard_rule`

在频繁调用的场景（例如在方法体内或循环语句中）中，定义Pattern会导致重复预编译正则表达式，降低程序执行效率。另外，对于JDK中的某些API会接受字符串格式的正则表达式作为参数，如`String.replaceAll`、`String.split`等，对于这些API的使用也要考虑性能问题。

**修改建议：** 扩大Pattern的作用域，如用类属性的方式，避免正则表达式从重复编译。

✅ **正确示例：**

##### 场景1： 方法内定义Pattern，方法频繁被调用时导致正则表达式重复预编译
- 修复示例：将Pattern定义为类属性

  ```java
  public class RegexExp {
      private static final Pattern CHARSET_REG = Pattern.compile("[a-z]+");

      // 该方法被频繁调用
      private boolean isLowerCase(String str) {
          if (CHARSET_REG.matcher(str).find()) {
              return true;
          }
          return false;
      }
  }
  ```
##### 场景2： for循环内定义Pattern，导致正则表达式重复预编译
- 修复示例：将Pattern定义为类属性

  ```java
  public class RegexExp {
      private static final Pattern CHARSET_REG = Pattern.compile("[a-z]+");

      private void doSomething(String[] args) {
          int count = 0;
          for (String str : args) {
              if (CHARSET_REG.matcher(str).find()) {
                  count++;
              }
          }
          ...
      }
  }
  ```

❌ **错误示例：**

##### 场景1： 方法内定义Pattern，方法频繁被调用时导致正则表达式重复预编译
  ```java
  public class RegexExp {
      // 该方法被频繁调用
      private boolean isLowerCase(String str) {
          Pattern pattern = Pattern.compile("[a-z]+");
          if (pattern.matcher(str).find()) {
              return true;
          }
          return false;
      }
  }
  ```
##### 场景2： for循环内定义Pattern，导致正则表达式重复预编译
  ```java
  public class RegexExp {
      private void doSomething(String[] args) {
          int count = 0;
          for (String str : args) {
              Pattern pattern = Pattern.compile("[a-z]+");
              if (pattern.matcher(str).find()) {
                  count++;
              }
          }
          ...
      }
  }
  ```

---

## `G.PRM.09 禁止使用Finalizer机制` 🟡 🔴[安全] `common_standard_rule`

禁止主动调用对象的`finalize()`方法;
禁止覆写`finalize()`方法；
禁止调用`System.runFinalization()`与`Runtime.runFinalization()`。

**修改建议：** 删除对象的`finalize()`方法调用。

删除覆写`finalize()`方法;

删除调用`System.runFinalization()`与`Runtime.runFinalization()`。

✅ **正确示例：**

```java
void doSomething() {
    NetworkDemo demo = new NetworkDemo();
    ...     
}
```

❌ **错误示例：**

- 错误示例1：主动调用对象的`finalize()`方法

```java
  void doSomething() {
      NetworkDemo demo = new NetworkDemo();
      ...
      demo.finalize();        
  }
```

- 错误示例2：调用`System.runFinalization()`与`Runtime.runFinalization()`

```java
void doSomething() {
    NetworkDemo demo = new NetworkDemo();
    ...
    System.runFinalization();
}
```

---

## `G.PRM.08 禁止使用主动GC（除非在密码、RMI等方面），尤其是在频繁/周期性的逻辑中` 🟠 🔴[安全] `common_standard_rule`

虽然主动调用GC方法时JVM规范不承诺立即进行垃圾回收操作，但是Oracle Java SE JVM在绝大多数情况下响应此方法调用，会触发JVM的全量GC操作，这会增加GC的次数，也就增加了程序因为GC而停顿的时间；而且在GC过程中的某些阶段程序会完全停顿，这会让程序失去响应，对系统造成非常大的风险。在频率/周期性的逻辑（for循环、定时器）中更要尽量避免主动GC的调用。

**修改建议：** 避免主动执行GC操作，尤其是不能在循环中频繁调用GC操作。

✅ **正确示例：**

- 修复示例：不主动调用GC操作

  ```java
  for (String bookName : bookNames) {
      Book book = new Book(bookName);
      checkBook(book);
      ... // 其他操作
  }
  ```

❌ **错误示例：**

- 错误示例：在循环中调用了`System.gc()`

  ```java
  for (String bookName : bookNames) {
      Book book = new Book(bookName);
      checkBook(book);
      ... // 其他操作
      System.gc();
  }
  ```

---

## `G.PRM.07 进行IO类操作时，必须在try-with-resource或finally里关闭资源` 🟠 🔴[安全] `common_standard_rule`

申请的资源不再使用时，需要及时释放，否则会导致资源泄露问题。释放后的资源不要继续使用，否则可能导致系统抛出异常或其他未知不安全行为。

**修改建议：** 系统异常可能导致资源释放操作被跳过，因此对于IO、数据库操作等需要显式调用关闭方法（如`close()`）来释放资源的场景，必须在try-catch-finally的finally中调用关闭方法。如果有多个资源需要`close()`，需要分别对每个资源`close()`时的异常进行try-catch处理，防止某个资源关闭失败导致其他资源无法正常关闭，最终保证所有资源都能被正确释放。

Java 7有自动资源管理的特性try-with-resource，不需手动关闭。该方式应该优先于try-finally，这样得到的代码将更加简洁、清晰，产生的异常也更有价值。特别是对于多个资源关闭发生异常时，try-finally可能丢失掉前面的异常，而try-with-resource会保留第一个异常，并把后续的异常作为Suppressed exceptions，可通过`getSuppressed()`获取这些异常信息。

✅ **正确示例：**

- 修复示例1：在finally中释放资源

  ```java
  public void doSomething() {
      FileInputStream in = null;
      try {
          in = new FileInputStream(inputFileName);
      } catch (IOException e) {
          ...
      } finally {
          ...
          in.close();
      }
  }
  ```
- 修复示例2：使用try-with-resource机制，保证资源正确释放

  ```java
  try (FileOutputStream fop = new FileOutputStream(file)) {
      fop.write(buf);
  }
  ```

❌ **错误示例：**

- 错误示例：方法抛出异常时，资源可能无法正确释放

  ```java
  public void doSomething() {
      FileInputStream in = null;
      try {
          in = new FileInputStream(inputFileName);
          in.close();
      } catch (IOException e) {
          int aaaa = 1;
      }
  }
  ```

---

## `G.PRM.10 不要创建临时变量作为return语句的返回值` 🟡 `common_standard_recommend`

不要创建临时变量作为return语句的返回值，保持代码简洁。

**修改建议：** 去除临时变量和赋值，直接内联到return语句中

✅ **正确示例：**

  ```java
  private List<String> func() {
      return solve();
  }
  ```

❌ **错误示例：**

  ```java
  private List<String> func() {
      List<String> res = solve();
      return res;
  }
  ```

---

## `G.PRM.05 禁止创建不必要的对象` 🟡 `common_standard_rule`

重用一个已经创建的对象比创建一个新的对象要好得多，除非确实需要重新创建。创建重复不必要的对象会导致资源浪费，严重时可能会导致性能问题。

**修改建议：** 对于基本数据类型，建议避免使用封装类型导致的重复创建冗余对象；对于字符串常量避免使用`new String("xxx")`操作。

✅ **正确示例：**

- 修复示例：直接用基本类型创建数据或直接使用字符串常量，避免创建冗余对象

  ```java
  String foo = "string";
  Integer bar = Integer.valueOf(90);
  ...
  Integer baz = Integer.valueOf(90); // 默认在-128~127间，会重用内存中缓存的对象
  ```

❌ **错误示例：**

- 错误示例：创建冗余的String、封装的基本类型对象

  ```java
  String foo = new String("string"); // 建立了2个String对象
  Integer bar = new Integer(90);
  ...
  Integer baz = new Integer(90);
  ```

---
