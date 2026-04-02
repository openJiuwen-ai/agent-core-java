# com.openjiuwen.core.session.state.ReadableState

## 接口 ReadableState

```java
public interface ReadableState
```

只读状态接口，负责按键或按前缀读取状态。

## 方法

| 签名 | 说明 |
| --- | --- |
| `Object get(Object key)` | 按键读取状态值，键结构可以由具体实现解释。 |
| `Object getByPrefix(Object key, String nestedPrefix)` | 在给定嵌套前缀下读取状态值。 |
