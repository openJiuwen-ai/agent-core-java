# G.SEC Security 安全

共 3 条规则。

## `G.SEC.01 进行安全检查的方法必须声明为private或final` 🔴 🔴[安全] `security_standard_rule`

安全检查方法可能被子类重写。

实现安全检查功能（主要是指调用SecurityManager执行的安全检查）的方法，如果可以被子类重写，恶意子类可以重写安全检查方法，忽略这些安全检查，使安全检查失效。所以安全检查相关的方法必须声明为private或final，防止被子类重写。

**修改建议：** 请确保所有执行安全操作的方法都已在 final 类中声明，或者这些方法本身已声明为final/private。

✅ **正确示例：**

##### 场景1：安全检查的方法不声明为private或final。
- 修复示例1：安全检查的方法声明为private
```java
// 安全检查的方法声明为private
private void doSomething() {
    AccessController.checkPermission(new SecurityPermission("SomeAction"));
    ...
}
```
- 修复示例2：安全检查的方法声明为final
```java
// 安全检查的方法声明为final
public final void doSomething() {
    AccessController.checkPermission(new SecurityPermission("SomeAction"));
    ...
}
```

❌ **错误示例：**

##### 场景1：安全检查的方法不声明为private或final。
- 错误示例：安全检查的方法声明为public

```java       
// 用于执行安全检查的非最终方法可能会被绕过安全检查的多种方式覆盖
public void doSomething() {
    AccessController.checkPermission(new SecurityPermission("SomeAction"));
    ...
}
```

---

## `G.SEC.02 自定义类加载器覆写getPermission() 时，必须先调用父类的getPermission() 方法` 🔴 🔴[安全] `security_standard_rule`

自定义类加载器未调用父类的getPermissions()方法。

自定义类加载器，如果需要重写getPermissions()方法时，在给其它代码设置权限之前，必须要先调用父类的getPermissions()方法来应用默认的安全策略。自定义类加载器如果忽略调用父类的getPermissions()方法，该类加载器可以加载提升权限的不可信类。

**修改建议：** 继承URLClassLoader类时，重载getPermissions(CodeSource)方法时需要调用super.getPermissions()。

✅ **正确示例：**

##### 场景1：忽略调用父类的getPermissions()方法。
```java
@Override
protected PermissionCollection getPermissions(CodeSource cs) {

    super.getPermissions(cs); // 调用父类的getPermissions方法
    PermissionCollection pc = new Permissions();

// allow exit from the VM anytime
    pc.add(new RuntimePermission("exitVM"));
    return pc;
}
```

❌ **错误示例：**

##### 场景1：忽略调用父类的getPermissions()方法。
```java
@Override
protected PermissionCollection getPermissions(CodeSource cs) {

     // 没有调用父类的getPermissions方法。
    PermissionCollection pc = new Permissions();

    // allow exit from the VM anytime
    pc.add(new RuntimePermission("exitVM"));
    return pc;
}
```

---

## `G.SEC.04 使用安全管理器来保护敏感操作` 🟠 🔴[安全] `security_standard_recommend`

敏感操作缺少安全管理器。
所有的敏感操作必须经过安全管理器的检查，防止被不可信的代码调用。对于Java API 中的敏感操作，例如文件操作、向远程主机开放套接字连接以及创建ClassLoader 等，在源码中已经添加了安全检查的代码逻辑，开发者只需要安装安全管理器即可；对于应用本身的敏感操作，除了安装安全管理器之外，还需要自定义安全策略，并在合适的位置添加安全检查的代码。

**修改建议：** 所有的敏感操作必须经过安全管理器的检查，防止被不可信的代码调用。

✅ **正确示例：**

```java
public class SensitiveInfoManage {
    private static final SensitiveResourcePermission REMOVE_ENTRY_PERMISSION =
        new SensitiveResourcePermission("removeInfo");

    private final Map<Integer, String> resourceMap = new HashMap<>();

    public final boolean remove(Integer id) {
        securityCheck(); // 增加安全管理器检查
        return resourceMap.remove(id) != null;
    }

    private void securityCheck() {
        SecurityManager securityManager = System.getSecurityManager();
        if (securityManager != null) {
            securityManager.checkPermission(REMOVE_ENTRY_PERMISSION);
        }
    }
    ... // 其他代码
}
```
同时需要在protect.policy文件进行如下的权限配置，授权基于路径在"file:${{trusted.code.dirs}}/*"的class和jar包，所有权限。
```
grant codeBase "file:${{trusted.code.dirs}}/*" {
    permission com.huawei.security.SensitiveResourcePermission "removeInfo";
};
```

❌ **错误示例：**

```java
public class SensitiveInfoManage {
    private final Map<Integer, String> resourceMap = new HashMap<>();

    // 未进行安全管理器检查
    public boolean remove(Integer id) {
        return resourceMap.remove(id) != null;
    }

    ... // 其他代码
}
```

---
