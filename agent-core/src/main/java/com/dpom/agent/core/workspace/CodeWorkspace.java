package com.dpom.agent.core.workspace;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 受控代码工作区：仅在允许的快照根目录内搜索/阅读源码。
 *
 * <p>拒绝 {@code ../}、绝对路径与符号链接逃逸；对行数/字节数/命中数施加上限；不提供任意 shell。</p>
 */
@Service
public class CodeWorkspace {

    /**
     * 列出目录下的一级文件（相对根目录路径）。
     *
     * @param root         允许的工作区根目录
     * @param relativePath 相对路径（空串表示根目录）
     * @param maxEntries   最大返回条目数
     * @return 文件相对路径列表
     */
    public List<String> listFiles(Path root, String relativePath, int maxEntries) {
        Path dir = resolveWithinRoot(root, relativePath);
        try (Stream<Path> walk = Files.walk(dir, 1)) {
            return walk
                    .filter(path -> !path.equals(dir))
                    .filter(Files::isRegularFile)
                    .limit(maxEntries)
                    .map(path -> dir.relativize(path).toString().replace('\\', '/'))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new WorkspaceAccessException("无法列出目录：" + relativePath, e);
        }
    }

    /**
     * 递归列出目录下的源码文件（相对根目录路径）。
     *
     * @param root         允许的工作区根目录
     * @param relativePath 相对路径（空串表示根目录）
     * @param maxEntries   最大返回条目数
     * @return 文件相对路径列表
     */
    public List<String> listFilesRecursive(Path root, String relativePath, int maxEntries) {
        Path dir = resolveWithinRoot(root, relativePath);
        Path rootReal = resolveReal(root);
        try (Stream<Path> walk = Files.walk(dir)) {
            return walk
                    .filter(Files::isRegularFile)
                    .filter(path -> !Files.isSymbolicLink(path))
                    .limit(maxEntries)
                    .map(path -> rootReal.relativize(path).toString().replace('\\', '/'))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new WorkspaceAccessException("无法列出目录：" + relativePath, e);
        }
    }

    /**
     * 解析根目录真实路径（失败则回退原路径）。
     */
    private Path resolveReal(Path root) {
        try {
            return root.toRealPath();
        } catch (IOException e) {
            return root;
        }
    }

    /**
     * 在根目录内搜索文本。
     *
     * @param root    允许的工作区根目录
     * @param pattern 正则表达式
     * @param maxHits 最大命中数
     * @return 命中列表
     */
    public List<SearchHit> searchText(Path root, String pattern, int maxHits) {
        Pattern compiled = Pattern.compile(pattern);
        List<SearchHit> hits = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile)
                    .filter(path -> !Files.isSymbolicLink(path))
                    .forEach(file -> searchFile(file, root, compiled, hits, maxHits));
        } catch (IOException e) {
            throw new WorkspaceAccessException("无法搜索目录：" + root, e);
        }
        return hits;
    }

    /**
     * 读取源码文件内容。
     *
     * @param root         允许的工作区根目录
     * @param relativePath 相对路径
     * @param maxLines     最大返回行数
     * @param maxBytes     最大文件字节数
     * @return 文件内容
     */
    public String readSource(Path root, String relativePath, int maxLines, long maxBytes) {
        return readSource(root, relativePath, 1, maxLines, maxBytes);
    }

    /**
     * 从指定起始行读取源码文件内容。
     *
     * @param root         允许的工作区根目录
     * @param relativePath 相对路径
     * @param startLine    起始行（从 1 开始，小于 1 视为 1）
     * @param maxLines     最大返回行数
     * @param maxBytes     最大文件字节数
     * @return 从 startLine 起至多 maxLines 行的内容（超出文件末尾则为空串）
     */
    public String readSource(Path root, String relativePath, int startLine, int maxLines, long maxBytes) {
        Path file = resolveWithinRoot(root, relativePath);
        long size;
        try {
            size = Files.size(file);
        } catch (IOException e) {
            throw new WorkspaceAccessException("无法读取文件：" + relativePath, e);
        }
        if (size > maxBytes) {
            throw new WorkspaceLimitException("文件过大：" + size + " 字节，上限 " + maxBytes);
        }
        try {
            List<String> lines = Files.readAllLines(file);
            int from = Math.max(0, startLine - 1);
            if (from >= lines.size()) {
                return "";
            }
            int to = Math.min(lines.size(), from + maxLines);
            return String.join("\n", lines.subList(from, to));
        } catch (IOException e) {
            throw new WorkspaceAccessException("无法读取文件：" + relativePath, e);
        }
    }

    /**
     * 把相对路径解析到根目录内，校验不越界。
     */
    private Path resolveWithinRoot(Path root, String relativePath) {
        Path rootReal;
        try {
            rootReal = root.toRealPath();
        } catch (IOException e) {
            throw new WorkspaceAccessException("工作区根目录不可访问：" + root, e);
        }
        Path candidate = rootReal.resolve(relativePath).normalize();
        if (!candidate.startsWith(rootReal)) {
            throw new WorkspaceAccessException("路径越界：" + relativePath);
        }
        try {
            Path real = candidate.toRealPath();
            if (!real.startsWith(rootReal)) {
                throw new WorkspaceAccessException("符号链接越界：" + relativePath);
            }
            return real;
        } catch (IOException e) {
            throw new WorkspaceAccessException("路径不存在或不可访问：" + relativePath, e);
        }
    }

    /**
     * 在单个文件内搜索并累计命中。
     */
    private void searchFile(Path file, Path root, Pattern pattern, List<SearchHit> hits, int maxHits) {
        List<String> lines;
        try {
            lines = Files.readAllLines(file);
        } catch (IOException e) {
            return;
        }
        String relative = root.relativize(file).toString().replace('\\', '/');
        for (int i = 0; i < lines.size() && hits.size() < maxHits; i++) {
            if (pattern.matcher(lines.get(i)).find()) {
                hits.add(new SearchHit(relative, i + 1, lines.get(i)));
            }
        }
    }
}
