# G.FMT Formatting 格式化

共 20 条规则。

## `G.FMT.03 import包应该按照先安卓、华为公司、其他商业组织、其他开源第三方、net/org开源组织、最后java的分类顺序出现，并用一个空行分组` 🟡 `common_standard_recommend`

import包推荐按如下顺序排列：

- 静态导入置于所有其他导入之上。
- 从上往下，大致分类是：import static、安卓、华为公司com.huawei.*、其他商业组织com.*、其他开源第三方xxx.yyy.*、net/org开源组织、javacard、Java最基础的包、Java的其他包、Java的扩展包。
- 每一类内部按照字母顺序排序。几大分类也大致是按字母排序（android、com、net、org），只是java/javax在最后。

Java最基础的包，是指java.base模块中的包，参照[java.base中的包清单](https://docs.oracle.com/en/java/javase/11/docs/api/java.base/module-summary.html)。Java的其他包，是指java.base模块之外的[其他SE模块的包](https://docs.oracle.com/en/java/javase/11/docs/api/index.html)。

三方开源，包含了商业公司的开源，例如com.alibaba.fastjson，com.intellij.openapi等，与非盈利组织的开源，例如net/org组织的。这里，“其他”，就是指除了前缀为com、net、org之外的其他三方开源，例如下面示例中的lombok、maven。

这个风格兼容于[安卓的import顺序](https://source.android.com/setup/contribute/code-style#order-import-statements)，如果没有最上面的安卓包，也适用于非安卓。

**修改建议：** 对代码进行格式化处理。

✅ **正确示例：**

```java
import static all.statics.imports;  // 静态导入

import android.xx.Xyz; // 安卓
import androidx.xx.Xyz; // 安卓

import com.hisilicon.xx.Xyz; // 海思
import com.huawei.xx.Xyz; // 华为公司

import com.google.common.io.Files; // 其他商业组织

import harmonyos.xx.Xyz; // 开源第三方 鸿蒙
import lombok.extern.slf4j.Sl4j;
import maple.xx.Xyz;
import maven.xx.Xyz;
import ohos.xx.Xyz;

import net.sf.json.xx.Xyz; // net/org开源组织

import org.linux.apache.server.SoapServer;

import javacard.xx.Xyz;

import java.io.IOException; // Java最基础的包
import java.net.URL;
import java.rmi.RmiServer;
import java.rmi.server.Server;

import javax.swing.JPanel; // Java的扩展包
import javax.swing.event.ActionEvent;

```

❌ **错误示例：**

```java
import static all.statics.imports; // 静态导入
import android.xx.Xyz;  // 安卓
import androidx.xx.Xyz; // 安卓
import lombok.extern.slf4j.Sl4j;   // 开源第三方
import maple.xx.Xyz; // 开源第三方
import maven.xx.Xyz; // 开源第三方
import net.sf.json.xx.Xyz; // net/org开源组织
import org.linux.apache.server.SoapServer; // net/org开源组织
import com.hisilicon.xx.Xyz; // 海思
import com.huawei.xx.Xyz;    // 华为公司
import com.google.common.io.Files; // 其他商业组织
import harmonyos.xx.Xyz; // 开源第三方 鸿蒙
import ohos.xx.Xyz; // 开源第三方 鸿蒙
import javacard.xx.Xyz;
import java.io.IOException; // Java最基础的包
import java.net.URL;
import java.rmi.RmiServer;  // Java的其他包
import java.rmi.server.Server;
import javax.swing.JPanel;  // Java的扩展包
import javax.swing.event.ActionEvent;
```

---

## `G.FMT.04 一个类或接口的声明部分应该按照类变量、静态初始化块、实例变量、实例初始化块、构造器、方法的顺序出现，且用空行分隔` 🟢 `common_standard_recommend`

一个类或接口的声明部分应该按照以下顺序排列：

- 类（静态）变量
- 静态初始化块
- 实例变量
- 实例初始化块
- 构造器
- 方法或嵌套类，嵌套类可以与成员方法根据业务逻辑交替出现，把概念上相近的放在一起，无需把所有嵌套类都下移至文件底部
- 类（静态）变量、实例变量、构造器，均按访问修饰符从大到小排列：public、protected、package（default）、private

**说明：**

1. 对于自注释成员变量之间可以不加空行；
2. 非自注释成员变量应该加注释且成员变量间以空行分隔。

**修改建议：** 对代码进行格式化处理。

✅ **正确示例：**

##### 场景1：部分类声明未空行分隔
  ```java

  public class DeclarationOrder {
      private static final Logger LOGGER = LoggerFactory.getLogger(DeclarationOrder.class);

      static class StaticClass {
          ...
      }

      private void privateMethod() {
          ...
      }   
  ```

❌ **错误示例：**

##### 场景1：部分类声明未空行分隔
  ```java
  public class DeclarationOrder {
      private static final Logger LOGGER = LoggerFactory.getLogger(DeclarationOrder.class);

      private void privateMethod() {
          ...
      }   

      static class StaticClass {
          ...
      }

  }
  ```

---

## `G.FMT.02 一个源文件按顺序包含版权、package、import、顶层类，且用空行分隔` 🟢 `common_standard_recommend`

一个源文件中应按顺序包含以下信息：

1. 许可证或版权信息；
2. package语句，且语句内不换行；
3. import语句，且语句内不换行，不能用通配符*；
4. 顶级类（只有一个），所在.java源文件与它同名。

以上每个部分之间用一个空行隔开。

**修改建议：** 对代码进行格式化处理。

✅ **正确示例：**

##### 场景1：版权、package、import、顶层类按推荐顺序排列
- 修复示例：版权、package、import、顶层类之间使用一个空行分隔

```java
/**
 * Copyright (c) Huawei Technologies Co., Ltd. 2012-2019. All rights reserved.
 */

package com.puppycrawl.tools.checkstyle;

import java.util.List;

/**
 * JavaComponentOrder介绍
 *
 * @since 2023-08-29
 */
public class JavaComponentOrder {
}
```

❌ **错误示例：**

##### 场景1：版权、package、import、顶层类按推荐顺序排列
- 错误示例：版权、package的顺序错误，分隔使用多个空行

```java
package com.puppycrawl.tools.checkstyle;

/**
 * Copyright (c) Huawei Technologies Co., Ltd. 2012-2019. All rights reserved.
 */

import java.util.List;

/**
 * JavaComponentOrder介绍
 *
 * @since 2023-08-29
 */
public class JavaComponentOrder {
}

```

---

## `G.FMT.16 case语句块结束时如果不加break，需要有注释说明(fall-through)` 🟢 🔴[安全] `common_standard_recommend`

switch语句中，当没有终止语句（`break`，`return`或抛出异常）时会执行到switch语句的结束处。当case语句块中没有终止语句时，需要添加注释，表明会继续执行到下一个case语句块。任何符合fall-through概念的注释都可以（通常是`// $FALL-THROUGH$`）。

Eclipse和IntelliJ IDEA支持`$FALL-THROUGH$`这种特殊的注释来suppress缺少`break`的告警。尽管这不是Java的标准，但它被主流的IDE支持，推荐优先使用。

**注意**：

- 当javac开启 `-Xlint:fallthrough`选项编译时 ，加与不加`$FALL-THROUGH$`，可能都会告警；修复此告警可以考虑改用`if else if`写法替代`switch case`。
- continue不能单独用于switch中，可用于循环中的switch中，continue的作用是跳出本次循环，所以case语句中使用continue时，还会影响循环代码块中后续代码的执行。

如果`case`语句是空语句，则可以不用加注释特别说明。

**修改建议：** 建议为每个case语句添加break，如果确实不需要添加break时，需要添加`$FALL-THROUGH$`注释。

✅ **正确示例：**

##### 场景1： case语句块结束时无break
- 修复示例1：增加break

  ```java
  switch (label) {
      case 0:
      case 1:
          System.out.println("1");
          break;
      case 2:
          System.out.println("2");
          break;
      case 3:
          System.out.println("3");
          break;
      default:
          System.out.println("Default case!");
  ```
- 修复示例2：增加`$FALL-THROUGH$`注释

  ```java
  switch (label) {
      case 0:
      case 1:
          System.out.println("1");
          // $FALL-THROUGH$
      case 2:
          System.out.println("2");
          break;
      case 3:
          System.out.println("3");
          break;
      default:
          System.out.println("Default case!");
  ```

❌ **错误示例：**

##### 场景1： case语句块结束时无break
  ```java
  switch (label) {
      case 0:
      case 1:
          System.out.println("1");
      case 2:
          System.out.println("2");
      case 3:
          System.out.println("3");
          break;
      default:
          System.out.println("Default case!");
  }
  ```

---

## `G.FMT.17 应用于类、方法、类属性的每个注解独占一行` 🟢 `common_standard_recommend`

应用于类、方法（含构造方法）、类属性的注解应在其上部，且每个注解独占一行。

**修改建议：** 对代码进行格式化处理。

✅ **正确示例：**

##### 场景1：多个注解共处一行
- 修复示例1：每个注解独占一行

  ```java
  @Partial
  @Mock
  DataLoader loader;

  @Override
  @Nullable
  public String doSomething() {
      ...
  }
  ```

❌ **错误示例：**

##### 场景1：多个注解共处一行
  ```java
  @Partial@Mock
  DataLoader loader;

  @Nullable@Override
  public String doSomething() {
      ...
  }
  ```

---

## `G.FMT.18 块注释的缩进级别应与上下文代码相同` 🟢 `common_standard_recommend`

块注释的缩进级别应该与被注释代码相同。可以采用单行注释（`// ...`）风格或多行注释（`/* ... */`）风格，对于多行注释风格，每行注释要以`*`开头且保持前后对齐

**修改建议：** 对代码进行格式化处理。

✅ **正确示例：**

##### 场景1：注释与代码不对齐
- 修复示例1：遵循K&R风格换行

  ```java
  public void doSomething() {
      ...
      if(condition()) {
          /*
           * 第一行注释
           * 第二行注释
           */
          int value = 0;
      }
      ...
  }
  public void doSomething() {
      ...
      if(condition()) {
          // 第一行注释
          // 第二行注释
          int value = 0;
      }
      ...
  }
  ```

❌ **错误示例：**

##### 场景1：注释与代码不对齐
  ```java
  public void doSomething() {
      ...
      if(condition()) {
           /*
            * 第一行注释
            * 第二行注释
            */
          int value = 0;
      }
      ...
  }
  public void doSomething() {
      ...
      if(condition()) {
            // 第一行注释
         // 第二行注释
          int value = 0;
      }
      ...
  }
  ```

---

## `G.FMT.19 类和成员修饰符（如果存在）按Java语言规范建议的顺序显示` 🟢 `common_standard_recommend`

类和成员修饰符（如果存在）按推荐的顺序显示（如果存在）：

```java
public protected private abstract default static final transient volatile synchronized native strictfp
```

**修改建议：** 对代码进行格式化处理。

✅ **正确示例：**

##### 场景1：类和成员修饰符与推荐顺序不一致
  ```java
  public class ModifierOrder {
      private static final int II = 0;

      private synchronized void doSomething(String str) throws IOException {
          int num = 0;
          ...
      }
  }
  ```

❌ **错误示例：**

##### 场景1：类和成员修饰符与推荐顺序不一致
  ```java
  public class ModifierOrder {
      private final static int II = 0;

      synchronized private void doSomething(String str) throws IOException {
          int num = 0;
          ...
      }
  }
  ```

---

## `G.FMT.20 数字字面量应该设置合适的后缀，long类型应该使用L作为后缀` 🟢 `common_standard_recommend`

对于long、float、double类型的数字要使用合理的后缀指定数值的类型。Java 10增加了局部类型推断LVTI，一些字面量如果不加后缀，类型推断时可能与预期不符。为了形成良好的习惯，写出更健壮的代码，应参考LVTI的Style Guidelines。如果不加后缀，数值推断为int，float可能会推断为double。因此，应该在字面量后面加上后缀。

long值必须使用L后缀，不能使用l做后缀。例如，使用500000L而不是500000l。对于较大数值，可以使用Java 7新增的数字下划线分隔符，增强代码的可读性，如30_000_000_000L。

d、f后缀不易引起混淆的，不强制采用大写字母。

**修改建议：** 增加相应后缀。

✅ **正确示例：**

##### 场景1：使用不合适的后缀
  ```java
  long sum = 0L;
  float flt = 1.0f; 
  double dbl = 2.0d; 
  ```

❌ **错误示例：**

##### 场景1：使用不合适的后缀
  ```java
  long sum = 0l; 
  float flt = 1;
  double dbl = 2.0;
  ```

---

## `G.FMT.05 在条件语句和循环块中应该使用大括号` 🟢 🔴[安全] `common_standard_recommend`

在 `if`， `else`， `switch`， `for`，`do`和 `while`等语句中，即使程序体是空的或只包含一个语句，也应该使用大括号。对`switch`里面的`case`和`default`，大括号是可选的。

**修改建议：** 对代码进行格式化处理。

✅ **正确示例：**

##### 场景1：在条件语句和循环块中使用大括号
```java
// 在条件语句和循环块中使用大括号
public void doSomething() {
    ...
    if (condition()) {
        doSomethingElse();
    }
}
```

❌ **错误示例：**

##### 场景1：在条件语句和循环块中使用大括号
```java
// 在条件语句和循环块中应该使用大括号
public void doSomething() {
    ...
    if (condition())
        doSomethingElse();
}
```

---

## `G.FMT.06 对于非空块状结构，左大括号应该放在行尾，右大括号应该另起一行` 🟢 `common_standard_recommend`

对于非空块状结构（含初始化块），大括号应该遵循K&R风格：

- 左大括号不换行；
- 右大括号自己单独一行；
- 右大括号后，可以跟逗号、分号等，也可以跟随 `else`、 `catch`、`finally`等关键字语句。

**修改建议：** 对代码进行格式化处理。

✅ **正确示例：**

##### 场景1
- 修复示例1：遵循K&R风格换行

  ```java
  try {
      if (condition()) {
          doSomething();
      } else {
          doSomethingElse();
      }
  } catch (MyException ex) {
      handleException(ex);
  }
  ```

❌ **错误示例：**

##### 场景1
  ```java
  try {
      doSomething();
  } catch (MyException ex) { handleException(ex); } // 代码块应该换行
  ```

---

## `G.FMT.08 使用空格进行缩进，每次缩进4个空格` 🟢 `common_standard_recommend`

只允许使用空格（space）进行缩进，每次缩进为**4**个空格。不允许插入制表符tab、换页符等。

当前几乎所有的集成开发环境（IDE）和代码编辑器都支持配置将Tab键自动扩展为**4**空格输入，应在代码编辑器中配置使用空格进行缩进。

**修改建议：** 使用空格进行缩进，每次缩进4个空格。

✅ **正确示例：**

##### 场景1：代码缩进采用非4个空格
- 修复示例：以4的倍数的空格方式进行缩进

  ```java
      private int data;
      private int result;
  ```

❌ **错误示例：**

##### 场景1：代码缩进采用非4个空格
- 错误示例：使用3个空格进行缩进

  ```java
     private int data;
     private int result;
  ```

---

## `G.FMT.07 应该避免空块，必须使用空块时，应采用统一的大括号换行风格` 🟢 `common_standard_recommend`

程序中应避免空块，但对于工具自动生成的、用于被覆盖的场景（例如UI监听器），可能需要定义空的方法体；忽略异常时也可能使用空的catch块。

**修改建议：** 建议将代码中的无效空块删除。

✅ **正确示例：**

##### 场景1：代码中存在空块
  ```java
  class EmptyBlockDemo {
      ...
      public void doSomething() {
          int data = 1;
          ...        
      }

  }
  ```

❌ **错误示例：**

##### 场景1：代码中存在空块
  ```java
  class EmptyBlockDemo {
      static {

      }
      ...
      public void doSomething() {
          int data = 1;
          ...
          for (String str : list) {
          }
          ...
      }

  }
  ```

---

## `G.FMT.09 每行不超过一个语句` 🟢 `common_standard_recommend`

一行应只写一条语句。

**修改建议：** 对代码进行格式化处理。

✅ **正确示例：**

##### 场景1：多个语句放在一行
- 修复示例1：每行只放一个语句，合理进行断行

  ```java
  int aa;
  int bb;
  ```

❌ **错误示例：**

##### 场景1：多个语句放在一行
  ```java
  int aa; int bb;
  ```

---

## `G.FMT.10 行宽不超过120个窄字符` 🟢 `common_standard_recommend`

建议代码每行不超过maxLineLength(默认值为120，支持动态配置)个窄字符，保证在屏幕中可以呈现完整代码行，不需要拖动横向滚动条来查看完整代码。

对于宽字符，实际在IDE中的呈现宽度可通过cnCharLength参数进行设置，该参数的默认值值为1.5（结合IntelliJ IDEA的实际呈现效果确定）。

**修改建议：** 代码行宽超过maxLineLength个窄字符时，在合理的位置进行换行处理。

✅ **正确示例：**

- 修复示例：在合适处断行

  ```java
  public void doSomething(HashMap<String, String> hashMap,
      HashSet<String> hashSet, List<String> stringList, String describe) {
         ...
  }
  ```

❌ **错误示例：**

  ```java
  public void doSomething(HashMap<String, String> hashMap, HashSet<String> hashSet, List<String> stringList, String describe) {
      // 代码行宽超过120个窄字符
      ...
  }
  ```

---

## `G.FMT.11 建议换行起点在操作符之前` 🟢 `common_standard_recommend`

当语句过长，或者可读性不佳时，需要在合适的地方换行。换行时建议将操作符、连接符放在新的一行。

**修改建议：** 对代码进行格式化处理。

✅ **正确示例：**

##### 场景1：未合理换行
- 修复示例1：在合适处断行，尽量将操作符、连接符放在新的一行

  ```java
  Student student = Student.builder()
      .setName("zhangsan")
      .setAge(14)
      .setGrade("5年级")
      .setMajor("软件工程")
      .setNum("123456789")
      .build();
  ```

❌ **错误示例：**

##### 场景1：未合理换行
  ```java
  Student student = Student.builder().setName("zhangsan").setAge(14).setGrade("5年级").setMajor("软件工程").
      setNum("123456789").build();
  ```

---

## `G.FMT.12 减少不必要的空行，保持代码紧凑` 🟢 `common_standard_recommend`

减少不必要的空行，可以显示更多的代码，方便代码阅读。建议：

- 根据上下内容的相关程度，合理安排空行：空行出现在属性，构造方法，方法，嵌套类，静态初始化块之间；
- 方法内部、类型定义内部、初始化表达式内部，不使用**连续**空行；
- 不使用**连续3个**或更多空行；
- 大括号内的代码块**行首之前和行尾之后不要加空行**，包括类型和方法定义、语句代码块。

**修改建议：** 对代码进行格式化处理。

✅ **正确示例：**

##### 场景1：空行过多
  ```java
  int foo() {
      ...
  }

  int bar() { 
      ...
  }

  int baz() {
      doSomething();
      ...      
  }
  ```

❌ **错误示例：**

##### 场景1：空行过多
  ```java
  int foo() {
      ...
  }

  int bar() { 
      ...
  }

  int baz() {

      doSomething();
      ...

  }
  ```

---

## `G.FMT.13 用空格突出关键字和重要信息` 🟢 `common_standard_recommend`

水平空格应该突出关键字和重要信息。单个空格应该分隔关键字与其后的左括号、与其前面的右大括号，出现在任何二元/三元运算符/类似运算符的两侧，`,:;`或类型转换结束括号`)`之后使用空格。行尾和空行不应有空格space。总体规则如下：

**必须**加空格的场景：

- （包括复合）赋值运算符前后，例如`=`、`*= `等；
- 逗号`,`、非for-in的冒号`:`、for循环等分隔的`;`符号之后加空格；
- 二元运算符、类型并交的`|`和`&`符号、for-in的冒号`:`的前后两侧，例如`base + offset`；
- lambda表达式中的箭头前后，例如`str -> str.length()`；
- 方法声明、条件判断语句、循环语句等场景下的`)`与`{`之间加空格，例如：`void func() {...}`。

**禁止**加空格的场景：

- `super`、`this`等少数关键字之后（多数关键字之后自然地须加空格）；
- 成员访问操作符前后，例如`instance.member`；
- 圆括号、方括号、注解或数组等非换行的大括号内两侧；
- 一元运算符前后，例如`cnt++`；
- 方法声明或者方法调用的左括号之前。

**修改建议：** 对代码进行格式化处理。

✅ **正确示例：**

##### 场景1：格式不正确，未加空格
- 修复示例1：在适当处加空格

  ```java
  String str = "";

  private void method(String str, boolean bool, List<String> list)
  ```

❌ **错误示例：**

##### 场景1：格式不正确，未加空格
  ```java
  String str="";

  private void method(String str,boolean bool,List<String> list)
  ```

---

## `G.FMT.14 不应插入多余空格使代码垂直对齐` 🟢 `common_standard_recommend`

不应通过插入空格的方式使代码垂直对齐，包括在Javadoc的注释性描述内容前。原因是：
* 如果参数/变量名长短差异较大，无规律插入的空白呈凹凸状，并不美观；
* 如果某个参数/变量名较长，例如gardenPlantingDetailViewModel，对应的描述内容也较长的话，就可能不得不换行，又可能会有换行对齐的顾忌；
* 后续的维护者可能会困扰是否在整个module/package都刻意追求对齐。

因此，代码垂直对齐的弊大于利；为了减少维护成本，不造成困扰，不对齐是最好的选择。

**修改建议：** 删除不必要的空格。

✅ **正确示例：**

```java
private int size;
private String name;
```

❌ **错误示例：**

```java
private int size; // 维护者可能不得不修改这些对齐空格数
private String name; // 不必与上行对齐注释
```

---

## `G.FMT.15 枚举常量间用逗号隔开， 换行可选` 🟢 `common_standard_recommend`

下面是一个典型的枚举类声明示例：
```java
private enum Size {SMALL, MEDIUM, LARGE}
```
由于不涉及方法及常量的注释，采用的是数组初始化的格式。枚举常量之间使用逗号进行分隔。

在枚举常量后面的逗号之后，换行符是可选的。还允许额外的空白行（通常只有一行）。例如：
```java
private enum Encoding {
    UTF8 {
        @Override
        public String toString() {
          return "UTF-8";
        }
    },

    UTF16,
    US_ASCII
}
```
Java的枚举比较灵活强大，而且与switch/case结合较好，应优先使用。

枚举的使用场景：
* 布尔型的两元素值，例如isCelsius = true | false来表示摄氏|华氏可用；
```java
public enum TemperatureScale {CELSIUS, FAHRENHEIT}
```
* 变量值仅在一个固定范围内变化用enum类型来定义。例如G.DCL.04的Keyboard例子；
* 整数或字符串的枚举模式，蕴含有某种命名空间的，例如上面的Size例子，或者其他语言的ComparisonResult，避免-1、0、1的数字比较。
```java
public enum ComparisonResult {
    ORDERED_ASCENDING,
    ORDERED_SAME,
    ORDERED_DESCENDING
}
```

**修改建议：** 删除多余空行。

❌ **错误示例：**

```java
public enum EnumBlankTest {
    ENUM_A, ENUM_B,

    ENUM_C,

    ENUM_D;
}
```

---

## `G.FMT.01 源文件编码格式（包括注释）应该是UTF-8` 🟢 `common_standard_recommend`

对于源文件，应统一采用UTF-8进行编码。另外，对于资源文件（如xml、yml、properties等配置文件）等也应该采用UTF-8进行编码。

**修改建议：** 将源文件、资源文件统一使用UTF-8编码格式进行保存。

✅ **正确示例：**

```java
public class FileEncoding {
    public void method() {
        // 注释信息
    }
}
```

❌ **错误示例：**

```java
public class FileEncoding {
    public void method() {
        //     
    }
}
```

---
