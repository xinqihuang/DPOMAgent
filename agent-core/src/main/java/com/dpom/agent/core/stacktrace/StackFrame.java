package com.dpom.agent.core.stacktrace;

/**
 * 一个应用栈帧。
 *
 * @param className  类名
 * @param methodName 方法名
 * @param fileName   文件名（可为空）
 * @param lineNumber 行号（可为空）
 */
public record StackFrame(String className, String methodName, String fileName, Integer lineNumber) {
}
