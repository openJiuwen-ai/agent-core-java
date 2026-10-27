# G.CMT Comments 注释

共 8 条规则。

## `G.CMT.03 方法的Javadoc中应该包含功能说明，根据实际需要按顺序使用@param、@return、@throws标签对参数、返回值、异常进行注释--功能描述和标签之间要有一个空行` 🟢 `common_standard_recommend`

书写方法的Javadoc时，推荐用Java 8新增的@implSpec，@apiNote和@implNote对注释内容进行分类描述（不强制要求对存量代码进行修改）。各标签的排列顺序如下：

* 功能描述，说明API的原理、意图、契约（前置与后置条件）等。功能描述与后面的各种标签之间需要空1行。
* @implSpec：特定于API实现的规格说明，让实现者决定是否覆盖。
* @apiNote：说明API的注意事项，包括是否允许null、是否线程安全、算法复杂度、输入输出范围、非受检异常等。
* @implNote：特定于API实现的备注，让实现者参考。
* @param：注释方法的参数。
* @return：注释方法的返回值。
* @throws：注释方法抛出的所有类型的异常，包括受检异常和运行时异常。将运行时异常文档化，可有效描述方法被成功执行的前提条件。
* @Deprecated：如果方法被废弃，添加该标签。

上述标签中，除了@Deprecated，不允许空的描述出现。某标签中的内容需多行显示时，新行内容应从@位置缩进4个空格来对齐。

@implSpec|@apiNote|@implNote与@param|@return|@throws这两组标签之间需要空1行。

**修改建议：** 增加功能说明和相应标签。

✅ **正确示例：**

```java
/**
 * doSomething的功能说明
 *
 * @param str xxx
 * @return String xxx
 * @throws IOException xxx
 */
public String doSomething(String str) throws IOException {
    // doSomeThing function body
}

 /**
 * doSomething的功能说明
 *
 * @implSpec xxx
 * @apiNote xxx
 * @implNote xxx
 *
 * @param str xxx
 * @return String xxx
 * @throws IOException xxx
 */
public String doSomething(String str) throws IOException {
    // doSomeThing function body
}
```

❌ **错误示例：**

```java
/**
 * doSomething的功能说明
 * @implSpec xxx
 * @apiNote xxx
 * @implNote xxx
 * @param str xxx
 * @return String xxx
 * @throws IOException xxx
 */
public String doSomething(String str) throws IOException {
    // doSomeThing function body
}
```

---

## `G.CMT.05 文件头注释应该包含版权许可信息` 🟢 `common_standard_recommend`

文件头注释应该放在package和import之前，应该包含版权许可信息，如果需要在文件头注释中增加其他内容，可以在后面以相同格式补充。版权许可不应该使用Javadoc样式或单行样式的注释，应该从文件顶头开始。如果包含“关键资产说明”类注释，则应紧随其后。

版权许可内容及格式必须如下：

中文版：

```java
/* * 版权所有 (c) 华为技术有限公司 2012-2020 */
```

英文版：

```java
/* * Copyright (c) Huawei Technologies Co., Ltd. 2012-2020. All rights reserved. */
```

关于版本说明，应注意：

- 2012-2020 根据实际需要可以修改。
  2012 是文件首次创建年份，而 2020 是最后文件修改年份。二者可以一样，如 "2020-2020"。
  对文件有重大修改时，必须更新后面年份，如特性扩展，重大重构等。
- 版权说明可以使用华为子公司。
  如：版权所有 (c) 海思半导体 2012-2020
  或英文：Copyright (c) Hisilicon Technologies Co., Ltd. 2012-2020. All rights reserved.

**修改建议：** 为.java文件添加文件头注释，文件头注释中添加版权许可信息，版权许可信息中的时间信息要与实际情况保持一致。

✅ **正确示例：**

##### 场景1：无文件头注释
  ```java
  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2022-2022. All rights reserved.
   */

  package com.xxx;

  public class FileHeaderComment {
      public void method() {
      }
  }
  ```

❌ **错误示例：**

##### 场景1：无文件头注释
  ```java
  // 无文件头注释
  package com.xxx;

  public class FileHeaderComment {
      public void method() {
      }
  }
  ```

---

## `G.CMT.03 方法的Javadoc中应该包含功能说明，根据实际需要按顺序使用@param、@return、@throws标签对参数、返回值、异常进行注释` 🟢 `common_standard_recommend`

书写方法的Javadoc时，推荐用Java 8新增的@implSpec，@apiNote和@implNote对注释内容进行分类描述（不强制要求对存量代码进行修改）。各标签的排列顺序如下：

* 功能描述，说明API的原理、意图、契约（前置与后置条件）等。功能描述与后面的各种标签之间需要空1行。
* @implSpec：特定于API实现的规格说明，让实现者决定是否覆盖。
* @apiNote：说明API的注意事项，包括是否允许null、是否线程安全、算法复杂度、输入输出范围、非受检异常等。
* @implNote：特定于API实现的备注，让实现者参考。
* @param：注释方法的参数。
* @return：注释方法的返回值。
* @throws：注释方法抛出的所有类型的异常，包括受检异常和运行时异常。将运行时异常文档化，可有效描述方法被成功执行的前提条件。
* @Deprecated：如果方法被废弃，添加该标签。

上述标签中，除了@Deprecated，不允许空的描述出现。某标签中的内容需多行显示时，新行内容应从@位置缩进4个空格来对齐。

@implSpec|@apiNote|@implNote与@param|@return|@throws这两组标签之间需要空1行。

**修改建议：** 增加功能说明和相应标签。

✅ **正确示例：**

```java
/**
 * doSomething的功能说明
 *
 * @param str xxx
 * @return String xxx
 * @throws IOException xxx
 */
public String doSomething(String str) throws IOException {
    // doSomeThing function body
}

 /**
 * doSomething的功能说明
 *
 * @implSpec xxx
 * @apiNote xxx
 * @implNote xxx
 *
 * @param str xxx
 * @return String xxx
 * @throws IOException xxx
 */
public String doSomething(String str) throws IOException {
    // doSomeThing function body
}
```

❌ **错误示例：**

```java
protected abstract class Sample {
/**
 * doSomething的功能说明
 * @return String xxx
 */
public String doSomething(String str) throws IOException {
    // doSomeThing function body
}
```

---

## `G.CMT.01 public或protected修饰的元素应添加Javadoc注释` 🟢 `common_standard_recommend`

最低限度要为每个public或protected修饰的类、接口、枚举、类方法和类属性添加注释，这些注释的格式应该采用Javadoc注释格式（即使用`/** */`进行注释），除此之外按需添加Javadoc注释。实现接口方法时，其Javadoc允许使用{@inheritDoc}。

**修改建议：** 为public或protected修饰的元素应添加Javadoc注释。

✅ **正确示例：**

```java
// public或protected修饰的元素应添加Javadoc注释
/**
 * doSomething方法的功能说明
 *
 * @param data xxx（data的具体含义）
 * @return List  返回结果集合，集合不会为null
 * @throws IOException 当出现xx时，会抛出IOException 
 */
public static List<String> doSomething(int data) throws IOException {
    ...
}
```

❌ **错误示例：**

```java
// 未添加注释
public static List<String> doSomething(int data) throws IOException {
    ...
}
 ```

---

## `G.CMT.02 顶层public类的Javadoc应该包含功能说明和创建日期/版本信息` 🟢 `common_standard_recommend`

顶层public类的Javadoc中应该有功能说明、`@since`信息。日期格式为Java 8 time包中的`ISO_DATE`，例如“2011-12-03”或者“2011-12-03+01:00”。

编写文件头或顶层类头注释应注意：

- 禁止空有格式，无内容。
- 业界Java源码中一般没有History信息，History在配置库里面可以查询，不建议在Java源码的注释中包含History。
- 顶层public类头中创建日期的`@since`标签中的年份应该与版权中的起始年份相同。

**修改建议：** 在Javadoc中添加功能说明、添加@since注解，并在后面添加时间，例如：@since 2023-08-29

✅ **正确示例：**

```java
// 添加了顶层class的注释
/**
 * TopClassComment的功能说明
 *
 * @since 2020/5/26
 */
 public enum TopClassComment {
     ...
 }
```

❌ **错误示例：**

- 错误示例：注释中无功能说明、`@since`信息

  ```java
  // 注释中无功能说明、`@since`信息
  public enum TopClassComment {
      ...
  }
  ```

---

## `G.CMT.04 不写空有格式的方法头注释` 🟢 `common_standard_recommend`

对于不需要添加注释的方法无需添加空有格式的注释，这样代码更整洁。

**修改建议：** 注释中对方法的描述信息不能为空，且应该使用@param、@return、@throws注解分别对方法的参数、返回值、抛出的异常信息做描述。

对于不需要添加注释的方法，将空有格式的javadoc注释删除。

✅ **正确示例：**

##### 场景1：注释不够完整
```java
// 注释信息完整

    /**
     * doSomething describe message
     *
     * @param str xxxx
     * @return List<String>  xxxx
     * @throws IOException xxxxx
     */
    private List<String> doSomething(String str) throws IOException {
        ...
    }
```

❌ **错误示例：**

##### 场景1：注释不够完整
  ```java
  // 注释不够完整  
  /**
   *
   * @param str
   * @return
   * @throws IOException
   */
  public List<String> doSomething(String str) throws IOException {
      ...
  }
  ```

---

## `G.CMT.07 正式交付给客户的代码不应包含TODO/FIXME注释` 🟢 `common_standard_recommend`

TODO注释一般用来描述已知待改进、待补充的修改点。FIXME注释一般用来描述已知缺陷。在版本开发阶段可以使用这两类标签标注一些待处理的问题。

对于版本交付的代码中不应存在这两类标签，否则可能会被误解为代码中存在未实现的功能或已知的缺陷。

**修改建议：** 正式交付的代码中，排查所有的TODO、FIXME注释，保证功能是完整的，已知缺陷都已经被修复，并将所有的TODO、FIXME注释删除。

✅ **正确示例：**

##### 场景1：交付时文件中包含TODO/FIXME注释
- 修复示例：删除TODO、FIXME注释

  ```java
  public boolean doSomething(String data) {
      ...      
      return true;
  }
  ```

❌ **错误示例：**

##### 场景1：交付时文件中包含TODO/FIXME注释
  ```java
  // 交付时文件中包含TODO/FIXME注释
  // TODO(<author-name>): 补充XX处理
  public boolean doSomething(String data) {
      ...

      // FIXME: 存在XX缺陷，需添加xxx实现代码
      ...
      return true;
  }
  ```

---

## `G.CMT.06 注释与代码之间应该有空行或空格，注释符与注释内容之间应该有空格` 🟢 `common_standard_recommend`

注释与代码之间、注释符与注释信息之间应该合理通过空行、空格进行分隔，这样可保持代码格式更加清晰。

**修改建议：** 对代码进行格式化处理。

✅ **正确示例：**

##### 场景1：在方法内部（语句级），注释与上面的代码之间未加空行。对于本范围内的最开始位置（即大括号中的第一行）的注释，注释前不需要空行。
- 修复示例：在方法内部（语句级），注释与上面的代码之间可以考虑加一个空行，以便更加清晰。对于本范围内的最开始位置的注释，注释前不需要空行。

  ```java
  public interface Example {
      /**
       * 成员变量注释 
       */      
      String SOME_FIELD = ...;

      /**
       * 成员变量注释
       */
      String OTHER_FIELD = ...;

      default int bar() throws ProblemException {
          // 变量注释          
          var aVar = ...;

          // 方法注释
          doSomething();
      }
  }
  ```
##### 场景2：代码右边的注释，与代码之间，至少留1空格
- 修复示例：代码右边的注释，与代码之间，至少留1空格。

  ```java
  int foo = 100; // 变量注释
  int bar = 200; // 变量注释
  ```

❌ **错误示例：**

##### 场景1：在方法内部（语句级），注释与上面的代码之间未加空行。对于本范围内的最开始位置（即大括号中的第一行）的注释，注释前不需要空行。
  ```java
  // 在方法内部（语句级），注释与上面的代码之间可以应加一个空行，以便更加清晰
  public interface Example {
      /**
       * 成员变量注释 
       */
      String SOME_FIELD = ...;
      /**
       * 成员变量注释
       */
      String OTHER_FIELD = ...;
      default int bar() throws ProblemException {
          // 变量注释 
          var aVar = ...;
          // 方法注释
          doSomething();
      }
  }
  ```
##### 场景2：代码右边的注释，与代码之间，至少留1空格
  ```java
  int foo = 100;//变量注释
  int bar = 200;//变量注释
  ```

---
