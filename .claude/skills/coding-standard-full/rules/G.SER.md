# G.SER Serialization 序列化

共 5 条规则。

## `G.SER.02 实现Serializable接口的可序列化类应该显式声明serialVersionUID` 🟡 🔴[安全] `common_standard_recommend`

如果可序列化类未显式声明serialVersionUID，则序列化运行时将基于该类的各个方面计算该类的默认serialVersionUID值，如“Java(TM) 对象序列化规范”中所述。但是，强烈建议所有可序列化类都显式声明serialVersionUID值，原因是计算默认的serialVersionUID对类的详细信息具有较高的敏感性，根据编译器实现的不同可能千差万别，这样在反序列化过程中可能会导致意外的`InvalidClassException`。因此，为保证serialVersionUID值跨不同java编译器实现的一致性，序列化类必须声明一个明确的serialVersionUID值。

同时强烈建议使用private显式声明serialVersionUID，原因是这种声明仅应用于当前声明类，serialVersionUID作为继承成员没有用处。

**修改建议：** 对于需要实现序列化接口的Class，应该显式声明serialVersionUID。

✅ **正确示例：**

- 修复示例：显式声明serialVersionUID

  ```java
  public class BeanType implements Serializable {
      private static final long serialVersionUID = -2589766491699675794L;
      ...
  }
  ```

❌ **错误示例：**

- 错误示例：实现Serializable接口的类未显式声明serialVersionUID

  ```java
  public class BeanType implements Serializable {
      ...
  }
  ```

---

## `G.SER.05 禁止序列化非静态的内部类` 🔴 🔴[安全] `security_standard_rule`

内部类是没有显式或隐式声明为静态的嵌套类。内部类（包括本地类和匿名类）的序列化很容易出错。

在使用非静态内部类时，实际上隐含着对外部类实例的非transient引用，在对内部类进行序列化时，会一起将外部类也序列化。
内部类的实现与synthetic属性有关，对synthetic关键字，不同的编译器的实现不同，会影响程序的兼容性。并且会跟默认的serialVersionID产生冲突。
内部类不能声明静态成员以外的运行时常量，所以不能使用serialPersistentFields机制来指定可以序列化的属性。
与外部实例关联的内部类没有无参构造方法（此内部类的构造方法隐式的接收外部实例作为前置参数）。内部类无法实现Externalizable接口，Externalizable接口要求实现对象通过writeExternal()和readExternal()方法手动保存和恢复其状态。
基于以上原因，禁止序列化非静态内部类。但是这些原因不适用于静态内部类，所以静态内部类可以进行序列化。

**修改建议：** 对于支持序列化操作类，其内部类设置为静态类。

✅ **正确示例：**

- 修复示例1：支持序列化的类其内部类为静态类
```java
public class TestClass implements Serializable {
    private int rank;

    static class InnerSer implements Serializable {
        protected String name;
    }
}
```

❌ **错误示例：**

- 错误示例：支持序列化的类的内部类为非静态类

```java
public class SomeResource implements Serializable {
    private int rank;

    // 禁止序列化非静态的内部类
    class InnerSer implements Serializable {
        protected String name;
    }
}
```

---

## `G.SER.07 防止反序列化被利用来绕过构造方法中的安全操作` 🔴 🔴[安全] `security_standard_rule`

防止反序列化被利用来绕过构造方法中的安全操作。

反序列化操作可以在不执行构造方法的情况下创建对象的实例，所以反序列化操作中的行为应该设计为与构造方法保持一致，这些行为包括：

1. 对参数的校验；

2. 安全管理器的检查；

3. 对属性赋初始值，特别是transient修饰的属性反序列化操作默认不会赋值；

否则，攻击者就可能会通过反序列化操作构造出与预期不符合的对象实例。

**修改建议：** 如果可序列化的类的构造方法中存在SecurityManager检查，请确保readObject()方法中存在相同的SecurityManager检查。

✅ **正确示例：**

##### 场景1：安全管理器的检查。
- 修复示例1：方法中有安全检查器：
```java
public final class SecureSerializeDemo implements Serializable {
    private static final long serialVersionUID = 9078808681344666097L;

    // Private internal state
    private String town;

    private static final String UNKNOWN = "UNKNOWN";

    void performSecurityManagerCheck() throws SecurityException {
        // verify whether current user has rights to access the file
        SecurityManager securityManager = System.getSecurityManager();
    }

    public SecureSerializeDemo () {
        performSecurityManagerCheck();

        // Initialize town to default value
        town = UNKNOWN;
    }

    private void readObject(ObjectInputStream in) throws Exception {
        performSecurityManagerCheck();
        in.defaultReadObject();
    }
}
```

❌ **错误示例：**

##### 场景1：安全管理器的检查。
- 错误示例：方法中没有安全检查器。

```java
public final class SecureSerializeDemo implements Serializable {
    private static final long serialVersionUID = 9078808681344666097L;

    // Private internal state
    private String town;

    private static final String UNKNOWN = "UNKNOWN";

    void performSecurityManagerCheck() throws SecurityException {
        // verify whether current user has rights to access the file
        SecurityManager securityManager = System.getSecurityManager();
    }

    public SecureSerializeDemo () {
        performSecurityManagerCheck();
        // Initialize town to default value
        town = UNKNOWN;
    }

    // 实现Serializable接口，并且在实现类的构造函数中包含安全检查器 2）基于第一点，工具检测readObject和writeObject方法中是否包含安全检查器，如果没有则报告警。
    private void readObject(ObjectInputStream in) throws Exception {
        in.defaultReadObject();
    }

}
```

---

## `G.SER.04 禁止直接序列化指向系统资源的信息` 🟠 🔴[安全] `common_standard_rule`

当序列化结果中含有指向系统的资源时，这些信息很容易被篡改。当恶意用户篡改了指向系统的资源时，反序列化的对象会直接操作这些被攻击者指定的系统资源，导致任意文件读取或修改。

**修改建议：** 禁止直接序列化指向系统资源的信息，如实现Serializable的类，其成员变量为File或FileDescriptor时，用transient修饰，避免这些对象被序列化。

✅ **正确示例：**

##### 场景1： 类成员变量为File，且被序列化
- 修复示例1：用transient修饰类成员变量File

  ```java
  final class SomeResource implements Serializable {
      private static final long serialVersionUID = 6562477636399915529L;

      private transient File file;

      public SomeResource(String fileName) {
          file = new File(fileName);
          ...
      }
  }
  ```

❌ **错误示例：**

##### 场景1： 类成员变量为File，且被序列化
  ```java
  final class SomeResource implements Serializable {
      private static final long serialVersionUID = -2589766491699675794L;

      private File file;

      public SomeResource(String fileName) {
          file = new File(fileName);
          ...
      }
  }
  ```

---

## `G.SER.01 尽量避免实现Serializable接口` 🟢 `common_standard_recommend`

对于支持序列化操作的类，其实例属性如果不支持序列化操作，在对该类进行序列化操作时会出异常。class中的静态属性、transient修饰的非静态属性不会进行序列化操作。

**修改建议：** 对于支持序列化操作的类，其实例属性需支持序列化操作，不需要进行序列化操作的属性可以设置为transient。

✅ **正确示例：**

- 修复示例1：使用transient修饰属性
  ```java
  public class AvoidSerialization implements Serializable {

     public transient AttributeClass attribute= new AttributeClass ();
     ...
  }

   class AttributeClass{
   ...
   }
  ```
- 修复示例2：使用static修饰属性
  ```java
  public class AvoidSerialization implements Serializable {

     public static AttributeClass attribute= new AttributeClass ();
     ...
  }

   class AttributeClass{
   ...
   }
  ```
- 修复示例3：属性类实例化Serializable 
  ```java
  public class AvoidSerialization implements Serializable {

     public static AttributeClass attribute= new AttributeClass ();
     ...
  }

   class AttributeClass implements Serializable{
   ...
   }
  ```

❌ **错误示例：**

- 错误示例：支持序列化操作的类的属性不支持序列化操作

  ```java
  public class AvoidSerialization implements Serializable {

     public AttributeClass attribute= new AttributeClass ();
     ...
  }

   class AttributeClass{

   }
  ```

---
