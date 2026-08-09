# G.LOG Logging 日志

共 4 条规则。

## `G.LOG.03 日志必须分等级` 🟡 `common_standard_rule`

如果日志不分等级，则定位问题时，无法快速有效屏蔽大量低级别信息，给快速定位带来难度。

日志可分为以下级别：trace（有的也叫verbose）、debug、info、warning、error、fatal。

**修改建议：** 推荐与具体实现有关的日志记录trace或debug级，一般的业务处理日志用info级，不影响业务进行的错误用warning级，例如用户输入参数错误。而error或fatal级，只记录系统逻辑出错、异常或者重要的错误信息，常常向运维系统报警。

建议生产环境不输出trace或debug日志；有选择地输出info日志；输出warning、error、fatal日志。

对info及以下级别的日志，应使用条件形式或占位符的方式进行输出。

✅ **正确示例：**

##### 场景1： 对于info及以下级别的日志，应该避免直接进行日志内容拼接
- 修复示例：对info及以下级别的日志，使用条件形式或占位符的方式进行日志内容的构造

  ```java
  // 如果日志库提供了带"msgSupplier"的API，如下这样调用可以消除不必要的消息创建
  LOGGER.debug(() ->
      "Processing trade with id: " + id + " and symbol: " + symbol.fetchBigMessage());

  // 采用条件方式
  if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("Processing trade with id: " + id + " and symbol: " + symbol);
  }

  // 或者使用占位符
  LOGGER.debug("Processing trade with id: {} and symbol: {}" , id, symbol);
  ```

❌ **错误示例：**

##### 场景1： 对于info及以下级别的日志，应该避免直接进行日志内容拼接
- 错误示例：对info及以下级别的日志，直接进行字符串拼接

  ```java
  LOGGER.debug("Processing trade with id: " + id + " and symbol: " + symbol);
  ```

---

## `G.LOG.02 日志工具Logger类的实例必须声明为private static final或者private final` 🟡 `common_standard_rule`

对于工具类的实例，如果声明时并进行了初始化，应该声明为private static final，如果只是声明但未初始化，则应声明为private final。

- 声明为private是出于访问封装的考虑，防止Logger类的实例对象被其他类非法使用；
- 声明为static是为了防止重复new出Logger类的实例，造成资源的浪费，同时防止实例被序列化，造成安全风险（精心设计的library除外）；
- 声明为final是因为在类的生命周期内无需变更Logger类的实例。

**修改建议：** 日志工具实例，应该声明为`private static final`或`private final`。

✅ **正确示例：**

```java
private static final Logger logger = LoggerFactory.getLogger(CheckUpdateToAutoRenewStep.class);
```

❌ **错误示例：**

- 错误示例：没有使用final修饰日志实例
```java
private static Logger logger = LoggerFactory.getLogger(CheckUpdateToAutoRenewStep.class);
```

---

## `G.LOG.01 记录日志应该使用Facade模式的日志框架` 🟡 `common_standard_rule`

专用日志工具与控制台打印（System.out、System.err）相比，提供了更丰富的日志记录功能，且使用更加简单。日志打印推荐使用Facade模式的日志框架，如第三方slf4j、产品自研日志框架等，不要使用System.out与System.err进行控制台打印。

**修改建议：** 使用日志打印。

✅ **正确示例：**

采用日志工具输出日志，例如slf4j+logback。
```java
start = System.currentTimeMillis();
// 其他加载数据的代码
LOGGER.info("items loaded, use {}ms.", (System.currentTimeMillis() - start));
```

❌ **错误示例：**

```java
start = System.currentTimeMillis();
// 其他加载数据的代码
System.out.println ("items loaded, use " + (System.currentTimeMillis() - start) + "ms.");
```

---

## `G.LOG.04 非仅限于中文区销售产品禁止用中文打印日志` 🟡 🔴[安全] `common_standard_rule`

建议日志内容统一只用英文字符，避免使用中文字符，尤其是注意中文标点符号。统一日志的字符集，有利于对日志的自动化分析处理。

**修改建议：** 将日志中的中文字符、标点等替换为英文。

✅ **正确示例：**

##### 场景1： 出现用中文打印日志
- 修复示例1：用英文打印日志

  ```java
  String body = getRequestBodyData(request);
  try {
      handeData(body);
  }catch(MyBizException ex) {
      LOG.error("handle request data failed!");
  }
  ```

❌ **错误示例：**

##### 场景1： 出现用中文打印日志
  ```java
  String body = getRequestBodyData(request);
  try {
      handeData(body);
  }catch(MyBizException ex) {
      LOG.error("在处理请求数据时出现了异常！");
  }
  ```

---
