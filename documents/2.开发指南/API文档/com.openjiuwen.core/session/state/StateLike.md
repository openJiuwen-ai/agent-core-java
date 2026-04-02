# com.openjiuwen.core.session.state.StateLike

## 接口 StateLike

```java
public interface StateLike extends ReadableState, RecoverableState
```

可读写的状态接口，在只读与可恢复能力之上增加更新和转换读取入口。

## 方法

| 签名 | 说明 |
| --- | --- |
| `void update(Map<String, Object> data)` | 合并一组状态更新。 |
| `Object getByTransformer(Function<Object, Object> transformer)` | 使用转换函数从状态对象中提取结果。 |

## 说明

- 相关测试：`StateTest`。
