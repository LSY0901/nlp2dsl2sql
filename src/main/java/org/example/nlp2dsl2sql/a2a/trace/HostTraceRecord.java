package org.example.nlp2dsl2sql.a2a.trace;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 单次 A2A Host 请求的可观测 trace 记录。
 */
@Data
public class HostTraceRecord {

    /** 状态：运行中 */
    public static final String STATUS_RUNNING = "RUNNING";

    /** 状态：已完成 */
    public static final String STATUS_COMPLETED = "COMPLETED";

    /** 状态：失败 */
    public static final String STATUS_FAILED = "FAILED";

    /** 状态：已取消 */
    public static final String STATUS_CANCELLED = "CANCELLED";

    /** 会话 ID */
    private String sessionId;

    /** 用户问题 */
    private String question;

    /** 复杂度档位（fast / strong） */
    private String tier;

    /** 选中模型名 */
    private String modelName;

    /** 运行状态 */
    private String status = STATUS_RUNNING;

    /** 开始时间毫秒 */
    private long startTimeMs;

    /** 结束时间毫秒 */
    private long endTimeMs;

    /** 总耗时毫秒 */
    private long durationMs;

    /** 最终待执行 SQL（HITL 提取） */
    private String sql;

    /** HITL 是否批准 */
    private Boolean hitlApproved;

    /** 失败/取消原因 */
    private String error;

    /** 阶段时间线 */
    private List<Step> steps = new ArrayList<>();

    /** 单调递增序号（仅用于同毫秒内稳定排序，不对外展示） */
    @JsonIgnore
    private long seq;

    /**
     * 单个执行阶段。
     */
    @Data
    public static class Step {
        /** 阶段名，如 route-model / call_sql_agent / sql_confirm */
        private String name;

        /** 阶段详情 */
        private String detail;

        /** 记录时间毫秒 */
        private long timeMs;
    }
}
