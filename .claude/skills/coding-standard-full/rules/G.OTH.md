# G.OTH Other 其他

共 5 条规则。

## `G.OTH.02 必须使用SSLSocket代替Socket来进行安全数据交互` 🟠 🔴[安全] `security_standard_rule`

在 Web 应用程序中使用基于套接字的通信往往容易出错。

当网络通信中涉及明文的敏感信息时，需要使用SSLSocket而不是Socket，Socket是明文通信，攻击者可以通过网络监听获取其中的敏感信息，通过中间人攻击对报文进行恶意篡改。SSLSocket是在Socket的基础上进行了一层安全性保护，包括身份认证、数据加密和完整性保护。

**修改建议：** 当网络通信中涉及明文的敏感信息时，需要使用SSLSocket而不是Socket。

✅ **正确示例：**

- 修复示例：使用加密通道传递敏感信息

```java
// server端
try {
    byte[] ip = new byte[4];
    // ...

    // 在敏感数据传递时，使用SSLSocket代替Socket来进行安全数据交互
    SSLServerSocketFactory sslServerSocketFactory =
        (SSLServerSocketFactory) SSLServerSocketFactory.getDefault();
    SSLServerSocket sslServerSocket =
        (SSLServerSocket) sslServerSocketFactory.createServerSocket(9999, 10, InetAddress.getByAddress(ip));
    SSLSocket sslSocket = (SSLSocket) sslServerSocket.accept();
    // ...sslSocket交由其他线程处理
} catch (IOException ex) {
    // ...处理异常
}

// client端
try {
    // 在敏感数据传递时，使用SSLSocket代替Socket来进行安全数据交互
    SSLSocketFactory sslSocketFactory =
        (SSLSocketFactory) SSLSocketFactory.getDefault();
    SSLSocket sslSocket = (SSLSocket) sslSocketFactory.createSocket(ip, port);
    os = sslSocket.getOutputStream();
    os.write(userInfo.getBytes(StandardCharsets.UTF_8));
    ...
} catch (IOException ex) {
    // ...处理异常
} finally {
    // ...关闭流
}
```

❌ **错误示例：**

- 错误示例：使用明文通道传递敏感信息

```java
// server端
try {
    // 在敏感数据传递时，直接使用了Socket，易导致信息被窃取或受中间人攻击
    ServerSocket serverSocket = new ServerSocket(8080, 10);
    Socket socket = serverSocket.accept();

    // ...socket交由其他线程处理
}catch(IOException ex) {
    // ...处理异常
}

// client端
try {
    // 在敏感数据传递时，直接使用了Socket，易导致信息被窃取或受中间人攻击
    Socket socket = new Socket();
    socket.connect(new InetSocketAddress(ip, port), 10000);
    os = socket.getOutputStream();
    os.write(userInfo.getBytes(StandardCharsets.UTF_8));
    ...
} catch (IOException ex) {
    // ...处理异常
} finally {
    // ...关闭流
}
```

---

## `G.OTH.04 禁止代码中包含公网地址` 🟠 🔴[安全] `security_standard_rule`

硬编码公网IP地址。

代码或脚本中包含用户不可见，不可知的公网地址，可能会引起客户质疑。对产品发布的软件（包含软件包/补丁包）中包含的公网地址（包括公网IP地址、公网URL地址/域名、 邮箱地址）要求如下：

1. 禁止包含用户界面不可见、或产品资料未描述的未公开的公网地址。

2. 已公开的公网地址禁止写在代码或者脚本中，可以存储在配置文件或数据库中。对于开源/第三方软件自带的公网地址必须至少满足上述第1条公开性要求。

**修改建议：** 不要硬编码公网IP地址

✅ **正确示例：**

##### 场景1：代码中存在硬编码IP。
- 修复示例1：可以将IP放入配置文件中：

```java
config.ip = 10.90.0.1 // 公网IP地址调整为在配置文件中配置，代码从配置文件中读取该IP地址并使用。
```

❌ **错误示例：**

##### 场景1：代码中存在硬编码IP。
- 错误示例：代码中硬编码IP。

```java
public void test01Bad() {
    String publicIp = "10.90.0.1"; // IP地址硬编码
}
```

---

## `G.OTH.01 安全场景下必须使用密码学意义上的安全随机数` 🔴 🔴[安全] `security_standard_rule`

安全场景下必须使用密码学意义上的安全随机数。

不安全的随机数可能被部分或全部预测到，导致系统存在安全隐患，安全场景下使用的随机数必须是密码学意义上的安全随机数。密码学意义上的安全随机数分为两类：
1.真随机数产生器产生的随机数；
2.以真随机数产生器产生的少量随机数作为种子的密码学安全的伪随机数产生器产生的大量随机数。

已知的可供产品使用的密码学安全的非物理真随机数产生器有：Linux操作系统的/dev/random设备接口（存在阻塞问题）和Windows操作系统的CryptGenRandom接口。Java中的SecureRandom是一种密码学安全的伪随机数产生器，对于使用非真随机数产生器产生随机数时，要使用少量真随机数作为种子。常见安全场景包括但不限于以下场景：
1.用于密码算法用途，如生成IV、盐值、秘钥等；
2.会话标识（sessionId）的生成；
3.挑战算法中的随机数生成；
4.验证码的随机数生成。

**修改建议：** 使用安全的随机数。

✅ **正确示例：**

明确指定采用sun.security.provider.SecureRandom作为随机数产生器，然后使用generateSeed()方法产生的随机数作为种子，该方法产生的随机数默认为真随机数（如linux下从/dev/random获取）。下述代码实际是使用少量真随机数作为种子（种子长度推荐不少于64bytes），然后采用伪随机数产生器来产生随机数，避免linxu下阻塞问题。对于需要生成大量随机数的场景，需要周期性补充种子，SHA1PRNG算法目前业界没有明确标准，推荐获取2^32次随机数后设置一次种子（调用一次nextBytes()、nextInt()等都计为一次获取随机数操作）。
```java
public byte[] generateSalt() {
    byte[] salt = new byte[8];
    try {
        SecureRandom random = SecureRandom.getInstance("SHA1PRNG", "SUN");
        random.setSeed(random.generateSeed(SEED_LEN));
        random.nextBytes(salt);
    } catch (NoSuchAlgorithmException ex) {
        // 处理异常
    }
    return salt;
}
```

❌ **错误示例：**

Random生成是不安全随机数，不能用做盐值。
```java
public byte[] generateSalt() {
    byte[] salt = new byte[8];
    Random random = new Random();
    random.nextBytes(salt);
    return salt;
}
```

---

## `G.OTH.03 不用的代码段包括import，直接删除，不要注释掉--不用的import语句，直接删除，不要注释掉` 🟡 `common_standard_rule`

不用的import，增加了代码的耦合度，不利于维护。

被注释掉的代码，无法被正常维护；当企图恢复使用这段代码时，极有可能引入易被忽略的缺陷。

正确的做法是，不需要的代码直接删除掉。若再需要时，考虑移植或重写这段代码。

这里说的注释掉代码，包括用 /** *\*/，*/* */和//。

**修改建议：** 删除注释或无用import语句。

✅ **正确示例：**

```java
import java.util.ArrayList;
import java.util.List;     // [GOOD] 所有import都是该类使用的

public class UnusedImportGood {

    private void addList(){
        List list = new ArrayList<String>();
        list.add("A");
    }
}
```

❌ **错误示例：**

```java
import java.util.ArrayList;
import java.util.List;
import java.util.Map; // [BAD]该import语句未使用

public class UnusedImportBad {
    private void addList(){
        List list = new ArrayList<String>();
        list.add("A");
    }
}
```

---

## `G.OTH.03 不用的代码段包括import，直接删除，不要注释掉--不用的代码段，直接删除，不要注释掉` 🟡 `common_standard_rule`

不用的import，增加了代码的耦合度，不利于维护。

被注释掉的代码，无法被正常维护；当企图恢复使用这段代码时，极有可能引入易被忽略的缺陷。

正确的做法是，不需要的代码直接删除掉。若再需要时，考虑移植或重写这段代码。

这里说的注释掉代码，包括用 /** *\*/，*/* */和//。

**修改建议：** 删除注释或无用import语句。

✅ **正确示例：**

```java
public class CodeInCommentTest {
    // [GOOD] CodeInComment
    private int age;
}
```

❌ **错误示例：**

```java
public class CodeInCommentTest {
    // 无用的注释代码
    // [BAD] CodeInComment
    // public void doSomething() {}
    /*private short a;*/
}
```

---
