# search

`com.openjiuwen.core.memory.manage.search` 提供记忆检索入口与参数模型，负责把查询条件转换为管理层可执行的搜索请求。

## 核心类型

| 类型 | 说明 |
| --- | --- |
| [`SearchManager`](./search/SearchManager.md) | 统一封装变量、分片记忆和摘要记忆的查询逻辑。 |
| [`SearchParams`](./search/SearchParams.md) | 记忆搜索参数对象。 |

## 相关测试

- `SearchManagerTest`
