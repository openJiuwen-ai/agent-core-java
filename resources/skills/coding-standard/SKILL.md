---
name: coding-standard
description: 华为 CodeArts Check Java 编程规范。在用户编写或修改 Java 代码、做 Code Review、要求检查代码规范时主动应用。覆盖命名、异常、日志、并发、格式化、安全等 102 条规则。涉及关键词：编程规范、代码规范、coding standard、code convention、规范检查、代码风格、Code Review、华为规则、CodeArts Check、G.FMT、G.NAM、G.ERR。不适用于：只问 Java 语法概念、非 Java 代码、调试运行错误、配置部署问题。
---

# 华为 CodeArts Check Java 编程规范

本 skill 收录华为云 CodeArts Check 服务的 Java 规则集，规则编号格式为 `G.<前缀>.<序号>`，前缀对应主题分类。共 102 条规则，按 18 个主题分组，每条规则附正反例代码。

## 规则编号前缀索引

| 前缀 | 主题 | 条数 |
|---|---|---|
| `G.CMT` | 注释规范 | 7 |
| `G.COL` | 集合泛型 | 3 |
| `G.CON` | 并发规范 | 6 |
| `G.CTL` | 控制流 | 4 |
| `G.DCL` | 声明规范 | 4 |
| `G.ERR` | 异常处理 | 11 |
| `G.EXP` | 表达式 | 5 |
| `G.FIO` | 文件 IO | 1 |
| `G.FMT` | 格式化 | 13 |
| `G.LOG` | 日志规范 | 4 |
| `G.MET` | 方法规范 | 6 |
| `G.NAM` | 命名规范 | 8 |
| `G.OBJ` | 类与对象 | 7 |
| `G.OTH` | 其他 | 2 |
| `G.PRM` | 性能规范 | 8 |
| `G.SEC` | 安全规范 | 1 |
| `G.SER` | 序列化 | 3 |
| `G.TYP` | 类型规范 | 9 |
| **合计** | | **102** |

## 引擎与类别说明

| 引擎 | 类别 | 说明 |
|---|---|---|
| `fixbotengine-java` | 质量 | 华为自研 Java 检查引擎，覆盖风格/规范/性能（91 条） |
| `codemars` | 安全 | 华为安全缺陷检查引擎（7 条） |
| `secbrella` | 安全 | 华为通用安全检查引擎（4 条） |

> **类别推断**：`codemars` / `secbrella` 引擎的规则归为"安全"类，其余归为"质量"类。安全类规则踩坑代价更大，Code Review 时优先关注。

## 场景速查表

根据当前任务场景，直接定位相关规则编号，避免逐条扫描：

| 场景 | 相关规则 |
|---|---|
| 写 Service / 业务类 | `G.OBJ.01/02/03/06/08、G.NAM.03/04、G.CMT.01/03` |
| 写异常处理 (try/catch/finally) | `G.ERR.01/02/03/04/06/08、G.CTL.03` |
| 写多线程代码 | `G.CON.05/06/07/08/10/12、G.TYP.03` |
| 写日志 | `G.LOG.01/02/03/04` |
| 写集合 / 泛型 | `G.COL.02/03/04、G.PRM.01` |
| 写 IO 操作 (文件/流) | `G.PRM.07、G.FIO.01、G.TYP.09` |
| 写方法签名 | `G.MET.01/03/04/05/06/07、G.NAM.04、G.ERR.07` |
| 写常量 / 枚举 | `G.NAM.05、G.DCL.04/05、G.FMT.15` |
| 覆写 equals | `G.OBJ.06、G.EXP.04` |
| 写序列化类 | `G.SER.02/04/05` |
| 写测试 | `扩展规则：测试规范` |
| 写 SQL / 数据库 | `扩展规则：SQL 与数据库` |
| 排查 OOM/CPU/GC | `用 jvm-troubleshoot skill` |
| Code Review | `先看"快速自查清单"12 条，再按场景查` |

## 快速自查清单（日常编码最易违反的 12 条）

做 Code Review 或写新代码时，优先检查这 12 条。每条规则详情见"规则详情"小节。

- `G.NAM.03` 类、枚举、接口名用大驼峰
- `G.NAM.04` 方法名用小驼峰
- `G.NAM.05` 常量名全大写，单词间下划线
- `G.ERR.01` 不要用空 catch 块忽略异常
- `G.ERR.02` 不要直接捕获 Throwable/Exception/RuntimeException
- `G.ERR.06` catch 后抛新异常时，把原异常作为 cause 传入
- `G.FMT.07` 避免空块，必须用空块时用统一的大括号换行风格
- `G.LOG.01` 日志用 Facade 模式（SLF4J），不直接用 Log4j/Logback
- `G.LOG.02` Logger 实例声明为 private static final
- `G.OBJ.06` 覆写 equals 必须同时覆写 hashCode
- `G.PRM.05` 禁止创建不必要的对象
- `G.CON.07` 创建新线程必须指定线程名

## 使用方式

1. **场景定位**：先用"场景速查表"按当前任务找到相关规则编号，直接跳到对应小节。
2. **关键词检索**：如果场景表没覆盖，用 `Grep` 搜本文件关键词（如 "空块"、"日志"、"线程"、"序列化"）找到规则。
3. **应用规则**：读取规则简述和正反例后，按规则修改或审查代码，在回复中引用规则编号（如 `G.NAM.01`）。
4. **快速自查**：Code Review 时优先检查"快速自查清单"12 条，覆盖 80% 常见问题。
5. **不确定不要编造**：规则原文以本文件为准，不要凭记忆推断规则细节。

## 规则详情

每条规则附正例（OK）与反例（BAD），按前缀分组。

### G.CMT 注释规范

#### `G.CMT.01` public 或 protected 修饰的元素应添加 Javadoc 注释

public/protected 元素必须加 Javadoc

#### `G.CMT.02` 顶层 public 类的 Javadoc 应该包含功能说明和创建日期/版本信息

顶层 public 类的 Javadoc 含功能说明 + 创建日期/版本

#### `G.CMT.03` 方法的 Javadoc 中应该包含功能说明，根据实际需要按顺序使用 @param、 @return、@throws 标签对参数、返回值、异常进行注释

方法 Javadoc 含功能说明，按顺序用 @param/@return/@throws

#### `G.CMT.04` 不写空有格式的方法头注释

不写空有格式的方法头注释

#### `G.CMT.05` 文件头注释应该包含版权许可信息

文件头注释含版权许可信息

#### `G.CMT.06` 注释与代码之间应该有空行或空格，注释符与注释内容之间应该有空格

注释与代码之间有空行或空格，注释符与内容之间有空格

#### `G.CMT.07` 正式交付给客户的代码不应包含 TODO/FIXME 注释

正式交付代码不应包含 TODO/FIXME

### G.COL 集合泛型

#### `G.COL.02` 优先使用泛型集合，而不是数组

优先用泛型集合，而不是数组

#### `G.COL.03` 声明一个泛型类通过限定符限制可用的泛型类型

泛型类用限定符限制可用类型

#### `G.COL.04` 不要在 foreach 循环中通过 remove()/ add()方法更改集合

foreach 循环中不要用 remove/add 改集合

### G.CON 并发规范

#### [安全] `G.CON.05` 禁止使用非线程安全的方法来覆写线程安全的方法

禁止用非线程安全方法覆写线程安全方法

```java
// OK 正例
// 父类 synchronized 方法，子类也用 synchronized
@Override public synchronized void put(String k, String v) { ... }
// BAD 反例
// 父类 synchronized，子类不加 synchronized
@Override public void put(String k, String v) { ... }  // 破坏线程安全
```

#### `G.CON.06` 使用新并发工具代替 wait()和 notify()

用新并发工具代替 wait/notify

#### `G.CON.07` 创建新线程时必须指定线程名

创建新线程必须指定线程名

#### `G.CON.08` 使用 Thread 对象的 setUncaughtExceptionHandler 方法注册未捕获异常处理者

用 setUncaughtExceptionHandler 注册未捕获异常处理器

#### `G.CON.10` 线程中断由业务代码来协作完成，慎用 Thread.interrupt 方法

线程中断由业务代码协作完成，慎用 Thread.interrupt

#### `G.CON.12` 避免不加控制地创建新线程，应该使用线程池来管控资源

避免不加控制创建新线程，应用线程池

### G.CTL 控制流

#### `G.CTL.01` 不要在控制性条件表达式中执行赋值操作或执行复杂的条件判断

控制性条件表达式中不要执行赋值或复杂判断

#### `G.CTL.02` 含 else if 分支的条件判断应在最后加一个 else 分支

含 else if 分支的条件判断应在最后加 else

#### `G.CTL.03` switch 语句要有 default 分支

switch 语句要有 default 分支

#### `G.CTL.05` 避免在循环体中修改循环控制变量

避免在循环体中修改循环控制变量

### G.DCL 声明规范

#### `G.DCL.01` 每行声明一个变量

每行声明一个变量

#### `G.DCL.03` 禁止 C 风格的数组声明

禁止 C 风格的数组声明（方括号跟在变量名后）

#### `G.DCL.04` 避免枚举常量序号的产生依赖于 ordinal()方法

避免枚举常量序号依赖 ordinal()

#### `G.DCL.05` 禁止将 mutable 对象定义为常量

禁止将 mutable 对象定义为常量

### G.ERR 异常处理

#### `G.ERR.01` 不要通过一个空的 catch 块忽略异常

不要用空 catch 块忽略异常

```java
// OK 正例
try { ... } catch (IOException e) { log.error("读文件失败", e); throw new RuntimeException(e); }
// BAD 反例
try { ... } catch (IOException e) {}  // 异常被吞掉
```

#### `G.ERR.02` 不要直接捕获异常的基类 Throwable、Exception、RuntimeException

不要直接捕获 Throwable/Exception/RuntimeException

```java
// OK 正例
catch (FileNotFoundException e) { ... }
// BAD 反例
catch (Exception e) { ... }  // 过于宽泛
```

#### `G.ERR.03` 不要直接捕获可通过预检查进行处理的 RuntimeException，如 NullPointerException、 IndexOutOfBoundsException 等

不要直接捕获可通过预检查的 RuntimeException

#### [安全] `G.ERR.04` 防止通过异常泄露敏感信息

防止通过异常泄露敏感信息

```java
// OK 正例
catch (AuthException e) { log.error("认证失败", e); throw new BizException("认证失败"); }
// BAD 反例
catch (AuthException e) { throw new RuntimeException("密码错误: " + password); }  // 泄露密码
```

#### `G.ERR.05` 方法抛出的异常，应该与本身的抽象层次相对应

方法抛出的异常应与本身抽象层次对应

#### [安全] `G.ERR.06` 在 catch 块中抛出新异常时，避免丢失原始异常信息

catch 后抛新异常时，把原异常作为 cause 传入

```java
// OK 正例
catch (SQLException e) { throw new ServiceException("查询失败", e); }
// BAD 反例
catch (SQLException e) { throw new ServiceException("查询失败"); }  // 丢失根因
```

#### `G.ERR.07` 一个方法不应抛出超过 5 个异常，并在 Javadoc 的 @throws 标签中记录每个抛出的异常及其条件

一个方法不应抛出超过 5 个异常，并在 @throws 记录

#### [安全] `G.ERR.08` 不要使用 return、break、continue 或抛出异常使 finally 块非正常结束；在 finally 块中使用 throw 语句

不要用 return/break/continue/throw 使 finally 非正常结束

```java
// OK 正例
try { return compute(); }
finally { cleanup(); }  // finally 只做清理
// BAD 反例
try { return compute(); }
finally { return -1; }  // finally 用 return，覆盖返回值
```

#### [安全] `G.ERR.09` 不要调用 System.exit()终止 JVM

不要调用 System.exit() 终止 JVM

```java
// OK 正例
throw new StartupException("无法启动，请检查配置");  // 让上层处理
// BAD 反例
System.exit(1);  // 强制终止，影响调用方
```

#### `G.ERR.10` 尽量消除非受检的异常，不应该在整个类上使用 SuppressWarning

尽量消除非受检异常，不应整个类上用 SuppressWarning

#### `G.ERR.11` 对于 GeneralSecurityException 及其子类异常应记录日志

GeneralSecurityException 及子类异常应记录日志

### G.EXP 表达式

#### `G.EXP.01` 不要在单个表达式中对相同的变量赋值超过一次

不要在单个表达式中对相同变量赋值超过一次

#### `G.EXP.02` 用括号明确表达式的操作顺序，避免过分依赖默认优先级

用括号明确表达式操作顺序，不依赖默认优先级

#### `G.EXP.03` 条件表达式?:的第 2 和第 3 个操作数应使用相同的类型

条件表达式 ?: 第 2 和第 3 个操作数应同类型

#### `G.EXP.04` 表达式的比较，应该遵循左侧倾向于变化、右侧倾向于不变的原则；使用 equals 方法进行字符串比较

表达式比较：左侧倾向变化、右侧倾向不变；字符串用 equals

#### [安全] `G.EXP.06` 代码中不应使用断言（assert）

代码中不应使用断言 (assert)

```java
// OK 正例
if (input == null) throw new IllegalArgumentException("input null");
// BAD 反例
assert input != null;  // 生产环境默认禁用，不起作用
```


### G.FIO 文件 IO

#### [安全] `G.FIO.01` 使用外部数据构造的文件路径前必须进行校验，校验前必须对文件路径进行规范化处理

用外部数据构造的文件路径前必须校验并规范化

```java
// OK 正例
Path base = Paths.get("/safe/base").normalize().toAbsolutePath();
Path resolved = base.resolve(userInput).normalize();
if (!resolved.startsWith(base)) throw new SecurityException("路径越界");
// BAD 反例
File f = new File(userInput);  // 未校验，可能路径穿越
```


### G.FMT 格式化

#### `G.FMT.05` 在条件语句和循环块中应该使用大括号

条件语句和循环块必须用大括号

#### `G.FMT.06` 对于非空块状结构，左大括号应该放在行尾，右大括号应该另起一行

非空块：左大括号放行尾，右大括号另起一行

#### `G.FMT.07` 应该避免空块，必须使用空块时，应采用统一的大括号换行风格

避免空块，必须用空块时用统一的大括号换行风格

```java
// OK 正例
// 写明注释解释为什么空
if (cond) {
    // 故意留空，见 ISSUE-123
}
// BAD 反例
if (cond) {}  // 无注释的空块
```

#### `G.FMT.09` 每行不超过一个语句

每行不超过一个语句

#### `G.FMT.12` 减少不必要的空行，保持代码紧凑

减少不必要的空行，保持代码紧凑

#### `G.FMT.13` 用空格突出关键字和重要信息

用空格突出关键字和重要信息

#### `G.FMT.14` 不应插入多余空格使代码垂直对齐

不应插入多余空格使代码垂直对齐

#### `G.FMT.15` 枚举常量间用逗号隔开，换行可选

枚举常量间用逗号隔开，换行可选

#### `G.FMT.16` case 语句块结束时如果不加 break，需要有注释说明（fall-through）

case 块结束不加 break 时要有注释说明 fall-through

#### `G.FMT.17` 应用于类、方法、类属性的每个注解独占一行

类、方法、类属性的每个注解独占一行

#### `G.FMT.18` 块注释的缩进级别应与上下文代码相同

块注释的缩进级别与上下文代码相同

#### `G.FMT.19` 类和成员修饰符（如果存在）按 Java 语言规范建议的顺序显示

类和成员修饰符按 Java 规范建议的顺序

#### `G.FMT.20` 数字字面量应该设置合适的后缀， long 类型应该使用 L 作为后缀

数字字面量应有合适后缀，long 用 L

### G.LOG 日志规范

#### `G.LOG.01` 记录日志应该使用 Facade 模式的日志框架

日志用 Facade 模式（SLF4J），不直接用 Log4j/Logback

#### `G.LOG.02` 日志工具 Logger 类的实例必须声明为 private static final 或者 private final

Logger 实例声明为 private static final

#### `G.LOG.03` 日志必须分等级

日志必须分等级（DEBUG/INFO/WARN/ERROR）

#### `G.LOG.04` 非仅限于中文区销售产品禁止用中文打印日志

非仅中文区销售产品禁止用中文打印日志

### G.MET 方法规范

#### `G.MET.01` 方法要简短；方法的参数不应超过 5 个

方法要简短，参数不应超过 5 个

#### `G.MET.03` 不应把方法的参数当做临时变量

不应把方法参数当做临时变量

#### `G.MET.04` 谨慎使用可变数量参数

谨慎使用可变数量参数

#### `G.MET.05` 对于返回数组或者容器的方法，应返回长度为 0 的数组或者容器，代替返回 null

返回数组或容器的方法应返回空集合，不返回 null

#### `G.MET.06` 使用 Optional 代替 null 作为返回值或者可能的缺失值；禁止对 Optional 对象赋值为 null

用 Optional 代替 null 作为可能缺失的返回值；禁止对 Optional 赋值为 null

#### [安全] `G.MET.07` 不要忽略方法的返回值

不要忽略方法的返回值

### G.NAM 命名规范

#### `G.NAM.01` 标识符应由不超过 64 字符的字母、数字和下划线组成

标识符不超过 64 字符，由字母、数字、下划线组成

#### `G.NAM.02` 包名中的字母应小写，包名以点号分隔层级

包名字母小写，点号分隔层级

#### `G.NAM.03` 类、枚举和接口名应采用大驼峰命名

类、枚举、接口名用大驼峰

```java
// OK 正例
public class OrderService {}
// BAD 反例
public class order_service {}  // 下划线风格
```

#### `G.NAM.04` 方法名应采用小驼峰命名

方法名用小驼峰

```java
// OK 正例
public String getUserName() {}
// BAD 反例
public String get_user_name() {}
```

#### `G.NAM.05` 常量名采用全大写单词，单词间以下划线分隔

常量名全大写，单词间下划线

```java
// OK 正例
static final int MAX_RETRY = 3;
// BAD 反例
static final int maxRetry = 3;
```

#### `G.NAM.06` 变量采用小驼峰命名

变量用小驼峰

#### `G.NAM.07` 避免使用具有否定含义布尔变量名

避免使用具有否定含义的布尔变量名

#### `G.NAM.08` 布尔型变量建议以表达是非意义的动词开头

布尔型变量建议以表达是非意义的动词开头

### G.OBJ 类与对象

#### `G.OBJ.01` 应避免定义 public 且非 final 的类属性

避免定义 public 且非 final 的类属性

#### `G.OBJ.02` 不要在父类的构造方法中调用可能被子类覆写的方法

不要在父类构造方法中调用可能被子类覆写的方法

#### `G.OBJ.03` 构造方法如果有多个，尽量重用

构造方法有多个时尽量重用

#### `G.OBJ.04` 避免在无关的变量或无关的概念之间重用名字，避免隐藏（hide）、遮蔽（shadow）和遮掩（obscure）

避免在无关变量或概念间重用名字，避免隐藏/遮蔽/遮掩

#### `G.OBJ.06` 覆写 equals 方法时，要同时覆写 hashCode 方法

覆写 equals 必须同时覆写 hashCode

```java
// OK 正例
@Override public boolean equals(Object o) { ... }
@Override public int hashCode() { ... }
// BAD 反例
@Override public boolean equals(Object o) { ... }  // 无 hashCode，HashMap 会出错
```

#### `G.OBJ.08` 正确实现单例模式

正确实现单例模式

#### `G.OBJ.10` 接口定义中去掉多余的修饰词

接口定义中去掉多余的修饰词

### G.OTH 其他

#### `G.OTH.03` 不用的代码段包括 import，直接删除，不要注释掉；不用的 import 语句，直接删除，不要注释掉

不用的代码段（含 import）直接删除，不要注释掉

#### [安全] `G.OTH.05` 删除无效或永不执行的代码

删除无效或永不执行的代码

```java
// OK 正例
// 只保留会执行的代码
// BAD 反例
if (false) { deadCode(); }  // 永不执行
return; doCleanup();  // return 后的死代码
```


### G.PRM 性能规范

#### `G.PRM.01` 将集合转为数组时使用 Collection<T>.toArray(T[])方法；Java 11 后使用 Collection<T>.toArray(IntFunction<T[]>)

集合转数组用 Collection.toArray(T[])，Java 11 后用 toArray(IntFunction)

#### `G.PRM.02` 使用 System.arraycopy()或 Arrays.copyOf()进行数组复制

数组复制用 System.arraycopy 或 Arrays.copyOf

#### `G.PRM.04` 不要对正则表达式进行频繁重复预编译

不要对正则表达式频繁重复预编译

#### `G.PRM.05` 禁止创建不必要的对象

禁止创建不必要的对象

```java
// OK 正例
Boolean enabled = Boolean.TRUE;  // 或直接 boolean enabled = true;
String s = "literal";
// BAD 反例
Boolean enabled = new Boolean(true);  // 每次新对象
String s = new String("literal");
```

#### `G.PRM.07` 进行 IO 类操作时，必须在 try-with- resource 或 finally 里关闭资源

IO 类操作必须在 try-with-resources 或 finally 里关闭资源

#### `G.PRM.08` 禁止使用主动 GC（除非在密码、 RMI 等方面），尤其是在频繁/周期性的逻辑中

禁止主动 GC（除非密码/RMI 等），尤其频繁/周期性逻辑中

#### `G.PRM.09` 禁止使用 Finalizer 机制

禁止使用 Finalizer 机制

#### `G.PRM.10` 不要创建临时变量作为 return 语句的返回值

不要创建临时变量作为 return 语句的返回值

### G.SEC 安全规范

#### [安全] `G.SEC.04` 使用安全管理器来保护敏感操作

使用安全管理器保护敏感操作

### G.SER 序列化

#### `G.SER.02` 实现 Serializable 接口的可序列化类应该显式声明 serialVersionUID

实现 Serializable 的类应显式声明 serialVersionUID

#### `G.SER.04` 禁止直接序列化指向系统资源的信息

禁止直接序列化指向系统资源的信息

#### [安全] `G.SER.05` 禁止序列化非静态的内部类

禁止序列化非静态的内部类

### G.TYP 类型规范

#### `G.TYP.03` 禁止使用浮点数作为循环计数器

禁止用浮点数作为循环计数器

#### `G.TYP.05` 浮点型数据判断相等不要直接使用 ==，浮点型包装类型不要用 equals()或者 flt.compareTo(another)==0 作相等的比较

浮点数判等不要直接用 ==，包装类型不用 equals

#### `G.TYP.06` 禁止尝试与 NaN 进行比较运算，相等操作使用 Double 或 Float 的 isNaN()方法

禁止与 NaN 比较，用 isNaN()

#### `G.TYP.07` 不要在代码中硬编码用于表示换行、文件路径分隔的字符；不要在代码中硬编码用于表示文件路径分隔的字符

不要硬编码换行、文件路径分隔字符，用常量

#### `G.TYP.08` 字符串大小写转换、数字格式化为西方数字时，必须加上 Locale.ROOT 或 Locale.ENGLISH

字符串大小写转换、数字格式化加 Locale.ROOT 或 Locale.ENGLISH

#### `G.TYP.09` 字符与字节的互相转换操作，要指明正确的编码方式

字符与字节转换要指明编码

#### `G.TYP.11` 基本类型优于包装类型，注意合理使用包装类型

基本类型优于包装类型，合理使用包装类型

#### `G.TYP.12` 明确地进行类型转换，避免依赖隐式类型转换

明确进行类型转换，避免隐式转换

#### `G.TYP.13` 在引用类型向下转换前用 instanceof 进行判断

引用类型向下转换前用 instanceof 判断

## 扩展规则（华为 102 条未覆盖的高频问题）

以下是华为 CodeArts Check 规则集未覆盖、但 Java CR 中高频出现的问题。编号用 `X.` 前缀，与 `G.*` 区分。带正反例的规则单独列出，其余在表格中。

### 测试规范

| 规则编号 | 规则简述 |
|---|---|
| `X.TST.01` | 测试方法间不能有共享状态（`static` 字段），每个测试应独立 |
| `X.TST.02` | 测试必须有有效断言，不能只 `assertNotNull`；断言要覆盖核心业务逻辑 |
| `X.TST.03` | 避免 Mock 静态方法（PowerMock），优先重构代码使依赖可注入 |
| `X.TST.04` | 集成测试（依赖 DB/HTTP）必须标注 `@Tag("integration")` 或类似标记，与单元测试分离 |
| `X.TST.05` | 测试类名与被测类对应：`UserService` → `UserServiceTest`，方法名 `methodName_场景_期望` |
| `X.TST.06` | 测试三段式：Given（准备）→ When（执行）→ Then（断言），用空行分隔 |

### SQL 与数据库

| 规则编号 | 规则简述 |
|---|---|
| `X.SQL.02` | 查询大表必须分页（`LIMIT/OFFSET` 或 `WHERE id > lastId` 游标分页） |
| `X.SQL.03` | `WHERE` 条件字段必须有索引，避免全表扫描；联合索引遵循最左前缀 |
| `X.SQL.04` | 禁止 `SELECT *`，明确列名，减少 IO 和避免列变更时映射错乱 |
| `X.SQL.05` | 批量操作用 batch，不要循环单条 insert（`addBatch()` 或 MyBatis `<foreach>`） |
| `X.SQL.06` | 事务范围最小化，不在事务里做 RPC 调用或耗时计算 |
| `X.SQL.07` | 避免大事务，长事务会占锁和连接池；用 `@Transactional(timeout=...)` 设上限 |

#### `X.SQL.01` 禁止 SQL 拼接，必须用 PreparedStatement 或参数化查询

```java
// OK 正例
// PreparedStatement 参数化
String sql = "SELECT id, name FROM user WHERE name = ? AND status = ?";
try (PreparedStatement ps = conn.prepareStatement(sql)) {
    ps.setString(1, name);
    ps.setInt(2, status);
    try (ResultSet rs = ps.executeQuery()) { ... }
}
// MyBatis：用 #{} 而非 ${}
// <select id="findByName">SELECT ... WHERE name = #{name}</select>
// BAD 反例
// 字符串拼接，SQL 注入风险
String sql = "SELECT id, name FROM user WHERE name = '" + name + "' AND status = " + status;
Statement st = conn.createStatement();
ResultSet rs = st.executeQuery(sql);  // name 含 ' OR '1'='1 即可注入
// MyBatis：${} 是字符串拼接，同样危险
// <select id="findByName">SELECT ... WHERE name = '${name}'</select>
```

| `X.SQL.02` | 查询大表必须分页（`LIMIT/OFFSET` 或 `WHERE id > lastId` 游标分页） |

### 并发细节（华为 G.CON 未覆盖）

| 规则编号 | 规则简述 |
|---|---|
| `X.CON.03` | `synchronized` 不能锁 String 字面量、Integer 缓存对象、Boolean，应锁 `new Object()` 或专用锁对象 |
| `X.CON.05` | `HashMap` 多线程并发写可能死循环（JDK7 链表成环）或丢数据，用 `ConcurrentHashMap` |

#### `X.CON.01` SimpleDateFormat 非线程安全，多线程下用 DateTimeFormatter 或 ThreadLocal

```java
// OK 正例
// 1：DateTimeFormatter（JDK8+，线程安全）
private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
String s = LocalDate.now().format(FMT);

// OK 正例 2：ThreadLocal 包装（兼容旧代码）
private static final ThreadLocal<SimpleDateFormat> TL =
    ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd"));
String s = TL.get().format(new Date());
// BAD 反例
// static SimpleDateFormat 多线程共享
private static final SimpleDateFormat FMT = new SimpleDateFormat("yyyy-MM-dd");
// 多线程调 FMT.format() 会互相覆盖 Calendar 内部状态，结果错乱
// 现象：偶尔解析出 1970 年或抛 NumberFormatException
```

#### `X.CON.02` 双重检查锁必须加 volatile，防止指令重排

```java
// OK 正例
// instance 加 volatile
public class Singleton {
    private static volatile Singleton instance;
    public static Singleton getInstance() {
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
// BAD 反例
// 漏 volatile
public class Singleton {
    private static Singleton instance;  // 缺 volatile
    public static Singleton getInstance() {
        if (instance == null) {           // 线程 B 可能读到半初始化对象
            synchronized (Singleton.class) {
                if (instance == null) {
                    instance = new Singleton();  // 指令重排：先赋值再初始化字段
                }
            }
        }
        return instance;  // 其他线程拿到字段还是 null 的"已初始化"对象
    }
}
```

#### `X.CON.04` ConcurrentHashMap 用 computeIfAbsent 而非 get+put，保证原子性

```java
// OK 正例
// computeIfAbsent 原子操作
ConcurrentMap<String, List<String>> map = new ConcurrentHashMap<>();
map.computeIfAbsent(key, k -> new ArrayList<>()).add(value);  // 一次完成

// OK 正例：putIfAbsent（替代 get+put）
List<String> list = new ArrayList<>();
List<String> old = map.putIfAbsent(key, list);
if (old != null) { list = old; }
list.add(value);
// BAD 反例
// get + put 非原子，多线程下会丢数据
ConcurrentMap<String, List<String>> map = new ConcurrentHashMap<>();
List<String> list = map.get(key);      // 线程 A、B 同时 get 到 null
if (list == null) {
    list = new ArrayList<>();
    map.put(key, list);                 // 线程 A、B 各 put 一个，A 的被覆盖
}
list.add(value);  // 线程 A 加到自己的 list，但 map 里是 B 的
```

#### `X.CON.06` 线程池必须用 ThreadPoolExecutor 显式创建，禁用 Executors 快捷方法

```java
// OK 正例
// 显式 ThreadPoolExecutor，参数可控
ExecutorService pool = new ThreadPoolExecutor(
    8,                              // corePoolSize
    16,                             // maxPoolSize
    60L, TimeUnit.SECONDS,          // keepAliveTime
    new LinkedBlockingQueue<>(1000),// 有界队列，防 OOM
    new ThreadFactoryBuilder().setNameFormat("order-pool-%d").build(),  // 命名
    new ThreadPoolExecutor.CallerRunsPolicy()  // 拒绝策略：调用方执行
);
// BAD 反例
// Executors 快捷方法，隐患大
ExecutorService pool1 = Executors.newCachedThreadPool();   // 无界线程数，可能创建上万线程
ExecutorService pool2 = Executors.newFixedThreadPool(8);   // 无界队列，任务堆积 OOM
ExecutorService pool3 = Executors.newSingleThreadExecutor(); // 同 newFixedThreadPool(1)，无界队列

// 问题：队列分别是 SynchronousQueue（无界）/ LinkedBlockingQueue（无界），任务堆积就 OOM
```

### 集合细节（华为 G.COL 未覆盖）

| 规则编号 | 规则简述 |
|---|---|
| `X.COL.01` | `Arrays.asList()` 返回固定大小列表，`add/remove` 抛 `UnsupportedOperationException`；要可变用 `new ArrayList<>(Arrays.asList(...))` |
| `X.COL.02` | `subList()` 返回视图，修改影响原列表；要独立副本用 `new ArrayList<>(list.subList(...))` |
| `X.COL.03` | 集合判空用 `isEmpty()` 而非 `size() == 0`，语义更清晰 |
| `X.COL.04` | `LinkedList` 当随机访问列表用 `get(i)` 是 O(n)，随机访问应用 `ArrayList` |

### 性能细节（华为 G.PRM 未覆盖）

| 规则编号 | 规则简述 |
|---|---|
| `X.PRM.01` | 循环里不要调 DB 或 RPC，应批处理或预加载 |
| `X.PRM.02` | 字符串循环拼接用 `StringBuilder`，不要 `+=`（每次创建新对象） |
| `X.PRM.03` | `Stream` 不要多次遍历：`list.stream().count()` 后又 `collect`，应一次完成 |
| `X.PRM.04` | 正则 `String.matches("...")` 每次重编译，应 `Pattern.compile` 静态缓存 |
| `X.PRM.05` | 避免在热点路径创建大对象（如循环里 `new ArrayList<>(hugeCapacity)`） |

### 依赖与构建

| 规则编号 | 规则简述 |
|---|---|
| `X.DEP.01` | 依赖版本统一在父 pom 的 `<dependencyManagement>` 声明，子模块不写 version |
| `X.DEP.02` | 不要引入 `commons-lang3` 等大库只用一个方法，可手写或找轻量替代 |
| `X.DEP.03` | `provided` 范围只用于容器提供（如 Servlet API），运行时需要的不要用 provided |
| `X.DEP.04` | 避免循环依赖：A 依赖 B，B 依赖 A；应重构提取公共模块 |

