# 契约：PathUtils

**关联**：[../data-model.md](../data-model.md) E-1

## 公共 API

```java
package top.lldwb.alistmediasync.common.util;

public final class PathUtils {
    private PathUtils() {}

    public static String join(String dir, String name);
    public static String parentDir(String fullPath);
    public static String baseName(String fullPath);
    public static String swapExtension(String fileName, String newExt);
    public static String trimLeadingSlash(String path);
    public static String normalize(String path);
}
```

## 行为契约

### join(dir, name)

| 输入 | 输出 |
|------|------|
| `join("/a", "b.mp4")` | `/a/b.mp4` |
| `join("/a/", "b.mp4")` | `/a/b.mp4` |
| `join("/a", "/b.mp4")` | `/a/b.mp4`（去除 name 前导斜杠） |
| `join("/a", "//b.mp4")` | `/a/b.mp4`（去除所有前导斜杠） |
| `join("/", "b.mp4")` | `/b.mp4` |
| `join("", "b.mp4")` | `/b.mp4` |
| `join(null, "b.mp4")` | `/b.mp4` |
| `join("/a", null)` | 抛 `IllegalArgumentException` |
| `join("/a", "")` | `/a/`（保留行为，待测试确认或抛异常） |

### parentDir(fullPath)

| 输入 | 输出 |
|------|------|
| `parentDir("/a/b/c.mp4")` | `/a/b` |
| `parentDir("/c.mp4")` | `""` |
| `parentDir("/")` | `""` |
| `parentDir(null)` | 抛 `IllegalArgumentException` |

### baseName(fullPath)

| 输入 | 输出 |
|------|------|
| `baseName("/a/b/c.mp4")` | `c.mp4` |
| `baseName("c.mp4")` | `c.mp4` |
| `baseName("/")` | `""` |

### swapExtension(fileName, newExt)

| 输入 | 输出 |
|------|------|
| `swapExtension("video.flv", "mp3")` | `video.mp3` |
| `swapExtension("video.flv", ".mp3")` | `video.mp3`（自动去前导 `.`） |
| `swapExtension("video", "mp3")` | `video.mp3`（无原扩展名时直接追加） |
| `swapExtension(".gitignore", "txt")` | `.txt`（边界，需测试确认） |

### trimLeadingSlash(path)

| 输入 | 输出 |
|------|------|
| `trimLeadingSlash("/abc")` | `abc` |
| `trimLeadingSlash("///abc")` | `abc` |
| `trimLeadingSlash("abc")` | `abc` |
| `trimLeadingSlash(null)` | `""` |

### normalize(path)

| 输入 | 输出 |
|------|------|
| `normalize("/a/")` | `/a` |
| `normalize("a")` | `/a` |
| `normalize("")` | `/` |
| `normalize(null)` | `/` |
| `normalize("/a//b/")` | `/a//b`（不去内部重复斜杠，保持现行 SyncService 语义） |

## 性能契约

- 全部方法 O(n)，n = 路径长度；
- 不分配额外集合；
- 适合在热路径（扫描、上传、下载）每次调用。

## 测试要求

`PathUtilsTest.java` 覆盖：

- 上述每行表格各一个用例；
- null 与空串边界；
- 至少 30 个用例，行覆盖率 ≥ 95%。
