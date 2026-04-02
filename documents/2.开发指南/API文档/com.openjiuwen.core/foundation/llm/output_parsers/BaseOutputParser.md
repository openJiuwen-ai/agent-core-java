# com.openjiuwen.core.foundation.llm.output_parsers.BaseOutputParser

## class BaseOutputParser

```java
public abstract class BaseOutputParser
```

Base class for parsing LLM output into the desired format.

## Methods

| Signature | Description |
| --- | --- |
| `public abstract Object parse(Object inputs) throws Exception` | Parse LLM output. |
| `public abstract Iterator<Object> streamParse(Iterator<?> streamingInputs) throws Exception` | Parse streaming LLM output. |
