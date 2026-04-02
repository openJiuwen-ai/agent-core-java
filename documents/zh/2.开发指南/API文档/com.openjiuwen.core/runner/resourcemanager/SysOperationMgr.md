# com.openjiuwen.core.runner.resourcemanager.SysOperationMgr

## 类 SysOperationMgr

```java
public class SysOperationMgr
```

`SysOperationMgr` 负责 `SysOperation` 实例的注册、获取、移除与清空。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `sysOperations` | `ConcurrentHashMap<String, SysOperation>` | `new ConcurrentHashMap<>()` | - |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public void addSysOperation(String sysOperationId, SysOperation sysOperationInstance)` | - |
| `public SysOperation removeSysOperation(String sysOperationId)` | - |
| `public void clear()` | 清空已注册的系统操作实例。 |
| `public SysOperation getSysOperation(String sysOperationId)` | - |
