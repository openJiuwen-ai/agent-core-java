# com.openjiuwen.core.memory.manage.mem_model.DataIdManager

## class DataIdManager

```java
public class DataIdManager
```

Generates unique memory IDs using timestamp + random + user hash.

## Fields

| Field | Type | Description |
| --- | --- | --- |
| `random` | `SecureRandom` | random. |

## Methods

| Signature | Description |
| --- | --- |
| `public String generateNextId(String userId)` | Generate a unique hex ID based on current time, random bytes, and user ID hash. |
