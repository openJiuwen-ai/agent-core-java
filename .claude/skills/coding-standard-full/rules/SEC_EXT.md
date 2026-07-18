# SEC_EXT Extended Security Rules 扩展安全规则

共 8 条规则。

## `规则-5.8 禁止调用Thread.run()` 🟠 🔴[安全] `security`

禁止直接调用Thread.run()方法启动线程。

调用Thread.start()方法表示启动一个线程，并执行该线程对应的run()方法。但是直接调用Thread.run()方法，run()方法中的语句是由当前线程执行而不是新创建线程来执行。

**修改建议：** 调用start()来取代调用run()。

✅ **正确示例：**

使用start()方法正确启动一个新线程来执行run()方法中的代码。
  ```java
  public final class Foo implements Runnable {
      @Override
      public void run() {
          // ...
      }
      public static void main(String[] args) {
          Foo foo = new Foo();
          new Thread(foo).start();
      }
  }
  ```

❌ **错误示例：**

在当前线程中直接调用run()，所以run()方法中的语句是由当前线程执行而非新创建的线程执行。
  ```java
  public final class Foo implements Runnable {
      @Override
      public void run() {
          // ...
      }
      public static void main(String[] args) {
          Foo foo = new Foo();
          new Thread(foo).run();
      }
  }
  ```

---

## `规则-9.2 禁止使用私有或者弱加密算法--不安全的哈希算法` 🟠 🔴[安全] `security`

禁止使用不安全的哈希算法（比如SHA0，SHA1,MD2,MD4等）。推荐使用的哈希算法有：SHA256。

**修改建议：** 换用安全的hash算法。

❌ **错误示例：**

Signature.getInstance参数使用DSA
```java
public void doSomething() throws Exception {
    Signature.getInstance("DSA");
}
```

---

## `规则-9.3 基于哈希算法的口令安全存储必须加入盐值（salt）--Hash迭代不充分` 🟠 🔴[安全] `security`

Hash迭代不充分导致弱加密问题。

实践中，一个口令可以编码为一个哈希值，且无法从哈希值逆向计算出原始的口令。口令是否相等可以通过 比较它们的哈希值是否相等来判断。如果一个口令的哈希值储存在一个数据库中，由于哈希算法的不可逆性，攻击 者就应该不可能还原出口令。如果说可以恢复口令，那么唯一的方式就是暴力破解攻击，比如计算所有可能口令的 哈希值，或是字典攻击，计算出所有常用的口令的哈希值。如果每个口令都只仅经过简单哈希，相同的口令将得到 相同的哈希值。仅保存口令哈希有以下两个缺陷：1. 由于"生日判定"，攻击者可以快速找到一个口令，尤其是当数据库中的口令数量较大的时候。2. 攻击者可以使用事先计算好的哈希列表在几秒钟之内破解口令。 为了解决这些问题，可以在进行哈希运算之前在口令中引入盐值。一个盐值是一个固定长度的随机数。这个盐值对 于每个存储入口来说必须是不同的。可以明文方式紧邻哈希后的口令一起保存。在这样的配置下，攻击者必须对每 一个口令分别进行暴力破解攻击。这样数据库便能抵御"生日"或者"彩虹表"攻击。

**修改建议：** 使用一个基于密码的密钥派生函数时，迭代计数应至少为 10,000,000。这将大幅增加穷尽式密码搜索的代价，而对派生各个密钥的代价不会产生显著影响。

✅ **正确示例：**

使用一个基于密码的密钥派生函数时，迭代计数至少为 10,000,000
  ```java
  public static byte[] createHash(char[] password) throws NoSuchAlgorithmException, InvalidKeySpecException {
      SecureRandom random = SecureRandom.getInstance("SHA1PRNG");
      byte[] salt = new byte[8];
      random.nextBytes(salt);
      PBEKeySpec spec = new PBEKeySpec(password, salt, 20000000, 256);
      //PBKDF2WithHmacSHA256 is supportted from JDK1.8
      SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
      byte[] hashed = skf.generateSecret(spec).getEncoded();
      return hashed;
  }
  ```

❌ **错误示例：**

使用一个基于密码的密钥派生函数时，迭代计数小于10,000,000。
  ```java
  public static byte[] createHash(char[] password) throws NoSuchAlgorithmException, InvalidKeySpecException {
      SecureRandom random = SecureRandom.getInstance("SHA1PRNG");
      byte[] salt = new byte[8];
      random.nextBytes(salt);
      PBEKeySpec spec = new PBEKeySpec(password, salt, 5000, 256);
      //PBKDF2WithHmacSHA256 is supportted from JDK1.8
      SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
      byte[] hashed = skf.generateSecret(spec).getEncoded();
      return hashed;
  }
  ```

---

## `规则-9.3 基于哈希算法的口令安全存储必须加入盐值（salt）--盐值硬编码` 🟠 🔴[安全] `security`

加密算法的盐值硬编码。
实践中，一个口令可以编码为一个哈希值，且无法从哈希值逆向计算出原始的口令。口令是否相等可以通过比较它们的哈希值是否相等来判断。如果一个口令的哈希值储存在一个数据库中，由于哈希算法的不可逆性，攻击者就应该不可能还原出口令。如果说可以恢复口令，那么唯一的方式就是暴力破解攻击，比如计算所有可能口令的哈希值，或是字典攻击，计算出所有常用的口令的哈希值。如果每个口令都只仅经过简单哈希，相同的口令将得到相同的哈希值。仅保存口令哈希有以下两个缺陷：
1. 由于"生日判定"，攻击者可以快速找到一个口令，尤其是当数据库中的口令数量较大的时候。
2. 攻击者可以使用事先计算好的哈希列表在几秒钟之内破解口令。

为了解决这些问题，可以在进行哈希运算之前在口令中引入盐值。一个盐值是一个固定长度的随机数。这个盐值对于每个存储入口来说必须是不同的。可以明文方式紧邻哈希后的口令一起保存。在这样的配置下，攻击者必须对每一个口令分别进行暴力破解攻击。这样数据库便能抵御"生日"或者"彩虹表"攻击。

**修改建议：** 盐值不能为硬编码。

✅ **正确示例：**

盐值随机生成
  ```java
  public static byte[] createHash(char[] password) throws NoSuchAlgorithmException, InvalidKeySpecException {
      SecureRandom random = SecureRandom.getInstance("SHA1PRNG");
      byte[] salt = new byte[8];
      random.nextBytes(salt);
      PBEKeySpec spec = new PBEKeySpec(password, salt, 20000000, 256);
      //PBKDF2WithHmacSHA256 is supportted from JDK1.8
      SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
      byte[] hashed = skf.generateSecret(spec).getEncoded();
      return hashed;
  }
  ```

❌ **错误示例：**

盐值硬编码
  ```java
  public static byte[] createHash(char[] password) throws NoSuchAlgorithmException, InvalidKeySpecException {
      PBEKeySpec spec = new PBEKeySpec(password, "salt".getBytes(), 20000000, 256);
      //PBKDF2WithHmacSHA256 is supportted from JDK1.8
      SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
      byte[] hashed = skf.generateSecret(spec).getEncoded();
      return hashed;
  }
  ```

---

## `规则-9.2 禁止使用私有或者弱加密算法--弱加密算法` 🟠 🔴[安全] `security`

禁止使用私有算法或者弱加密算法（比如DES）。应该使用经过验证的、安全的、公开的加密算法。加密算法分为对称加密算法和非对称加密算法。推荐使用的对称加密算法有：AES。推荐使用的非对称算法有：RSA。

**修改建议：** 换用安全的加密算法。

❌ **错误示例：**

使用弱加密算法ARCFOUR
  ```java
  public void doSomething() throws Exception {
      Cipher.getInstance("ARCFOUR");
  }
  ```

---

## `规则-9.2 禁止使用私有或者弱加密算法--密钥长度少于指定长度` 🟠 🔴[安全] `security`

算法密钥长度少于指定长度。

**修改建议：** 确保算法密钥长度不少于指定长度。

✅ **正确示例：**

AES算法密钥长度不少于128
  ```java
  public void doSomething() throws Exception {
      KeyGenerator kg = KeyGenerator.getInstance("AES");
      kg.init(128);
  }
  ```

❌ **错误示例：**

AES算法密钥长度少于128
  ```java
  public void doSomething() throws Exception {
      KeyGenerator kg = KeyGenerator.getInstance("AES");
      kg.init(126);
  }
  ```

---

## `规则-9.2 禁止使用私有或者弱加密算法--RSA未使用填充模式` 🟠 🔴[安全] `security`

RSA未使用填充模式。

实际中，使用 RSA 公钥的加密通常与某种填充模式结合使用。该填充模式的目的在于防止一些针对 RSA 的攻击，这些攻击仅在执行不带填充模式的加密时才起作用。

**修改建议：** 为安全使用RSA，在执行加密时推荐使用OAEP（最优非对称加密填充模式）。

✅ **正确示例：**

执行加密时使用OAEP
  ```java
  public void doSomething() throws Exception {
      Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
  }
  ```

❌ **错误示例：**

执行加密时没有使用OAEP
  ```java
  public void doSomething() throws Exception {
      Cipher.getInstance("RSA");
  }
  ```

---

## `规则-9.2 禁止使用私有或者弱加密算法--AES使用不安全的ECB操作模式` 🟠 🔴[安全] `security`

AES使用不安全的ECB操作模式。

AES算法的ECB模式本质上较弱，因为它会对相同的明文块生成一样的密文。GCM模式由于没有这个缺陷，使之成为一个更好的选择。

**修改建议：** 对于AES算法，推荐使用GCM操作模式。

✅ **正确示例：**

AES使用GCM操作模式
  ```java
  public void doSomething() throws Exception {
      Cipher.getInstance("AES/GCM/PKCS5Padding");
  }
  ```

❌ **错误示例：**

AES使用ECB操作模式
  ```java
  public void doSomething() throws Exception {
      Cipher.getInstance("AES/ECB/NoPadding");
  }
  ```

---
