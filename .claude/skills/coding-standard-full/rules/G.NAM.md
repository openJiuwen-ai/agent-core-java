# G.NAM Naming 命名

共 8 条规则。

## `G.NAM.08 布尔型变量建议以表达是非意义的动词开头` 🟢 `common_standard_recommend`

布尔型的变量建议以表达是非意义的动词开头，如`is`（JavaBeans经常被使用）、`has`、`can`、`should`等。

JavaBeans规范会对布尔型的类属性自动生成`isXxx()`的getter，例如类属性isCompleted可能会生成方法`isIsCompleted()`。为避免部分自动化处理工具（如Spring，IDE，Lombok等）对布尔型的类属性的意外处理，**类似场景下，不强制要求布尔型类属性名以`is`开头**。对于这样的问题，也可以通过在IDE中定制getter/setter代码生成模版，例如常用序列化框架可通过注解的方式设置序列化属性名。

**修改建议：** 按照规范以表达是非意义的动词开头为布尔型的变量命名

✅ **正确示例：**

##### 场景1：布尔型的变量命名未以表达是非意义的动词开头
  ```java
  boolean canEvaluate; // 以表达是非意义的动词开头
  boolean hasLicense; // 以表达是非意义的动词开头
  boolean shouldAbort = false // 以表达是非意义的动词开头
  ```

❌ **错误示例：**

##### 场景1：布尔型的变量命名未以表达是非意义的动词开头
  ```java
  boolean evaluate; // 未以表达是非意义的动词开头
  ```

---

## `G.NAM.01 标识符应由不超过64字符的字母、数字和下划线组成` 🟢 `common_standard_recommend`

变量名应该只以字母数字下划线组成，且长度在[2,maxLength]之间。maxLength的默认值是64，可动态配置（上限是64）。

**修改建议：** 删除变量名称中的不合规字符或缩短变量名长度。

✅ **正确示例：**

##### 场景1：标识符里包含了除字母、数字和下划线之外的其他字符
  ```java
  int num; // 去掉标识符里面的$符号
  ```

❌ **错误示例：**

##### 场景1：标识符里包含了除字母、数字和下划线之外的其他字符
  ```java
  int num$;  // 标识符里面含有$符号
  ```

---

## `G.NAM.02 包名中的字母应小写，包名以点号分隔层级` 🟢 `common_standard_recommend`

包名仅能使用小写字母、数字、下划线，下划线只能在一些特殊情况使用，如包名以数字开头或是java中保留关键字时，如:int_.example、com.example._123name。一层包路径可以是多个单词的简单连接（不用下划线连接）。

**修改建议：** 包名修改为仅使用小写字母、数字、下划线组成。

✅ **正确示例：**

##### 场景1
- 修复示例
  ```java
    package com.huaweicloud.views; // Cloud小写-->cloud
    package com.huawei.mobilecontrol.views; // 去除mobile_control“_”-->mobilecontrol
  ```

❌ **错误示例：**

##### 场景1
- 错误示例
  ```java
    package com.huaweiCloud.views;  // Cloud没有小写，不符合规范
    package com.huawei.mobile_control.views; // mobile_control出现下划线“_”,不符合规范
  ```

---

## `G.NAM.03 类、枚举和接口名应采用大驼峰命名` 🟢 `common_standard_recommend`

类名、接口名通常是名词或名词短语，接口名还可以是形容词或形容词短语，都应采用**大驼峰命名**，例如：ArrayList（类）、Collection（接口）、Comparable（接口）等。

**修改建议：** 类、枚举和接口名应采用大驼峰命名。

✅ **正确示例：**

##### 场景1：类名没有使用大驼峰命名
  ```java
  class MarcoPolo { // 类名要采用大驼峰命名
      ...  
  }
  ```
##### 场景2：枚举类名没有使用大驼峰命名
  ```java
  enum ComparisonResult { // 枚举类名要采用大驼峰命名
      ...  
  }
  ```
##### 场景3：接口名没有使用大驼峰命名
  ```java
  interface ParameterSetNameInterface { // 接口名要采用大驼峰命名
      ...  
  }
  ```

❌ **错误示例：**

##### 场景1：类名没有使用大驼峰命名
  ```java
  class marcoPolo { // 类名采用了小驼峰命名
      ...  
  }
  ```
##### 场景2：枚举类名没有使用大驼峰命名
  ```java
  enum  comparisonResult { // 枚举类名采用了小驼峰命名
      ...  
  }
  ```
##### 场景3：接口名没有使用大驼峰命名
  ```java
  interface parameterSetNameInterface { // 接口名采用了小驼峰命名
      ...  
  }
  ```

---

## `G.NAM.04 方法名应采用小驼峰命名` 🟢 `common_standard_recommend`

方法名通常是动词或动词短语，采用**小驼峰命名**。

**修改建议：** 方法名采用小驼峰命名。

✅ **正确示例：**

##### 场景1
- 修复示例
  ```java
  public boolean isFinished() // Finished()--> isFinished()

  public void draw() // DRAW()-->draw()

  public void addKeyListener(Listener) // KeyListener(Listener)-->addKeyListener(Listener)
  ```

❌ **错误示例：**

##### 场景1
- 错误示例
  ```java
  public boolean Finished() // 大驼峰命名。

  public void DRAW() // public void KeyListener(Listener) // ```

---

## `G.NAM.05 常量名采用全大写单词，单词间以下划线分隔` 🟢 `common_standard_recommend`

java规范中的常量是指不可被修改静态的field和枚举常量。“不可被修改”需要同时满足以下两个条件：

- field的值/对象不可被修改为其他的值/对象；
- field为对象类型时，对象在初始化完成后其属性不能被修改。

常量定义的一般格式为：`[访问修饰符] static final 类型 常量名 = 常量值;`。

常量命名应该由**全大写单词与下划线**组成，单词间用下划线分隔，如CONSTANT_CASE。常量命名要尽量表达完整的语义。

不要使用魔鬼数字（难以理解的数字或字符串)，用有意义的常量代替。SQL或日志的字符串，不应视为魔鬼数字，不需定义为字符串常量；

**修改建议：** 对于常量，应该采用全大写单词与下划线进行命名。

对于魔鬼数字，可采用如下方法进行优化：

- 如果有现成的API，不要定义数字，比如判断集合内元素是否为空时，不应该使用size() == 0，应使用isEmpty()方法；比如时间的比较判断，用java.time中的API；

- 有命名模式的可以用枚举类型。

✅ **正确示例：**

##### 场景1
- 修复示例
  ```java
  static final int MAX_USER_NUM = 200; // MAXUSERNUM-->MAX_USER_NUM  ，并用final修饰。

  static final String APPLICATION_NAME = "Launcher"; // sL-->APPLICATION_NAME ，并用final修饰。

  static final int MAX_FILE_NUM = 5; // ```

❌ **错误示例：**

##### 场景1
- 错误示例
  ```java
  static final int MAXUSERNUM = 200; // 单词间应该以下划线分隔

  static final String ApplicationName = "Launcher"; // 常量名应该采用全大写单词，单词间以下划线分隔

  static final int NUM_FIVE = 5; // static final int NUM_5 = 5; // ```

---

## `G.NAM.07 避免使用具有否定含义布尔变量名` 🟢 `common_standard_recommend`

布尔型变量使用否定含义的变量名，会增加代码理解的难度，尤其是再对该变量进行逻辑非运算，如`!isNotError`。

**修改建议：** 修改变量名，去除否定含义的单词。

✅ **正确示例：**

##### 场景1：boolean型变量名中不能含有no或者not
  ```java
  boolean isError; // 去掉标识符里面的no
  ```

❌ **错误示例：**

##### 场景1：boolean型变量名中不能含有no或者not
  ```java
  boolean isNoError; // 标识符里含有no
  ```

---

## `G.NAM.06 变量采用小驼峰命名` 🟢 `common_standard_recommend`

变量的名字通常是名词或名词短语，应采用**小驼峰命名**。

即使局部变量是final或不可改变（immutable）的，也不应该把它视为常量，应采用小驼峰命名。

**修改建议：** 变量名采用小驼峰命名。

✅ **正确示例：**

##### 场景1：成员变量命名
- 修复示例
  ```java
  String customerName; // customername-->customerName小驼峰命名
  ```
##### 场景2：方法中final修饰的临时变量命名
- 修复示例
  ```java
  void doSomething() {
      final String port = "9090";
      ...
  }
  ```

❌ **错误示例：**

##### 场景1：成员变量命名
- 错误示例
  ```java
  String customername; // 变量未使用小驼峰命名
  ```
##### 场景2：方法中final修饰的临时变量命名
- 错误示例
  ```java
  void doSomething() {
      final String PORT = "9090";
      ...
  }
  ```

---
