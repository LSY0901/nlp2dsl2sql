package org.example.nlp2dsl2sql.a2a.trace;

import org.example.nlp2dsl2sql.config.A2aHostTraceProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link HostTraceRecorder} 单元测试。
 */
class HostTraceRecorderTest {

    private HostTraceRecorder recorder;

    /**
     * 构造带默认配置的记录器。
     */
    @BeforeEach
    void setUp() {
        A2aHostTraceProperties properties = new A2aHostTraceProperties();
        properties.setMaxRecords(200);
        properties.setTtlMs(3_600_000L);
        recorder = new HostTraceRecorder(properties);
    }

    /**
     * start 后记录为 RUNNING，含问题与 sessionId。
     */
    @Test
    void startCreatesRunningRecord() {
        recorder.start("s1", "六年级最高分是多少");
        HostTraceRecord record = recorder.get("s1");
        assertNotNull(record);
        assertEquals("s1", record.getSessionId());
        assertEquals("六年级最高分是多少", record.getQuestion());
        assertEquals(HostTraceRecord.STATUS_RUNNING, record.getStatus());
        assertTrue(record.getStartTimeMs() > 0);
    }

    /**
     * recordModel 记录档位与模型，并追加 route-model 步骤。
     */
    @Test
    void recordModelStoresTierAndModel() {
        recorder.start("s1", "问题");
        recorder.recordModel("s1", "fast", "deepseek-v4-flash");
        HostTraceRecord record = recorder.get("s1");
        assertEquals("fast", record.getTier());
        assertEquals("deepseek-v4-flash", record.getModelName());
        assertEquals("route-model", record.getSteps().get(0).getName());
    }

    /**
     * step 追加阶段时间线。
     */
    @Test
    void stepAppendsTimeline() {
        recorder.start("s1", "问题");
        recorder.step("s1", "call_sql_agent", "各科平均分");
        recorder.step("s1", "call_sop_agent", "奖励政策");
        List<HostTraceRecord.Step> steps = recorder.get("s1").getSteps();
        assertEquals(2, steps.size());
        assertEquals("call_sql_agent", steps.get(0).getName());
        assertEquals("call_sop_agent", steps.get(1).getName());
    }

    /**
     * finish 后状态与耗时正确。
     */
    @Test
    void finishSetsStatusAndDuration() {
        recorder.start("s1", "问题");
        recorder.finish("s1", HostTraceRecord.STATUS_COMPLETED);
        HostTraceRecord record = recorder.get("s1");
        assertEquals(HostTraceRecord.STATUS_COMPLETED, record.getStatus());
        assertTrue(record.getDurationMs() >= 0);
        assertTrue(record.getEndTimeMs() >= record.getStartTimeMs());
    }

    /**
     * fail 记录失败状态与原因。
     */
    @Test
    void failSetsError() {
        recorder.start("s1", "问题");
        recorder.fail("s1", HostTraceRecord.STATUS_FAILED, "boom");
        HostTraceRecord record = recorder.get("s1");
        assertEquals(HostTraceRecord.STATUS_FAILED, record.getStatus());
        assertEquals("boom", record.getError());
    }

    /**
     * listRecent 按开始时间倒序。
     */
    @Test
    void listRecentSortedDescending() {
        recorder.start("s1", "旧");
        recorder.start("s2", "新");
        List<HostTraceRecord> list = recorder.listRecent();
        assertEquals("s2", list.get(0).getSessionId());
        assertEquals("s1", list.get(1).getSessionId());
    }

    /**
     * 超上限时淘汰最旧的记录。
     */
    @Test
    void evictsOldestBeyondMaxRecords() {
        A2aHostTraceProperties properties = new A2aHostTraceProperties();
        properties.setMaxRecords(3);
        properties.setTtlMs(3_600_000L);
        HostTraceRecorder small = new HostTraceRecorder(properties);
        small.start("s1", "1");
        small.start("s2", "2");
        small.start("s3", "3");
        small.start("s4", "4");
        assertNull(small.get("s1"));
        assertNotNull(small.get("s2"));
        assertNotNull(small.get("s3"));
        assertNotNull(small.get("s4"));
    }

    /**
     * 过期记录被清理。
     */
    @Test
    void evictsExpiredRecords() {
        A2aHostTraceProperties properties = new A2aHostTraceProperties();
        properties.setMaxRecords(200);
        properties.setTtlMs(1_000L);
        HostTraceRecorder ttl = new HostTraceRecorder(properties);
        ttl.start("s1", "旧");
        HostTraceRecord record = ttl.get("s1");
        record.setStartTimeMs(System.currentTimeMillis() - 10_000);
        assertNull(ttl.get("s1"));
    }

    /**
     * 不存在的会话取 null，且不抛异常。
     */
    @Test
    void missingSessionReturnsNull() {
        assertNull(recorder.get("nope"));
        recorder.step("nope", "x", "y");
        recorder.sql("nope", "select 1");
        recorder.hitl("nope", true, null);
        recorder.finish("nope", HostTraceRecord.STATUS_COMPLETED);
        assertNull(recorder.get("nope"));
    }
}
