# G.ERR Exception Handling 异常处理

共 11 条规则。

## `G.ERR.08 不要使用return、break、continue或抛出异常使finally块非正常结束--在finally块中使用return语句` 🟠 🔴[安全] `common_standard_rule`

从finally块中返回会导致异常的丢失。

在finally代码块中，直接使用return、break、continue、throw语句，或由于调用方法的异常未处理， 会导致finally代码块无法正常结束。非正常结束的finally代码块会影响try或catch代码块中异常的抛出， 也可能会影响方法的返回值。所以要保证finally代码块正常结束。

**修改建议：** 将返回指令移到 finally 块之外。如果必须要 finally 块返回一个值，可以简单地将该返回值赋给一个本地变量，然后在 finally 块执行完毕后返回该变量。

✅ **正确示例：**

main方法中捕获到异常。
```java
public static void main(String[] args) {
    try {
        System.out.println(func());
    } catch (MyException ex) {
        // 处理异常
    }
}
public static int func() throws MyException {
    for (int i = 1; i < 2; i++) {
        try {
            throw new MyException();
        } finally {
            // ...
        }
    }
    return 0;
}
```

❌ **错误示例：**

main方法中不会捕获到异常，而是直接输出-1。
```java
public static void main(String[] args) {
    try {
        System.out.println(func());
    } catch (MyException ex) {
        // 处理异常
    }
}

public static int func() throws MyException {
    for (int i = 1; i < 2; i++) {
        try {
            throw new MyException();
        } finally {
            continue; // 不推荐
        }
    }
    return 0;
}
```

---

## `G.ERR.08 不要使用return、break、continue或抛出异常使finally块非正常结束--在finally块中使用throw语句` 🟠 🔴[安全] `common_standard_rule`

禁止在finally块中抛出异常。

在finally代码块中，直接使用return、break、continue、throw语句，或由于调用方法的异常未处理，会导致finally代码块无法正常结束。非正常结束的finally代码块会影响try或catch代码块中异常的抛出，也可能会影响方法的返回值。所以要保证finally代码块正常结束。

**修改建议：** 将返回指令移到finally块之外。如果必须要finally块返回一个值，可以简单地将该返回值赋给一个本地变量，然后在finally块执行完毕后返回该变量。

✅ **正确示例：**

main方法中捕获到try中的异常信息。
```java
public static void main(String[] args) {
    try {
        System.out.println(func());
    } catch (MyException ex) {
        // 处理异常
    }
}
public static int func() throws MyException {
    for (int i = 1; i < 2; i++) {
        try {
            throw new MyException("try");
        } finally {
            // ...
        }
    }
    return 0;
}
```

❌ **错误示例：**

main方法中捕获到finally中的异常信息，忽略了try中的异常信息。
```java
public static void main(String[] args) {
    try {
        System.out.println(func());
    } catch (MyException ex) {
        // 处理异常
    }
}
public static int func() throws MyException {
    for (int i = 1; i < 2; i++) {
        try {
            throw new MyException("try");
        } finally {
            throw new MyException("finally"); // 不推荐
        }
    }
    return 0;
}
```

---

## `G.ERR.09 不要调用System.exit()终止JVM` 🟡 🔴[安全] `security_standard_recommend`

不要调用System.exit()。

System.exit()会结束当前正在运行的Java虚拟机（JVM），导致拒绝服务攻击。例如，在某个web请求的处理逻辑中调用System.exit()，会导致web容器停止运行。系统中应避免无意和恶意地调用System.exit()。

**修改建议：** 不要调用System.exit()终止JVM。

✅ **正确示例：**

##### 场景1：调用System.exit()。
- 修复示例：程序正确运行：
```java
LOGGER.info("exit");
```

❌ **错误示例：**

##### 场景1：调用System.exit()。
- 错误示例：调用exit函数导致结束当前正在运行的Java虚拟机。

```java
System.exit(1);
LOGGER.info("exit");
```

---

## `G.ERR.04 防止通过异常泄露敏感信息` 🟡 🔴[安全] `security_standard_rule`

避免使用printStackTrace()输出异常信息。

程序抛出的异常中，可能会包含一些敏感信息，将这些异常直接记录到日志或反馈给用户，会导致敏感信息泄露风险。另外，即使异常中不含敏感信息，但是直接将异常反馈给用户，该动作本身可能就会导致敏感信息泄露风险，比如系统访问用户指定的文件路径，当该路径不存在时，系统给用户反馈一个过滤了敏感信息的异常，恶意用户可以根据系统是否抛出异常来构造文件路径，达到对系统的文件目录结构进行探测的目的。

附录C 敏感异常列出了一些常见的需要注意的Java原生异常类型，除此之外，三方件也可能会抛出携带敏感信息的异常，如 JSONException 等。

**修改建议：** 不要调用printStackTrace方法。

✅ **正确示例：**

- 修复示例1：敏感异常记录日志前对敏感信息进行脱敏处理
```java
// filePath、directory已经规范化处理
public static String readFile(String filePath, String directory) throws MyBizException {
    ...
    try {
        fileInputStream = new FileInputStream(filePath);
        ...
    } catch (FileNotFoundException ex) {
        LOGGER.error("Invalid file ... {}", cleanMessage(ex));
        throw new MyBizException("Invalid file ...");
    }
    ...
}
```

❌ **错误示例：**

- 错误示例1：敏感异常直接抛出给前端用户
```java
public static String readFile(String filePath) throws IOException {
    FileInputStream fileInputStream;
    try {
        fileInputStream = new FileInputStream(filePath);
        ...
    } catch (FileNotFoundException ex) {
        throw new IOException("Unable to retrieve file.", ex);
    }
    ...
}
```

- 错误示例2：敏感异常直接记录日志
```java
public static String readFile(String filePath) throws MyBizException {
    ...
    try {
        fileInputStream = new FileInputStream(filePath);
        ...
     } catch (FileNotFoundException ex) {
        LOGGER.error("Invalid file ... {}", ex); // 在日志中记录敏感异常
        throw new MyBizException("...");
    }
    ...
}
```

---

## `G.ERR.03 不要直接捕获可通过预检查进行处理的RuntimeException ，如NullPointerException 、IndexOutOfBoundsException 等` 🟡 🔴[安全] `security_standard_recommend`

可通过预检查的方式进行消除的RuntimeException，这类异常一般表示程序逻辑错误，不应该通过try...catch的方式进行处理（这也可能会影响代码的可读性及系统的运行效率）。推荐通过预检查方式进行消除，该类运行期异常包括：NullPointerException、IndexOutOfBoundsException等。对于NumberFormatException、IllegalArgumentException、IllegalStateException等可通过try...catch方式处理。

**修改建议：** 不要捕获NullPointerException异常。

✅ **正确示例：**

```java
public class SomeDemo {
    private boolean doSomething(String str) {
        if (str == null) {
            return false;
        }
        String[] names = str.split(" ");
        if (names.length != 2) {
            return false;
        }
        return (isCapitalized(names[0]) && isCapitalized(names[1]));
    }

    private boolean isCapitalized(String str) {
        for (int i = 0; i < str.length(); i++) {
            if (!(str.charAt(i) >= 'A' && str.charAt(i) <= 'Z')) {
                return false;
            }
        }
        return true;
    }
}
```

❌ **错误示例：**

```java
public class SomeDemo {
    private boolean doSomething1(String str) {
        try {
            String[] names = str.split(" ");

            if (names.length != 2) {
                return false;
            }
            return (isCapitalized(names[0]) && isCapitalized(names[1]));
            } catch (NullPointerException e) {
            return false;
        }
    }

    private boolean doSomething2(String str) {
        if (str == null) {
            return false;
        }
        try {
            String[] names = str.split(" ");
            return (isCapitalized(names[0]) && isCapitalized(names[1]));
            } catch (ArrayIndexOutOfBoundsException e) {
            return false;
        }
    }

    private boolean doSomething3(String str) {
        try {
            String[] names = str.split(" ");
            return (isCapitalized(names[0]) && isCapitalized(names[1]));
            } catch (ArrayIndexOutOfBoundsException | NullPointerException e) {
            return false;
        }
    }

    private boolean isCapitalized(String str) {
        for (int i = 0; i < str.length(); i++) {
            if (!(str.charAt(i) >= 'A' && str.charAt(i) <= 'Z')) {
                return false;
            }
        }
        return true;
    }
}
```

---

## `G.ERR.07 一个方法不应抛出超过5个异常，并在Javadoc的@throws标签中记录每个抛出的异常及其条件` 🟢 `common_standard_recommend`

方法抛出过多的异常，会增加上层调用方法中的异常处理的工作，同时也表明方法承担了过多的职责，推荐一个方法最多抛出maxThrowNum（默认值为5，支持动态配置）类异常，包括受检异常和运行时异常。方法抛出的异常中应该避免存在继承关系，存在继承关系时，仅保留父类异常。

**修改建议：** 方法抛出异常的类型超过maxThrowNum时，建议对方法进行合理拆分或对异常进行封装。

✅ **正确示例：**

- 修复示例：减少throw的异常种类

  ```java
  private static byte[] getNewBytes(byte[] password, byte[] tmpByte) throws NoSuchAlgorithmException, NoSuchPaddingException { 
      ...
  }
  ```

❌ **错误示例：**

- 错误示例：方法throw的异常种类超过5种

  ```java
  private static byte[] getNewBytes(byte[] password, byte[] tmpByte) throws NoSuchAlgorithmException, NoSuchPaddingException,         
      InvalidKeyException,InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException { 
      ...
  }
  ```

---

## `G.ERR.01 不要通过一个空的catch块忽略异常` 🟡 🔴[安全] `common_standard_rule`

异常表示程序运行发生了错误，发生异常会中断程序的正常处理流程。不应该使用空的catch块会忽略发生的异常，发生异常要么在catch块中对异常情况进行处理，要么将异常抛出，交由上层调用方进行处理。

**修改建议：** 代码中避免出现空的catch块，确实需要空catch块，建议添加注释，解释为什么可以忽略该异常。

✅ **正确示例：**

##### 场景1：代码中应避免空的catch块
- 修复示例： 消除代码中的空catch块
  ```java
  InputStream inputStream = null;
  try {
      inputStream = new InputStream(null);
      doSomething();
  } catch (Exception t) {
      logger.error("something is wrong");
  }    
  ```

❌ **错误示例：**

##### 场景1：代码中应避免空的catch块
- 错误示例： 使用空catch块忽略异常
  ```java
  InputStream inputStream = null;
  try {
      inputStream = new InputStream(null);
      doSomething();
  } catch (Exception t) {
  }
  ...
  ```

---

## `G.ERR.02 不要直接捕获异常的基类Throwable、Exception、RuntimeException` 🟡 `common_standard_recommend`

捕获异常的目的是为了将程序从异常状态恢复或对异常进行针对性处理，而如果不加区分的直接捕获基类异常，则会忽略程序抛出的异常类型，无法对各类异常进行有针对性地恢复处理，另外不利于代码的可读性、可维护性。

当程序中的多种异常使用同一逻辑进行处理时，可以用并语法(ExceptionType1 | ... | ExceptionTypeN 变量)来减少重复代码。

**修改建议：** 捕获具体的子异常类型，使用语法`(ExceptionType1 | ... | ExceptionTypeN 变量)`合并处理逻辑相同的异常。

✅ **正确示例：**

##### 场景1：直接捕获并处理Exception
  ```java
    try {
        doSomething();
    } catch (IOException  ex) {  //应根据实际情况捕获具体的子异常类
        handleException(ex);
    }
  ```
##### 场景2：对于相同处理逻辑的异常，建议通过并语法(ExceptionType1 | ... | ExceptionTypeN 变量)来减少重复代码
  ```java
    try {
        doSomething();
    } catch (ParseException | IOException ex) {
        handleException(ex);
    } 
  ```

❌ **错误示例：**

##### 场景1：直接捕获并处理Exception
- 错误示例：直接捕获异常的基类

  ```java
    try {
        doSomething();
    } catch (Exception ex) {
        handleException(ex);
    }
  ```
##### 场景2：对于相同处理逻辑的异常，建议通过并语法(ExceptionType1 | ... | ExceptionTypeN 变量)来减少重复代码
  ```java
    try {
        doSomething();
    } catch (ParseException  ex) {
        handleParseException(ex);
    } catch (IOException  ex) {
        handleIOException(ex);
    }
  ```

---

## `G.ERR.10 尽量消除非受检的异常，不应该在整个类上使用SuppressWarning` 🟡 🔴[安全] `common_standard_recommend`

在源代码中通过@SuppressWarning("unchecked")屏蔽告警，是个坏的实践。它丢失了类型安全和描述性的好处。

然而有些Java API，是用Object obj来存储数据对象的，当数据被取出来用时，不得不转换为用户数据对象。这时可能会有强制类型转换的告警，例如：[unchecked] unchecked method invocation。

非受检警告很重要，不要轻易忽略它们。应该始终在最小的范围内使用@SuppressWarning注解，一般是在变量声明，简短的语句或方法上。

**修改建议：** 如果需要使用@SuppressWarning注解来抑制告警信息，@SuppressWarning注解的应用范围应该最小化，避免直接为Class、Enum添加@SuppressWarning注解。

✅ **正确示例：**

##### 场景1：@SuppressWarning注解的范围最小化
  ```java
    public class SuppressWarnings {

        @SuppressWarnings("uncheck")
        public void doSomething(List<String> list) {
        }
    }
  ```

❌ **错误示例：**

##### 场景1：@SuppressWarning注解的范围最小化
  ```java
    @SuppressWarnings("uncheck")
    public class SuppressWarnings {

        public void doSomething(List<String> list) {
        }
    }
  ```

---

## `G.ERR.05 方法抛出的异常，应该与本身的抽象层次相对应` 🟡 🔴[安全] `common_standard_rule`

方法抛出异常时，应该避免直接抛出RuntimeException，更不应该直接抛出Exception或Throwable，因为这些父类异常无法与异常发生的场景相关联，直接抛出父类异常会降低代码可读性。方法抛出的异常应该与方法本身的抽象层次相对应，这些异常可以是JDK中定义的标准异常，也可以是业务层自定义的异常。另外，抛出的异常中应该包含理解该异常产生原因的所有信息。

**修改建议：** 方法抛出的异常应该是含有明确异常产生原因的具体异常类型（非基类异常）。

✅ **正确示例：**

  ```java
    public class Employee {
        ...
        public String getSomeInfo() throws MyBizException{
            ...
            throw new MyBizException("xxx");
        }
        ...
    }
  ```

❌ **错误示例：**

- 错误示例：直接抛出RuntimeException

  ```java
    public class Employee {
        ...
        public String getSomeInfo() {
            ...
            throw new RuntimeException("xxx");
        }
        ...
    }
  ```

---

## `G.ERR.08 不要使用return、break、continue或抛出异常使finally块非正常结束` 🟠 🔴[安全] `common_standard_rule`

在finally代码块中，直接使用return、break、continue、throw语句，或由于调用方法的异常未处理，会导致finally代码块无法正常结束。非正常结束的finally代码块会影响try或catch代码块中异常的抛出，也可能会影响方法的返回值。所以要保证finally代码块正常结束。

**修改建议：** 移除在finally块中的return、break、continue、throw语句。

✅ **正确示例：**

  ```java
  public int doSomething() {    
      int result = 0;
      ... 
      try {
          result = doSomethingElse();
          ... 
          return result;
      } catch (IOException ex) {
          ...
      }
      return -1;   
  }
  ```

❌ **错误示例：**

- 错误示例：finally代码块中含有return语句

  ```java
  public int doSomething() {    
      int result = 0;
      ... 
      try {
          result = doSomethingElse();
          ... 
          return result;
      } catch (IOException ex) {
          ...
      } finally {
          return -1;
      }   
  }
  ```

---
