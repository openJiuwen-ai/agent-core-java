# com.openjiuwen.core.session.store.Store

## 类 Store

```java
public abstract class Store
```

键值存储抽象基类。

## 方法

| 签名 | 说明 |
| --- | --- |
| `public abstract Object read(Object key)` | 按键或 schema 读取值。 |
| `public abstract void write(Map<String, Object> value)` | 把数据写入存储。 |

## 说明

- 相关测试：`InMemoryCheckpointerTest`。
