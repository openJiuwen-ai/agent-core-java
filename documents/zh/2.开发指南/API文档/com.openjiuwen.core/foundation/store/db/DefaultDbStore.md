# com.openjiuwen.core.foundation.store.db.DefaultDbStore

## class DefaultDbStore

```java
public class DefaultDbStore extends BaseDbStore<DataSource>
```

基于 JDBC 的默认数据库存储包装器。

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public DefaultDbStore(String jdbcUrl)` | 只通过 JDBC URL 创建底层 `DataSource`。 |
| `public DefaultDbStore(String jdbcUrl, String username, String password)` | 使用 JDBC URL、用户名与密码创建底层 `DataSource`。 |

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public DataSource getEngine()` | 返回底层 `DataSource`。 |

## 说明

- 该实现不是连接池，而是通过 `DriverManager` 风格接口创建连接。
