package top.lldwb.alistmediasync.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PathUtils 单元测试
 * <p>
 * 按 contracts/PathUtils.md 契约表格穷举用例，覆盖正常路径、边界、null 安全。
 * </p>
 *
 * @author AList-Media-Sync
 */
@DisplayName("PathUtils 路径工具类")
class PathUtilsTest {

    // ================================================================
    // join
    // ================================================================

    @Nested
    @DisplayName("join — 路径拼接")
    class JoinTests {

        @Test
        @DisplayName("正常拼接：/a + b.mp4 → /a/b.mp4")
        void shouldJoinDirAndName() {
            assertEquals("/a/b.mp4", PathUtils.join("/a", "b.mp4"));
        }

        @Test
        @DisplayName("dir 尾斜杠：/a/ + b.mp4 → /a/b.mp4")
        void shouldHandleTrailingSlash() {
            assertEquals("/a/b.mp4", PathUtils.join("/a/", "b.mp4"));
        }

        @Test
        @DisplayName("name 前导斜杠：/a + /b.mp4 → /a/b.mp4")
        void shouldStripLeadingSlashFromName() {
            assertEquals("/a/b.mp4", PathUtils.join("/a", "/b.mp4"));
        }

        @Test
        @DisplayName("name 多个前导斜杠：/a + //b.mp4 → /a/b.mp4")
        void shouldStripMultipleLeadingSlashes() {
            assertEquals("/a/b.mp4", PathUtils.join("/a", "//b.mp4"));
        }

        @Test
        @DisplayName("根目录：/ + b.mp4 → /b.mp4")
        void shouldHandleRootDir() {
            assertEquals("/b.mp4", PathUtils.join("/", "b.mp4"));
        }

        @Test
        @DisplayName("空 dir：'' + b.mp4 → /b.mp4")
        void shouldHandleEmptyDir() {
            assertEquals("/b.mp4", PathUtils.join("", "b.mp4"));
        }

        @Test
        @DisplayName("null dir：null + b.mp4 → /b.mp4")
        void shouldHandleNullDir() {
            assertEquals("/b.mp4", PathUtils.join(null, "b.mp4"));
        }

        @Test
        @DisplayName("null name 应抛 IllegalArgumentException")
        void shouldThrowOnNullName() {
            assertThrows(IllegalArgumentException.class, () -> PathUtils.join("/a", null));
        }

        @Test
        @DisplayName("空 name：/a + '' → /a/")
        void shouldHandleEmptyName() {
            assertEquals("/a/", PathUtils.join("/a", ""));
        }

        @Test
        @DisplayName("多层路径拼接")
        void shouldJoinNestedPath() {
            assertEquals("/a/b/c/d.mp4", PathUtils.join("/a/b/c", "d.mp4"));
        }

        @Test
        @DisplayName("name 仅含斜杠")
        void shouldHandleSlashOnlyName() {
            assertEquals("/a/", PathUtils.join("/a", "/"));
        }
    }

    // ================================================================
    // parentDir
    // ================================================================

    @Nested
    @DisplayName("parentDir — 提取父目录")
    class ParentDirTests {

        @Test
        @DisplayName("多层路径：/a/b/c.mp4 → /a/b")
        void shouldExtractParentDir() {
            assertEquals("/a/b", PathUtils.parentDir("/a/b/c.mp4"));
        }

        @Test
        @DisplayName("单层路径：/c.mp4 → ''")
        void shouldReturnEmptyForRootFile() {
            assertEquals("", PathUtils.parentDir("/c.mp4"));
        }

        @Test
        @DisplayName("根目录：/ → ''")
        void shouldReturnEmptyForRoot() {
            assertEquals("", PathUtils.parentDir("/"));
        }

        @Test
        @DisplayName("null 应抛 IllegalArgumentException")
        void shouldThrowOnNull() {
            assertThrows(IllegalArgumentException.class, () -> PathUtils.parentDir(null));
        }

        @Test
        @DisplayName("无斜杠的文件名")
        void shouldHandleNoSlash() {
            assertEquals("", PathUtils.parentDir("file.txt"));
        }
    }

    // ================================================================
    // baseName
    // ================================================================

    @Nested
    @DisplayName("baseName — 提取文件名")
    class BaseNameTests {

        @Test
        @DisplayName("多层路径：/a/b/c.mp4 → c.mp4")
        void shouldExtractBaseName() {
            assertEquals("c.mp4", PathUtils.baseName("/a/b/c.mp4"));
        }

        @Test
        @DisplayName("单层路径：c.mp4 → c.mp4")
        void shouldReturnSameForPlainName() {
            assertEquals("c.mp4", PathUtils.baseName("c.mp4"));
        }

        @Test
        @DisplayName("根目录：/ → ''")
        void shouldReturnEmptyForRoot() {
            assertEquals("", PathUtils.baseName("/"));
        }

        @Test
        @DisplayName("null 应抛 IllegalArgumentException")
        void shouldThrowOnNull() {
            assertThrows(IllegalArgumentException.class, () -> PathUtils.baseName(null));
        }
    }

    // ================================================================
    // swapExtension
    // ================================================================

    @Nested
    @DisplayName("swapExtension — 替换扩展名")
    class SwapExtensionTests {

        @Test
        @DisplayName("正常替换：video.flv → mp3")
        void shouldSwapExtension() {
            assertEquals("video.mp3", PathUtils.swapExtension("video.flv", "mp3"));
        }

        @Test
        @DisplayName("newExt 带前导点：video.flv → .mp3")
        void shouldStripLeadingDot() {
            assertEquals("video.mp3", PathUtils.swapExtension("video.flv", ".mp3"));
        }

        @Test
        @DisplayName("无扩展名：video → mp3")
        void shouldAppendExtension() {
            assertEquals("video.mp3", PathUtils.swapExtension("video", "mp3"));
        }

        @Test
        @DisplayName("点开头的文件名：.gitignore → txt")
        void shouldHandleDotFile() {
            assertEquals(".gitignore.txt", PathUtils.swapExtension(".gitignore", "txt"));
        }

        @Test
        @DisplayName("多扩展名：archive.tar.gz → zip")
        void shouldReplaceLastExtension() {
            assertEquals("archive.tar.zip", PathUtils.swapExtension("archive.tar.gz", "zip"));
        }

        @Test
        @DisplayName("null fileName 应抛异常")
        void shouldThrowOnNullFileName() {
            assertThrows(IllegalArgumentException.class, () -> PathUtils.swapExtension(null, "mp3"));
        }

        @Test
        @DisplayName("null newExt 应抛异常")
        void shouldThrowOnNullNewExt() {
            assertThrows(IllegalArgumentException.class, () -> PathUtils.swapExtension("a.mp4", null));
        }
    }

    // ================================================================
    // trimLeadingSlash
    // ================================================================

    @Nested
    @DisplayName("trimLeadingSlash — 去除前导斜杠")
    class TrimLeadingSlashTests {

        @Test
        @DisplayName("单斜杠：/abc → abc")
        void shouldTrimSingleSlash() {
            assertEquals("abc", PathUtils.trimLeadingSlash("/abc"));
        }

        @Test
        @DisplayName("多斜杠：///abc → abc")
        void shouldTrimMultipleSlashes() {
            assertEquals("abc", PathUtils.trimLeadingSlash("///abc"));
        }

        @Test
        @DisplayName("无斜杠：abc → abc")
        void shouldKeepUnchanged() {
            assertEquals("abc", PathUtils.trimLeadingSlash("abc"));
        }

        @Test
        @DisplayName("null → ''")
        void shouldReturnEmptyForNull() {
            assertEquals("", PathUtils.trimLeadingSlash(null));
        }

        @Test
        @DisplayName("仅斜杠：/// → ''")
        void shouldReturnEmptyForSlashesOnly() {
            assertEquals("", PathUtils.trimLeadingSlash("///"));
        }
    }

    // ================================================================
    // normalize
    // ================================================================

    @Nested
    @DisplayName("normalize — 路径规范化")
    class NormalizeTests {

        @Test
        @DisplayName("尾斜杠去除：/a/ → /a")
        void shouldRemoveTrailingSlash() {
            assertEquals("/a", PathUtils.normalize("/a/"));
        }

        @Test
        @DisplayName("补前导斜杠：a → /a")
        void shouldAddLeadingSlash() {
            assertEquals("/a", PathUtils.normalize("a"));
        }

        @Test
        @DisplayName("空字符串 → /")
        void shouldReturnRootForEmpty() {
            assertEquals("/", PathUtils.normalize(""));
        }

        @Test
        @DisplayName("null → /")
        void shouldReturnRootForNull() {
            assertEquals("/", PathUtils.normalize(null));
        }

        @Test
        @DisplayName("内部重复斜杠保留：/a//b/ → /a//b")
        void shouldKeepInternalDoubleSlashes() {
            assertEquals("/a//b", PathUtils.normalize("/a//b/"));
        }

        @Test
        @DisplayName("反斜杠转换：a\\b → /a/b")
        void shouldConvertBackslashes() {
            assertEquals("/a/b", PathUtils.normalize("a\\b"));
        }

        @Test
        @DisplayName("仅空白 → /")
        void shouldReturnRootForBlank() {
            assertEquals("/", PathUtils.normalize("   "));
        }
    }

    // ================================================================
    // 组合场景
    // ================================================================

    @Test
    @DisplayName("组合：join + parentDir + baseName 往返一致")
    void shouldRoundTripJoinParentBase() {
        String full = "/a/b/c.mp4";
        String dir = PathUtils.parentDir(full);
        String name = PathUtils.baseName(full);
        assertEquals(full, PathUtils.join(dir, name));
    }

    @Test
    @DisplayName("组合：swapExtension + baseName")
    void shouldSwapAndExtract() {
        String full = "/videos/stream.flv";
        String name = PathUtils.baseName(full);
        String swapped = PathUtils.swapExtension(name, "mp3");
        assertEquals("stream.mp3", swapped);
    }
}
