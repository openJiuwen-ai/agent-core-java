# com.openjiuwen.core.session.callback.BaseHandler

## 类 BaseHandler

```java
public abstract class BaseHandler
```

回调处理器抽象基类，用于封装无状态事件处理逻辑。

## 方法

| 签名 | 说明 |
| --- | --- |
| `public Object getOwner()` | 返回当前处理器关联的所属对象。 |
| `public abstract String eventName()` | 返回该处理器关联的事件名。 |
| `public List<String> getTriggerEvents()` | 通过反射收集所有标记了 `@TriggerEvent` 的公开方法名。 |

## 说明

- 源码通过受保护构造方法接收 `owner`，供具体子类在实例化时绑定所属对象。
- `getTriggerEvents()` 会遍历 `getClass().getMethods()`，因此只统计运行时可见的公开方法。
