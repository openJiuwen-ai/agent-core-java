# G.OBJ Classes & Objects 类与对象

共 10 条规则。

## `G.OBJ.05 避免基本类型与其包装类型的同名重载方法` 🟡 🔴[安全] `common_standard_recommend`

方法的重载特性允许声明名字相同、参数不同的方法（含构造方法），编译器在每次调用时都会去探查与调用参数相匹配的方法。但在自动装箱和泛型场景下，可能会导致各个重载方法之间的边界变得模糊，增加代码维护的难度，弄不清楚实际调用的是哪个方法。

**修改建议：** 对于重载方法，方法的参数类型要清晰明确，避免出现参数类型差别为封装类型与基本类型的重载方法。当方法的参数类型确实仅存在封装类型与基本类型的差别时，建议通过方法名进行区分。

✅ **正确示例：**

```java
class SomeResource {
    HashMap<Integer, Integer> hm = ...;
    public static Employee createSomeResourceByInt(int id, String name) {
        // 非重载，使用int类型的id构造对象
    }
    public static Employee createSomeResourceByInteger(Integer id, String name) {
        // 非重载，使用Integer类型的id构造对象
    }
    public Integer getDataByIndex(int id) {
        // 非重载
    }
    public String getDataByValue(Integer id) {
        // 非重载
    }
}
```

❌ **错误示例：**

```java
class SomeResource {
    HashMap<Integer, Integer> hm = ...;
    public SomeResource(int id, String name) {
        ...
    }
    public SomeResource(Integer id, String name) {
        ...
    }
    public String getData(Integer id) {
        // 获取一个特定的记录
        String str = hm.get(id).toString();
        return str + SUFFIX;
    }
    public Integer getData(int id) {
        // 获取在位置id的记录
        return hm.get(id);
    }
}
```

---

## `G.OBJ.07 子类覆写父类方法或实现接口时必须加上@Override注解` 🟡 `common_standard_rule`

加上`@Override`注解的好处是，如果覆写时因为疏忽，导致子类方法的参数同父类不一致，编译时会报错，使问题在编译期就被发现；如果父类修改了方法定义造成子类不再覆写父类方法，也能使问题在编译期尽早被发现。

**修改建议：** 子类重载的父类方法，要添加@Override注解注解。

✅ **正确示例：**

  ```java
    class BaseClass {
        public void doSomething() {
            ...
        }
    }

    class SubClass extends BaseClass {
        @Override
        public void doSomething() {
            ...
        }
    }
  ```

❌ **错误示例：**

  ```java
    class BaseClass {
        public void doSomething() {
            ...
        }
    }

    class SubClass extends BaseClass {
        public void doSomething() {
            ...
        }
    }
  ```

---

## `G.OBJ.02 不要在父类的构造方法中调用可能被子类覆写的方法` 🟡 🔴[安全] `common_standard_rule`

当在父类的构造方法中调用可能被子类覆写的方法时，构造方法的表现是不可预知的，很可能会导致异常。而问题出现后，往往难以快速定位。
这是由于在Java中，当子类初始化时，会调用父类的构造方法，当父类构造方法调用了被子类覆写的方法，往往会由于子类的初始化未完成而导致异常。

**修改建议：** 1. 将需要调用的方法修改为private修饰（构造方法中调用private方法）

2. 将需要调用的方法修改为final修饰（构造方法中调用final方法）

3. 将类修改为final类

✅ **正确示例：**

- 修复示例1：构造方法中调用private方法

```java
public class SeniorClass {
    public SeniorClass() {
        doSomething() ;  // 构造方法中调用private方法
    }
    private String doSomething() {
        ...
        return "IAmSeniorClass";
    }
}
```
- 修复示例2：构造方法中调用final方法
```java
public class SeniorClass {
    public SeniorClass() {
        doSomething() ; // 构造方法中调用final方法
    }
    public final String doSomething() {
        ...
        return "IAmSeniorClass";
    }
}
```
- 修复示例3：final类
```java
// final类
public final class SeniorClass {
    public SeniorClass() {
        doSomething() ;  
    }
    public String doSomething() {
        ...
        return "IAmSeniorClass";
    }
}
```

❌ **错误示例：**

```java
public class SeniorClass {
    public SeniorClass() {
        toString(); // 如果toString()被覆写了，可能会导致异常
    }
    @Override
    public String toString() {
        return "IAmSeniorClass";
    }
}
public class JuniorClass extends SeniorClass {
    private String name;
    public JuniorClass() {
        super(); // 调用父类的构造方法，导致NullPointerException异常
        name = "JuniorClass";
    }
    @Override
    public String toString() {
        return name.toUpperCase();
    }
}
```

---

## `G.OBJ.06 覆写equals方法时，要同时覆写hashCode方法` 🟠 🔴[安全] `common_standard_rule`

当对象需要进行逻辑相等的比较时（比如判断String、Integer对象中的值是否相同），应对Object的equals()方法进行覆写，实现具体的判断逻辑。覆写equals()方法时，要同步覆写hashCode()方法。Java对象在存放到基于Hash的集合（如HashMap、HashTable等）时，会使用其Hash码进行索引，如果只覆写了equals()方法，而没有正确覆写hashCode()方法，则会导致效率低下甚至出错。Java对象的hashCode()方法有如下约定：

- 同一次运行中，同一个对象如果equals方法中用到的信息没有改变，多次调用其hashCode方法返回值必须相同；
- 如果对两个对象调用equals方法时相等，则这两个对象的hashCode方法，也必须返回相同的值；
- 如果对两个对象调用equals方法时不相等，则对这两个对象的hashCode方法，不要求其返回值不同，但是出于减少哈希碰撞的性能考虑，最好能不同。

**修改建议：** 覆写`equals()`方法时，需要同步覆写`hashCode()`方法。

✅ **正确示例：**

- 修复示例：同步覆写`equals()`方法和`hashcode()`方法

```java
public class Entity {
    private String id;
    private String value;
    @Override
    public boolean equals(Object obj) {
        ...
        if (obj instanceof Entity) {
            Entity that = (Entity) obj;
            return Objects.equals(this.id, that.id)
                && Objects.equals(this.value, that.value);
        }
        ...
        return false;
    }

    // 覆写`equals()`方法时，需要同步覆写`hashCode()`方法
    @Override
    public int hashCode() {
        int result = 0;
        ...
        return result;        
    }
}
```

❌ **错误示例：**

- 错误示例：仅覆写`equals()`方法，未同步覆写`hashcode()`方法

```java
public class Entity {
    private String id;
    private String value;
    @Override
    public boolean equals(Object obj) {
        ...
        if (obj instanceof Entity) {
            Entity that = (Entity) obj;
            return Objects.equals(this.id, that.id)
                && Objects.equals(this.value, that.value);
        }
        ...
        // 未覆写`hashCode()`方法
        return false;
    }
}
```

---

## `G.OBJ.04 避免在无关的变量或无关的概念之间重用名字，避免隐藏（hide）、遮蔽（shadow）和遮掩（obscure）` 🟠 🔴[安全] `common_standard_rule`

一个变量、方法或类可以分别遮蔽（shadow）在类内部具有相同名字的变量、方法或类。如果一个实体被遮蔽了，那么就无法用简单名引用到它。

对于以下场景工具不会告警：

- 构造器参数变量
- setter方法参数变量

**修改建议：** 对于方法中的临时变量，避免与类的属性重名。

✅ **正确示例：**

```java
    public class HiddenField {
      Object obj;

      public void doSomething(Object prama) {
          ...
      }
      ...
  }
```

❌ **错误示例：**

```java
  public class HiddenField {
      Object obj;

      public void doSomething(Object obj) {
          ...
      }
      ...
  }
```

---

## `G.OBJ.01 应避免定义public且非final的类属性` 🟢 🔴[安全] `common_standard_recommend`

应避免定义public且非final的类属性。将类的属性设置为私有（private）的理由是：不希望类的外部代码依赖这个属性，依赖类内部的实现细节。这样，当内部实现需要变更时，影响面就比较小，变更的成本就比较低。

**修改建议：** 将public类型的属性更改为private或设置为final。

✅ **正确示例：**

- 修复示例：将public类型的成员变量改为private类型

```java
public class UserInfo {
    private String userName;
    private String addr;
    private int age;

    ...
}
```

❌ **错误示例：**

- 错误示例：类含有public类型的成员变量

```java
public class UserInfo {
    public String userName;
    public String addr;
    public int age;

    ...
}
```

---

## `G.OBJ.03 构造方法如果有多个，尽量重用` 🟡 `common_standard_recommend`

由于可选参数导致的存在多个构造方法时，参数少的构造方法可以重用参数更多的构造方法，这样可以是代码更加简洁。

**修改建议：** 由于可选参数导致的存在多个构造方法时，参数少的构造方法重用参数更多的构造方法。

✅ **正确示例：**

- 修复示例：复用参数更多的构造方法

  ```java
    public class Student {
        private String name;
        private String sex;
        private int weight; // 可选参数
        private int height; // 可选参数
        private int age;    // 可选参数
        public Student(String name, String sex) {
            this(name, sex, 0);
        }
        public Student(String name, String sex, int weight) {
            this(name, sex, weight, 0);
        }
        public Student(String name, String sex, int weight, int height) {
            this(name, sex, weight, height, 0);
        }
        public Student(String name, String sex, int weight, int height, int age) {
            this.name = name;
            this.sex = sex;
            this.weight = weight;
            this.height = height;
            this.age = age;
        }
        ...
    }
  ```

❌ **错误示例：**

  ```java
    public class Student {
        private String name;
        private String sex;
        private int weight; // 可选参数
        private int height; // 可选参数
        private int age;    // 可选参数
        public Student(String name, String sex) {
            this.name = name;
            this.sex = sex;
        }
        public Student(String name, String sex, int weight) {
            this.name = name;
            this.sex = sex;
            this.weight = weight;
        }
        public Student(String name, String sex, int weight, int height) {
            this.name = name;
            this.sex = sex;
            this.weight = weight;
            this.height = height;
        }
        public Student(String name, String sex, int weight, int height, int age) {
            this.name = name;
            this.sex = sex;
            this.weight = weight;
            this.height = height;
            this.age = age;
        }
        ...
    }
  ```

---

## `G.OBJ.09 使用类名调用静态方法，而不要使用实例或表达式来调用` 🟠 🔴[安全] `common_standard_rule`

明确地使用类名调用静态方法不容易造成混淆。使用实例调用静态方法时，调用的静态方法是声明类型的静态方法，与实例的实际类型无关，可能会导致与预期的结果不一致。当父类和子类有同名静态方法时，声明父类变量引用子类实例，使用该实例调用同名的静态方法调用的是父类的静态方法，而非子类的静态方法。类的静态属性也要使用类名进行调用。

**修改建议：** 使用类名来调用静态方法或静态变量。

✅ **正确示例：**

##### 场景1：使用类名调用静态方法
- 修复示例： 用类名来调用静态方法

```java
class Dog {
    public static void bark() {
        System.out.print("woof");
    }
}

class Basenji extends Dog {
    public static void bark() {
        System.out.println("miao");
    }
}

public class Bark {
    public static void main(String[]  args) {
        Dog.bark();
        Basenji.bark();
    }
}
```

❌ **错误示例：**

##### 场景1：使用类名调用静态方法
- 错误示例： 上述示例中，对bark()的两次调用，实际调用的都是Dog.bark()方法。

```java
class Dog {
    public static void bark() {
        System.out.println("woof");
    }
}

class Basenji extends Dog {
    public static void bark() {
        System.out.println("miao");
    }
}

 public class Bark {
    public static void main(String[]  args) {
        Dog woofer = new Dog();
        Dog nipper = new Basenji();
        woofer.bark();
        nipper.bark();
    }
}
```

---

## `G.OBJ.08 正确实现单例模式` 🟢 🔴[安全] `common_standard_recommend`

单例模式（Singleton Pattern）属于创建型模式，它确保在同一个进程内，单例类只有一个对象，并且该对象对所有其他对象提供访问，常见的如Windows系统下的资源管理器、Spring Bean等都会采用这种方式。

**修改建议：** 可通过如下方式保证正确实现单例模式：

- 将其构造方法设为私有；

- 防止对象在初始化被多个线程同时运行；

- 确保该对象不可序列化；

- 确保该对象无法克隆。

✅ **正确示例：**

##### 场景1：将构造方法设置为private
  ```java
  public class Singleton {
      private static Singleton instance = null;
      private Singleton() {
      }
      public static synchronized Singleton getSingletonInstance() {
          if (instance == null) {
              instance = new Singleton();
          }
          return instance;
      }
  }
  ```
##### 场景2：双重检查锁实现单例模式
  ```java
  public class Singleton {
      private static volatile Singleton instance = null;
      private Singleton() {
          ...
      }
      public static Singleton getSingletonInstance() {
          if (instance == null) {
              synchronized (Singleton.class) {
                  if (instance == null) {
                      instance = new Singleton();
                  }
              }
          }
          return instance;
      }
  }
  ```
##### 场景3：单例模式的Class禁止支持序列化操作
  ```java
  public class Singleton { // 不实例化Serializable接口
      private static volatile Singleton instance = null;
      private Singleton() {
          ...
      }
      public static Singleton getSingletonInstance() {
          ...    
      }
  }
  ```
##### 场景4：单例模式的Class禁止实现Clone功能
  ```java
  public class Singleton { // 不实例化Cloneable接口
      private static volatile Singleton instance = null;
      private Singleton() {
          ...
      }
      public static Singleton getSingletonInstance() {
          ...    
      }
  }
  ```

❌ **错误示例：**

##### 场景1：将构造方法设置为private
- 错误示例：非私有构造方法

  ```java
  public class Singleton {
      private static Singleton instance = null;
      protected Singleton() {
          ...
      }
      public static Singleton getSingletonInstance() {
          if (instance == null) {
              instance = new Singleton();
          }
          return instance;
      }
  }
  ```
##### 场景2：双重检查锁实现单例模式
- 错误示例：并发场景导致无法正确实现单例模式

  ```java
  public class Singleton {
      private static Singleton instance = null;
      private Singleton() {
          ...
      }
      public static Singleton getSingletonInstance() {
          if (instance == null) {
              instance = new Singleton();
          }
          return instance;
      }
  }
  ```
##### 场景3：单例模式的Class禁止支持序列化操作
- 错误示例：通过反序列化来构造多个实例

  ```java
  public class Singleton implements Serializable {
      private static final long serialVersionUID = 6289738106308341737L;
      private static volatile Singleton instance = null;
      private Singleton() {
          ...
      }
      public static Singleton getSingletonInstance() {
          ...    
      }
  }
  ```
##### 场景4：单例模式的Class禁止实现Clone功能
- 错误示例：通过clone机制获得新的实例

  ```java
  public class Singleton implements Cloneable {
      private static volatile Singleton instance = null;
      private Singleton() {
          ...
      }
      public static Singleton getSingletonInstance() {
          ...    
      }
  }
  ```

---

## `G.OBJ.10 接口定义中去掉多余的修饰词` 🟡 `common_standard_recommend`

在接口定义中，属性缺省具有public static final修饰词，非默认方法缺省具有public abstract修饰词。代码中不需要再次提供这些修饰词。

**修改建议：** 删除接口中的属性与非默认方法的缺省修饰符。

通过类名调用

✅ **正确示例：**

##### 场景1：接口中的属性缺省修饰符需省略
- 修复示例： 接口属性声明中不使用冗余修饰符

  ```java
    public interface ParameterSetNameInterface {
        int STATIC_VAR = 100;
    }
  ```

❌ **错误示例：**

##### 场景1：接口中的属性缺省修饰符需省略
- 错误示例：接口的属性声明使用了冗余的`public static final`修饰符

  ```java
    public interface ParameterSetNameInterface {
        public static final int STATIC_VAR = 100;
    }
  ```

---
