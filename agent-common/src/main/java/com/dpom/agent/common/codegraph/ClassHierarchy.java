package com.dpom.agent.common.codegraph;

import java.util.List;

/**
 * 类继承层次。
 *
 * @param className 类名
 * @param ancestors 祖先类列表
 */
public record ClassHierarchy(String className, List<String> ancestors) {
}
