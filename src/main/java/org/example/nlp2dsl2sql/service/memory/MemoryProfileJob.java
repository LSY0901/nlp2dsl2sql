package org.example.nlp2dsl2sql.service.memory;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.UserMessage;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import lombok.extern.slf4j.Slf4j;
import org.example.nlp2dsl2sql.a2a.A2aHostModelRouter;
import org.example.nlp2dsl2sql.config.MemoryProperties;
import org.example.nlp2dsl2sql.mapper.memory.MemorySessionSummaryMapper;
import org.example.nlp2dsl2sql.mapper.memory.MemoryUserProfileMapper;
import org.example.nlp2dsl2sql.models.entity.memory.MemorySessionSummary;
import org.example.nlp2dsl2sql.models.entity.memory.MemoryUserProfile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户画像定时任务：按 {@code summary_seen} 游标增量消化新摘要，
 * 用 fast 档 LLM 把"旧画像 + 新摘要"提炼成整份新画像（覆盖式）并 upsert PG。
 * <p>
 * LLM 失败时保留旧画像（本轮跳过），下轮重试。
 */
@Slf4j
@Component
public class MemoryProfileJob {

    private final MemorySessionSummaryMapper summaryMapper;
    private final MemoryUserProfileMapper profileMapper;
    private final A2aHostModelRouter modelRouter;
    private final MemoryProperties memoryProperties;

    /**
     * @param summaryMapper    摘要 Mapper
     * @param profileMapper    画像 Mapper
     * @param modelRouter      模型路由（fast 档）
     * @param memoryProperties 记忆配置
     */
    public MemoryProfileJob(
            MemorySessionSummaryMapper summaryMapper,
            MemoryUserProfileMapper profileMapper,
            A2aHostModelRouter modelRouter,
            MemoryProperties memoryProperties) {
        this.summaryMapper = summaryMapper;
        this.profileMapper = profileMapper;
        this.modelRouter = modelRouter;
        this.memoryProperties = memoryProperties;
    }

    /**
     * 周期刷新全部用户画像。
     */
    @Scheduled(fixedDelayString =
            "${memory.longterm.profile.refresh-fixed-delay-ms:3600000}")
    public void refreshProfiles() {
        if (!memoryProperties.getLongterm().isJobsEnabled()) {
            return;
        }
        List<String> userIds;
        try {
            userIds = summaryMapper.selectDistinctUserIds();
        } catch (Exception e) {
            log.warn("[MemoryProfile] 用户查询失败: {}", e.getMessage());
            return;
        }
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        log.info("━━━ [MemoryProfile] 待检查用户 {} 个 ━━━", userIds.size());
        for (String userId : userIds) {
            try {
                refreshOne(userId);
            } catch (Exception e) {
                log.warn("[MemoryProfile] 单用户画像失败: userId={}, error={}",
                        userId, e.getMessage());
            }
        }
    }

    /**
     * 增量刷新单个用户画像。
     */
    private void refreshOne(String userId) {
        MemoryUserProfile profile = profileMapper.selectById(userId);
        long seen = (profile == null || profile.getSummarySeen() == null)
                ? 0L : profile.getSummarySeen();
        List<MemorySessionSummary> fresh = summaryMapper.selectList(
                new LambdaQueryWrapper<MemorySessionSummary>()
                        .eq(MemorySessionSummary::getUserId, userId)
                        .gt(MemorySessionSummary::getId, seen)
                        .orderByAsc(MemorySessionSummary::getId));
        if (fresh == null || fresh.isEmpty()) {
            return;
        }

        String oldProfile = profile == null ? null : profile.getProfileJson();
        String summariesText = fresh.stream()
                .map(s -> "- " + s.getSummary())
                .collect(Collectors.joining("\n"));
        String newProfileJson = callFastLlmDistill(oldProfile, summariesText);
        if (newProfileJson == null) {
            log.warn("[MemoryProfile] LLM 提炼失败，保留旧画像: userId={}", userId);
            return;
        }

        MemoryUserProfile update = new MemoryUserProfile();
        update.setUserId(userId);
        update.setProfileJson(newProfileJson);
        update.setSummarySeen(fresh.get(fresh.size() - 1).getId());
        profileMapper.upsert(update);
        log.info("[MemoryProfile] 画像更新 userId={}, digested={}",
                userId, fresh.size());
    }

    /**
     * fast 档 LLM 画像提炼；输出经 JSON 校验，失败返回 null。测试子类覆写。
     */
    protected String callFastLlmDistill(String oldProfile, String summariesText) {
        String prompt = """
                你是用户画像维护器。根据旧画像与新增会话摘要，输出整份新画像 JSON（覆盖式）。
                schema：{"focusAreas":[],"preferredMetrics":[],"preferredDimensions":[],"notes":""}
                规则：四字段均为中文短词或语义层 code；新增证据优先；无新证据的字段沿用旧值；
                notes 不超过 50 字；只输出 JSON 本体，禁止 markdown 代码块。
                【旧画像】%s
                【新增摘要】
                %s
                """.formatted(oldProfile == null ? "无" : oldProfile, summariesText);

        OpenAIChatModel fastModel = modelRouter.resolveTier("fast");
        StringBuilder sb = new StringBuilder();
        try {
            fastModel.stream(List.of(new UserMessage(prompt)), List.of(), null)
                    .doOnNext(resp -> {
                        if (resp.getContent() != null) {
                            for (var block : resp.getContent()) {
                                if (block instanceof TextBlock tb) {
                                    sb.append(tb.getText());
                                }
                            }
                        }
                    })
                    .blockLast(Duration.ofSeconds(30));
        } catch (Exception e) {
            log.warn("[MemoryProfile] LLM 调用失败: {}", e.getMessage());
            return null;
        }
        return validateProfileJson(sb.toString());
    }

    /**
     * 校验并归一化画像 JSON：截花括号 → 解析 → 重新序列化；失败返回 null。
     */
    private String validateProfileJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            int start = raw.indexOf('{');
            int end = raw.lastIndexOf('}');
            if (start < 0 || end <= start) {
                return null;
            }
            var parsed = JSON.parseObject(raw.substring(start, end + 1));
            return parsed == null ? null : parsed.toJSONString();
        } catch (Exception e) {
            log.warn("[MemoryProfile] 画像 JSON 校验失败: {}", e.getMessage());
            return null;
        }
    }
}
