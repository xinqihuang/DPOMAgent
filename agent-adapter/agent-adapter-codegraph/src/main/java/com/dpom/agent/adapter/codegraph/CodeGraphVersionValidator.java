package com.dpom.agent.adapter.codegraph;

import com.dpom.agent.common.codegraph.CodeGraphQueryException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * CodeGraph 可执行文件校验：文件必须存在，版本必须与固定版本一致。
 */
public class CodeGraphVersionValidator {

    /**
     * 校验可执行文件存在且版本匹配。
     *
     * @param executable      CodeGraph 可执行文件路径
     * @param expectedVersion 期望版本（服务端固定）
     */
    public void validate(Path executable, String expectedVersion) {
        if (executable == null || !Files.isRegularFile(executable)) {
            throw new CodeGraphQueryException("CodeGraph 可执行文件不存在：" + executable);
        }
        String actual = runVersion(executable);
        requireVersionMatches(actual, expectedVersion, executable);
    }

    /**
     * 运行 codegraph version 读取版本。
     *
     * @param executable 可执行文件路径
     * @return 版本字符串
     */
    String runVersion(Path executable) {
        try {
            Process process = new ProcessBuilder(executable.toString(), "version")
                    .redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int code = process.waitFor();
            if (code != 0) {
                throw new CodeGraphQueryException("codegraph version 退出码非 0：" + code);
            }
            return output.lines().findFirst().orElse("").trim();
        } catch (IOException e) {
            throw new CodeGraphQueryException("无法执行 codegraph version", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CodeGraphQueryException("codegraph version 被中断", e);
        }
    }

    /**
     * 版本比对：不匹配即 fail。
     *
     * @param actual     实际版本
     * @param expected   期望版本
     * @param executable 可执行文件（用于错误信息）
     */
    static void requireVersionMatches(String actual, String expected, Path executable) {
        String actualVersion = actual == null ? "" : actual.trim();
        if (!expected.equals(actualVersion)) {
            throw new CodeGraphQueryException("CodeGraph 版本不匹配：期望 " + expected
                    + "，实际 " + actualVersion + "（" + executable + "）");
        }
    }
}
