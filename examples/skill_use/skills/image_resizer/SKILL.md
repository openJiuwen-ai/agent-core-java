---
name: image_resizer
description: Use this skill to resize images via OpenCV.
---

# Image Resizer

This skill enables an agent to resize and rescale images using **OpenCV**.

## Overview

The skill supports:

- Resize by explicit dimensions
- Resize while preserving aspect ratio
- Scale images up or down using factors
- Select interpolation methods for quality vs. speed trade-offs

Interpolation determines how pixel values are computed during resizing. Common interpolation methods are:

- `INTER_NEAREST` → fastest, lowest quality
- `INTER_LINEAR` → balanced default
- `INTER_CUBIC` → smoother high-quality upscaling
- `INTER_AREA` → best for shrinking images
- `INTER_LANCZOS4` → high-quality scaling preserving detail

## Installation

If OpenCV isn't installed, install OpenCV via the following command:

```python
pip install opencv-python -q
```

## Core Concept

OpenCV provides the following function:

```python
cv2.resize(src, dsize[, dst[, fx[, fy[, interpolation]]]])
```

- **src** – input image  
- **dsize** – target size `(width, height)`  
- **fx, fy** – x and y scaling factors (used when `dsize=None`)  
- **interpolation** – method for computing pixel values during scaling  

The function returns a resized NumPy image that can be displayed, saved, or further processed.

Use **either** explicit size (`dsize`) **or** scaling factors (`fx`, `fy`), not both simultaneously.