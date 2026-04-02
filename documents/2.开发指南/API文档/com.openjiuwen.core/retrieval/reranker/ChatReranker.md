# com.openjiuwen.core.retrieval.reranker.ChatReranker

## 类 ChatReranker

```java
public class ChatReranker extends StandardReranker
```

基于 chat completion 的重排器，通过 `yes` 与 `no` token 的 logprob 计算相关性分数。

## 说明

- 端点固定为 `/chat/completions`。
- 每个候选会单独发起一次请求。
- `RerankerConfig.yesNoIds` 必须包含两个 token id；测试确认缺失时会抛出异常。
