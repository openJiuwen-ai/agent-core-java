# utils

`com.openjiuwen.core.common.utils` 提供框架级通用工具，覆盖嵌套 `Map`/`List` 处理、哈希键生成、本机 IP 探测、消息上下文写入、Schema 校验与单例生命周期支持。

## 核心类型

| 类型 | 说明 |
| --- | --- |
| [`DictUtils`](./utils/DictUtils.md) | 处理嵌套结构的构造、拍平、叶子抽取与重建。 |
| [`HashUtil`](./utils/HashUtil.md) | 基于 SHA-256 生成稳定哈希键。 |
| [`IpUtils`](./utils/IpUtils.md) | 获取本机可用 IPv4 地址。 |
| [`MessageUtils`](./utils/MessageUtils.md) | 向 `ContextEngine`/`ModelContext` 追加或读取消息历史。 |
| [`SchemaUtils`](./utils/SchemaUtils.md) | 根据 schema 填充默认值、校验数据并移除空值。 |
| [`SingletonSupport`](./utils/SingletonSupport.md) | 为 Java 服务类型提供线程安全单例支持。 |

## 说明

- `DictUtilsTest` 覆盖叶子提取、路径格式化、列表索引路径与结构重建行为。
- `SchemaUtilsTest` 覆盖默认值填充、必填字段校验、数值与字符串约束校验，以及 `getSchemaDict` 的反射结果。
