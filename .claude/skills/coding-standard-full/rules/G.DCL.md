# G.DCL Declarations 声明

共 5 条规则。

## `G.DCL.04 避免枚举常量序号的产生依赖于ordinal()方法` 🟡 🔴[安全] `common_standard_recommend`

Java枚举类型通过`ordinal()`方法返回枚举常量的排列序号。默认情况下，序号是根据声明顺序从0开始累加，但某些情况下会希望指定某些枚举常量为某个固定值以代表特殊意义（例如，键盘某个按键的具体编码），返回该固定值的方法不应基于`ordinal()`方法来实现。

**修改建议：** 为枚举常量赋固定的值，消除对ordinal()方法的依赖。

✅ **正确示例：**

##### 场景1：新增枚举常量时可能会导致原有枚举常量的值发生变化
  ```java
  enum Keyboard {
      MOUSE_KEY_LEFT(1),
      MOUSE_KEY_RIGHT(2),
      MOUSE_KEY_CANCEL(4),
      MOUSE_KEY_MIDDLE(8);

      private final int mouseKeyValue;

      Keyboard(int value) {
          this.mouseKeyValue = value;
      }

      public int getMouseKeyValue() {
          return mouseKeyValue;
      }
  }
  ```

❌ **错误示例：**

##### 场景1：新增枚举常量时可能会导致原有枚举常量的值发生变化
  ```java
  enum Keyboard {
      MOUSE_KEY_LEFT,
      MOUSE_KEY_RIGHT,
      MOUSE_KEY_CANCEL,
      MOUSE_KEY_MIDDLE;

      public int getMouseKeyValue() {
          return ordinal() + 1;
      }
  }
  ```

---

## `G.DCL.02 局部变量被声明在接近它们首次使用的行` 🟢 🔴[安全] `common_standard_recommend`

将局部变量声明在接近它们首次被使用的点，以最小化局部变量的范围。当前工具会检查变量声明与变量使用间隔的代码行，超过`maxInterval（默认值10）行时会告警`。maxInterval可动态配置。

局部变量通常在声明时初始化，或在声明后立即被初始化，无需在声明时为局部变量设置无效的null值；另外，局部变量在使用结束后也不需要主动设置为null，类的成员变量要集中声明。

**修改建议：** 将局部变量声明接近它们首次被使用的点，并在声明时初始化。

✅ **正确示例：**

##### 场景1：局部变量声明未尽量接近首次被使用的点，并在使用时才初始化
  ```java
  public List<String> getResult(String param1, String param2) {
      ...
      List<String> result = new ArrayList<>(); // 首次被使用时才初始化
      result.addAll(getResult(param1));
      result.addAll(getResult(param2));
      return result;
  }
  ```

❌ **错误示例：**

##### 场景1：局部变量声明未尽量接近首次被使用的点，并在使用时才初始化
  ```java
  public List<String> getResult(String param1, String param2) {
      List<String> result = new ArrayList<>();  // 初始化到首次被使用时超过10行
      ...
      ... // 此处省略超过10行代码
      ...
      result.addAll(getResult(param1));
      result.addAll(getResult(param2));
      return result;
  }
  ```

---

## `G.DCL.01 每行声明一个变量` 🟡 🔴[安全] `common_standard_rule`

每行的变量声明（类属性或局部变量）都只声明一个变量。

**修改建议：** 每个变量声明都单独占一行。

✅ **正确示例：**

##### 场景1：代码块未换行
- 修复示例1：遵循K&R风格换行

  ```java
  int length;
  int result;
  ```

❌ **错误示例：**

##### 场景1：代码块未换行
  ```java
  int length, result;
  ```

---

## `G.DCL.03 禁止C风格的数组声明` 🟡 `common_standard_rule`

数组类型由数据元素类型紧跟中括号（[]）组成，数组声明格式应该是`String[] nonEmptyArray`，而不是`String nonEmptyArray[]`。

初始化数组时，数组中的最后一个元素后不要添加**逗号**，例如`String[] nonEmptyArray = {"these", "can", "change",};`。

**修改建议：** 使用类似`String[] array`风格的数组声明方式；

数组初始化时，删除数组中最后一个元素后多余的逗号。

✅ **正确示例：**

##### 场景1：数组声明格式错误
  ```java
  String[] nonEmptyArray = {"these", "can", "change"};
  ```
##### 场景2：数组初始化时，最后一个元素后有冗余的逗号
  ```java
  String[] nonEmptyArray = {"these", "can", "change"};
  ```

❌ **错误示例：**

##### 场景1：数组声明格式错误
  ```java
  String nonEmptyArray[] = {"these", "can", "change"};
  ```
##### 场景2：数组初始化时，最后一个元素后有冗余的逗号
  ```java
  String[] nonEmptyArray= {"these", "can", "change", };
  ```

---

## `G.DCL.05 禁止将mutable对象声明为public static final` 🟡 `common_standard_rule`

使用public static final的意图是定义一个常量。如果用其修饰一个mutable（可变）对象，极易产生不当使用，造成功能异常。

**修改建议：** 对于`public static final`修饰的mutable对象，应该使用小驼峰命名，或者是将mutable对象修改为immutable对象，并使用大写字母命名。

✅ **正确示例：**

##### 场景1：两个List集合都是mutable，不应该定义为常量
  ```java
  public static final List<String> EMPTY_RESULT_LIST = Collections.unmodifiableList(new ArrayList<>());
  public static final List<String> EMPTY_RESULT_LIST = Collections.emptyList();
  ```
  ```java
  private static final List<String> infoList= new ArrayList<>();
  ```

❌ **错误示例：**

##### 场景1：两个List集合都是mutable，不应该定义为常量
  ```java
  public static final List<String> EMPTY_RESULT_LIST = new ArrayList<>();
  public static final List<String> RESULT_LIST = Arrays.asList("result1", "result2");
  ```

---
