package com.dpom.agent.core.workspace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 受控代码工作区验收测试：正常 list/search/read、三类路径逃逸拒绝、超大源码限制。
 */
class CodeWorkspaceTest {

    private final CodeWorkspace workspace = new CodeWorkspace();

    /**
     * 正常列出文件。
     */
    @Test
    void listsFiles(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("A.java"), "a");
        Files.writeString(tempDir.resolve("B.java"), "b");
        Files.createDirectories(tempDir.resolve("sub"));
        Files.writeString(tempDir.resolve("sub").resolve("C.java"), "c");

        List<String> files = workspace.listFiles(tempDir, "", 100);

        assertThat(files).containsExactly("A.java", "B.java");
    }

    /**
     * 正常读取源码。
     */
    @Test
    void readsSource(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("A.java"), "line1\nline2\nline3");

        String content = workspace.readSource(tempDir, "A.java", 200, 65536);

        assertThat(content).isEqualTo("line1\nline2\nline3");
    }

    /**
     * 正常搜索文本。
     */
    @Test
    void searchesText(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("A.java"), "INSERT INTO asset\nSELECT 1");
        Files.writeString(tempDir.resolve("B.java"), "no match");

        List<SearchHit> hits = workspace.searchText(tempDir, "INSERT INTO", 100);

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).filePath()).isEqualTo("A.java");
        assertThat(hits.get(0).lineNumber()).isEqualTo(1);
    }

    /**
     * 拒绝 ../ 路径越界。
     */
    @Test
    void rejectsParentTraversal(@TempDir Path tempDir) throws IOException {
        Path root = tempDir.resolve("root");
        Files.createDirectories(root);
        Files.writeString(tempDir.resolve("secret.txt"), "secret");

        assertThatThrownBy(() -> workspace.readSource(root, "../secret.txt", 200, 65536))
                .isInstanceOf(WorkspaceAccessException.class);
    }

    /**
     * 拒绝绝对路径逃逸。
     */
    @Test
    void rejectsAbsolutePath(@TempDir Path tempDir) throws IOException {
        Path root = tempDir.resolve("root");
        Files.createDirectories(root);
        Path outside = tempDir.resolve("outside.txt");
        Files.writeString(outside, "secret");

        assertThatThrownBy(() -> workspace.readSource(root, outside.toAbsolutePath().toString(), 200, 65536))
                .isInstanceOf(WorkspaceAccessException.class);
    }

    /**
     * 拒绝符号链接逃逸。
     */
    @Test
    void rejectsSymlinkEscape(@TempDir Path tempDir) throws IOException {
        Path root = tempDir.resolve("root");
        Files.createDirectories(root);
        Files.writeString(root.resolve("A.java"), "a");
        Path outside = tempDir.resolve("outside.txt");
        Files.writeString(outside, "secret");

        Path link = root.resolve("link.txt");
        boolean symlinkCreated;
        try {
            Files.createSymbolicLink(link, outside);
            symlinkCreated = true;
        } catch (UnsupportedOperationException | IOException e) {
            symlinkCreated = false;
        }
        assumeTrue(symlinkCreated, "当前环境不支持创建符号链接");

        assertThatThrownBy(() -> workspace.readSource(root, "link.txt", 200, 65536))
                .isInstanceOf(WorkspaceAccessException.class);
    }

    /**
     * 超大源码限制：字节数超限拒绝、行数超限截断。
     */
    @Test
    void limitsOversizedSource(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("big.java"), "x".repeat(200));

        assertThatThrownBy(() -> workspace.readSource(tempDir, "big.java", 200, 50))
                .isInstanceOf(WorkspaceLimitException.class);

        StringBuilder manyLines = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            manyLines.append("line").append(i).append("\n");
        }
        Files.writeString(tempDir.resolve("many.java"), manyLines.toString());

        String truncated = workspace.readSource(tempDir, "many.java", 3, 65536);
        assertThat(truncated.lines()).hasSize(3);
    }

    /**
     * 从指定起始行读取：应返回该行附近的方法体而非文件开头的许可证头。
     */
    @Test
    void readsFromStartLine(@TempDir Path tempDir) throws IOException {
        StringBuilder src = new StringBuilder();
        for (int i = 1; i <= 300; i++) {
            src.append("/* header line ").append(i).append(" */\n");
        }
        src.append("public Configuration getConfiguration() { return null; }\n");
        Files.writeString(tempDir.resolve("ConfigurationFactory.java"), src.toString());

        String content = workspace.readSource(tempDir, "ConfigurationFactory.java", 301, 5, 65536);

        assertThat(content).contains("getConfiguration");
        assertThat(content).doesNotContain("header line 1");
        assertThat(content.lines()).hasSize(1);
    }
}