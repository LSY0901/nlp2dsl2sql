package org.example.nlp2dsl2sql.a2a;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link A2aSqlConfirmTexts} 单元测试。
 */
class A2aSqlConfirmTextsTest {

    /**
     * 仅 yes / 确认可通过，大小写与首尾空格忽略。
     */
    @Test
    void approvesYesAndConfirmIgnoringCaseAndSpaces() {
        assertTrue(A2aSqlConfirmTexts.isApproved("yes"));
        assertTrue(A2aSqlConfirmTexts.isApproved("YES"));
        assertTrue(A2aSqlConfirmTexts.isApproved(" 确认 "));
        assertFalse(A2aSqlConfirmTexts.isApproved("no"));
        assertFalse(A2aSqlConfirmTexts.isApproved("确认执行"));
        assertFalse(A2aSqlConfirmTexts.isApproved(null));
    }

    /**
     * 待确认 SSE 片段包含关键标记与 SQL。
     */
    @Test
    void formatsPendingMarkerWithSql() {
        String text = A2aSqlConfirmTexts.formatPending(
                "sid-1", "tc-1", "SELECT 1");
        assertTrue(text.contains("[SQL确认待审批]"));
        assertTrue(text.contains("sessionId: sid-1"));
        assertTrue(text.contains("toolCallId: tc-1"));
        assertTrue(text.contains("SELECT 1"));
        assertTrue(text.contains("[请输入 yes 或 确认]"));
    }

    /**
     * 拒绝后取消文案固定，且不得再含待审批标记。
     */
    @Test
    void cancelledByUserMessageHasNoPendingMarker() {
        String text = A2aSqlConfirmTexts.cancelledByUser();
        assertTrue(text.contains("已取消") || text.contains("拒绝"));
        assertFalse(text.contains(A2aSqlConfirmTexts.MARK_PENDING));
        assertFalse(text.contains(A2aSqlConfirmTexts.MARK_PROMPT));
    }
}
