# com.openjiuwen.core.session.store.FileStore

## 类 FileStore

```java
public class FileStore extends Store
```

预留中的文件存储实现；当前源码尚未补齐真正的文件读写逻辑。

## 方法

| 签名 | 说明 |
| --- | --- |
| `public Object read(Object key)` | 当前始终返回 `null`。 |
| `public void write(Map<String, Object> value)` | 当前为空实现，方法体只保留 `TODO` 注释。 |

## 说明

- 该类已经继承 `Store`，但尚未提供任何持久化行为，可视为占位接口实现。
