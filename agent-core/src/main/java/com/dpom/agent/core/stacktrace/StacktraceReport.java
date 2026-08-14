package com.dpom.agent.core.stacktrace;

import com.dpom.agent.common.codegraph.CodeSnapshot;

import java.util.List;

/**
 * 堆栈调查报告。
 *
 * @param snapshot       代码快照（含 commitSha）
 * @param frames         解析出的应用栈帧
 * @param sourceEvidence 源码证据
 * @param graphEvidence  代码图证据
 * @param rootCause      根因结论（必须引用源码位置）
 */
public record StacktraceReport(CodeSnapshot snapshot, List<StackFrame> frames,
                               List<SourceEvidence> sourceEvidence, List<GraphEvidence> graphEvidence,
                               String rootCause) {
}
