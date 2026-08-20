package com.dpom.agent.alarm.persistence.command;

/**
 * AlarmAuditInsert 插入命令（mutable，自增主键回填 {@code id}）。
 */
public class AlarmAuditInsert {

    private Long id;
    private final String action;
    private final String targetType;
    private final Long targetId;
    private final String operator;
    private final String detail;
    private final String result;

    /**
     * 构造插入命令。
     *
     * @param action     动作
     * @param targetType 目标类型
     * @param targetId   目标 id（可为空）
     * @param operator   操作人（可为空）
     * @param detail     详情（可为空）
     * @param result     结果（可为空）
     */
    public AlarmAuditInsert(String action, String targetType, Long targetId, String operator, String detail,
            String result) {
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.operator = operator;
        this.detail = detail;
        this.result = result;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getAction() {
        return action;
    }

    public String getTargetType() {
        return targetType;
    }

    public Long getTargetId() {
        return targetId;
    }

    public String getOperator() {
        return operator;
    }

    public String getDetail() {
        return detail;
    }

    public String getResult() {
        return result;
    }
}
