package org.example.nlp2dsl2sql.a2a;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("HITL 确认注册表")
class A2aSqlConfirmRegistryTest {

    private A2aSqlConfirmRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new A2aSqlConfirmRegistry();
    }

    @Test
    @DisplayName("批准完成返回 true，future 结果为 true")
    void completeApproved() {
        PendingSqlConfirm pending = new PendingSqlConfirm("s1", null, null);
        registry.put(pending);

        assertThat(registry.complete("s1", true)).isTrue();
        assertThat(pending.getDecision()).isCompletedWithValue(true);
    }

    @Test
    @DisplayName("拒绝完成返回 true，future 结果为 false")
    void completeDenied() {
        PendingSqlConfirm pending = new PendingSqlConfirm("s1", null, null);
        registry.put(pending);

        assertThat(registry.complete("s1", false)).isTrue();
        assertThat(pending.getDecision()).isCompletedWithValue(false);
    }

    @Test
    @DisplayName("未知会话完成返回 false")
    void completeUnknownSession() {
        assertThat(registry.complete("no-such-session", true)).isFalse();
    }

    @Test
    @DisplayName("同会话覆盖挂起时，旧挂起按拒绝完成")
    void overwriteCompletesOldAsDenied() {
        PendingSqlConfirm oldPending = new PendingSqlConfirm("s1", null, null);
        registry.put(oldPending);
        PendingSqlConfirm newPending = new PendingSqlConfirm("s1", null, null);
        registry.put(newPending);

        assertThat(oldPending.getDecision()).isCompletedWithValue(false);
        assertThat(newPending.getDecision()).isNotDone();
        assertThat(registry.get("s1")).isSameAs(newPending);
    }

    @Test
    @DisplayName("过期挂起在访问时按拒绝完成并清理")
    void expiredPendingCompletedAsDenied() throws Exception {
        PendingSqlConfirm pending = new PendingSqlConfirm("s1", null, null);
        backdate(pending, A2aSqlConfirmRegistry.TIMEOUT_MS + 1000);
        registry.put(pending);

        assertThat(registry.get("s1")).isNull();
        assertThat(pending.getDecision()).isCompletedWithValue(false);
    }

    @Test
    @DisplayName("未过期挂起不受清理影响")
    void freshPendingSurvivesCleanup() {
        PendingSqlConfirm pending = new PendingSqlConfirm("s1", null, null);
        registry.put(pending);

        assertThat(registry.get("s1")).isSameAs(pending);
        assertThat(pending.getDecision()).isNotDone();
    }

    private static void backdate(PendingSqlConfirm pending, long ageMs) throws Exception {
        Field field = PendingSqlConfirm.class.getDeclaredField("createdAtMs");
        field.setAccessible(true);
        field.setLong(pending, System.currentTimeMillis() - ageMs);
    }
}
