package com.dpom.agent.common.codegraph;

/**
 * 提交不一致异常：请求的 commit 与已注册仓库的 commit 不一致。
 */
public class CommitMismatchException extends CodeGraphException {

    /**
     * 构造异常。
     *
     * @param message 错误信息
     */
    public CommitMismatchException(String message) {
        super(message);
    }
}
