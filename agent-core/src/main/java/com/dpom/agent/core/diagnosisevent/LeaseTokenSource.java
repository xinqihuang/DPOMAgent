package com.dpom.agent.core.diagnosisevent;

/**
 * 生成不可预测租约 fencing token 的边界。
 */
@FunctionalInterface
public interface LeaseTokenSource {
    /** 返回新 token。 */
    String nextToken();
}
