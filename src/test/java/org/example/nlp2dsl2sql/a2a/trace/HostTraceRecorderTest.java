package org.example.nlp2dsl2sql.a2a.trace;

import org.example.nlp2dsl2sql.config.A2aHostTraceProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Trace 记录器：计时阶段")
class HostTraceRecorderTest {

    private HostTraceRecorder recorder;

    @BeforeEach
    void setUp() {
        recorder = new HostTraceRecorder(new A2aHostTraceProperties());
        recorder.start("s1", "q");
    }

    @Test
    @DisplayName("timedStep 关闭后回填耗时")
    void timedStepRecordsDuration() {
        try (var timer = recorder.timedStep("s1", "hitl-wait", null)) {
            timer.detail("approved");
        }

        HostTraceRecord.Step step = stepByName("hitl-wait");
        assertThat(step.getDurationMs()).isGreaterThanOrEqualTo(0);
        assertThat(step.getDetail()).isEqualTo("approved");
        assertThat(step.getTimeMs()).isPositive();
    }

    @Test
    @DisplayName("重复 close 无副作用")
    void doubleCloseIsSafe() {
        var timer = recorder.timedStep("s1", "phase", null);
        timer.close();
        long first = stepByName("phase").getDurationMs();
        timer.close();

        assertThat(stepByName("phase").getDurationMs()).isEqualTo(first);
        assertThat(stepsNamed("phase")).isEqualTo(1);
    }

    @Test
    @DisplayName("普通时间点 step 耗时为 0")
    void pointStepHasZeroDuration() {
        recorder.step("s1", "route-model", "tier=fast");

        assertThat(stepByName("route-model").getDurationMs()).isZero();
    }

    @Test
    @DisplayName("未知会话的计时句柄关闭不抛异常")
    void timedStepOnUnknownSessionIsSafe() {
        try (var timer = recorder.timedStep("no-such-session", "phase", null)) {
            timer.detail("x");
        }

        assertThat(recorder.get("no-such-session")).isNull();
    }

    private HostTraceRecord.Step stepByName(String name) {
        return recorder.get("s1").getSteps().stream()
                .filter(s -> name.equals(s.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("缺失 step: " + name));
    }

    private long stepsNamed(String name) {
        return recorder.get("s1").getSteps().stream()
                .filter(s -> name.equals(s.getName()))
                .count();
    }
}
