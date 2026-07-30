package org.example.nlp2dsl2sql.a2a;

/**
 * SQL HITL 确认文案与批准判定。
 */
public final class A2aSqlConfirmTexts {

    public static final String MARK_PENDING = "[SQL确认待审批]";
    public static final String MARK_PROMPT = "[请输入 yes 或 确认]";
    public static final String MARK_RESULT_PREFIX = "[SQL确认结果]";

    private A2aSqlConfirmTexts() {
    }

    /**
     * 服务端批准判定：trim 后忽略大小写，仅 yes 或 确认。
     *
     * @param rawInput 用户原始输入
     * @return 是否批准执行
     */
    public static boolean isApproved(String rawInput) {
        if (rawInput == null) {
            return false;
        }
        String t = rawInput.trim();
        return "yes".equalsIgnoreCase(t) || "确认".equals(t);
    }

    /**
     * 组装 SSE 待确认片段。
     *
     * @param sessionId  会话 ID
     * @param toolCallId 工具调用 ID
     * @param sql        待执行 SQL
     * @return SSE 文本
     */
    public static String formatPending(
            String sessionId, String toolCallId, String sql) {
        return "\n\n========== " + MARK_PENDING + " ==========\n"
                + "sessionId: " + nullToEmpty(sessionId) + "\n"
                + "toolCallId: " + nullToEmpty(toolCallId) + "\n"
                + "sql:\n"
                + nullToEmpty(sql) + "\n"
                + "========== " + MARK_PROMPT + " ==========\n\n";
    }

    /**
     * 组装确认结果片段。
     *
     * @param approved 是否批准
     * @param reason   可选原因
     * @return SSE 文本
     */
    public static String formatResult(boolean approved, String reason) {
        String suffix = (reason == null || reason.isBlank())
                ? ""
                : " (" + reason + ")";
        return "\n========== " + MARK_RESULT_PREFIX
                + " approved=" + approved
                + suffix
                + " ==========\n\n";
    }

    /**
     * 用户拒绝后返回给 Host 的固定取消说明（不再继续 HITL）。
     *
     * @return 取消文案
     */
    public static String cancelledByUser() {
        return "用户已拒绝执行 SQL，本子查询已取消。";
    }

    /**
     * null 转空串。
     *
     * @param s 原串
     * @return 非 null 字符串
     */
    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
