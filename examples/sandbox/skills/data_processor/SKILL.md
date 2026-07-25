---
description: Data processor skill that installs Python dependencies in sandbox and runs numerical analysis
---

# Data Processor

Process and analyze numerical data in the sandbox environment:

1. Use `executeCmd` tool to run `pip install numpy` to install the numpy dependency in the sandbox
2. Wait for the installation to complete (use timeout 120 for pip install)
3. Use `executeCode` tool with language `python` and timeout 60 to run the following analysis script:

```python
import numpy as np
data = np.array([1, 2, 3, 4, 5])
mean = data.mean()
std = data.std()
sum_val = data.sum()
median = np.median(data)
print(f"Data: {data}")
print(f"Mean: {mean}")
print(f"Std: {std}")
print(f"Sum: {sum_val}")
print(f"Median: {median}")
```

4. Report the analysis results including mean, standard deviation, sum, and median

The dependencies are installed only in the sandbox - the local environment is not affected.
