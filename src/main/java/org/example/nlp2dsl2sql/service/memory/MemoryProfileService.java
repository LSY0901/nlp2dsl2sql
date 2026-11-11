package org.example.nlp2dsl2sql.service.memory;

import lombok.extern.slf4j.Slf4j;
import org.example.nlp2dsl2sql.config.MemoryProperties;
import org.example.nlp2dsl2sql.mapper.memory.MemorySessionSummaryMapper;
import org.example.nlp2dsl2sql.mapper.memory.MemoryUserProfileMapper;
import org.example.nlp2dsl2sql.models.entity.memory.MemorySessionSummary;
import org.example.nlp2dsl2sql.models.entity.memory.MemoryUserProfile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 用户长期记忆读取服务：长期记忆（画像 + 会话压缩摘要）读写一律 PG（短期才走 Redis）。
 * <p>
 * 消费端：
 * <ol>
 *   <li>{@link QueryContextRewriter}：追问消歧改写时注入用户画像；</li>
 *   <li>{@link org.example.nlp2dsl2sql.service.impl.A2aHostServiceImpl}：Host 启动时将用户画像与近期会话摘要注入系统提示词尾部。</li>
 * </ol>
 * 无记录或存储异常一律静默降级返回 null / 空串，绝不阻断主流程。
 */
@Slf4j
@Component
public class MemoryProfileService {

    private final MemoryUserProfileMapper profileMapper;
    private final MemorySessionSummaryMapper summaryMapper;
    private final MemoryProperties memoryProperties;

    /**
     * 兼容单参构造（仅供单测便捷构造）。
     *
     * @param profileMapper 画像 Mapper
     */
    public MemoryProfileService(MemoryUserProfileMapper profileMapper) {
        this(profileMapper, null, new MemoryProperties());
    }

    /**
     * @param profileMapper    画像 Mapper
     * @param summaryMapper    会话摘要 Mapper
     * @param memoryProperties 记忆配置
     */
    @Autowired
    public MemoryProfileService(
            MemoryUserProfileMapper profileMapper,
            MemorySessionSummaryMapper summaryMapper,
            MemoryProperties memoryProperties) {
        this.profileMapper = profileMapper;
        this.summaryMapper = summaryMapper;
        this.memoryProperties = memoryProperties != null ? memoryProperties : new MemoryProperties();
    }

    /**
     * 读取画像 JSON（PG 直读）。
     *
     * @param userId 用户 ID
     * @return 画像 JSON 字符串；无画像或存储异常返回 null
     */
    public String getProfile(String userId) {
        if (userId == null || userId.isBlank() || profileMapper == null) {
            return null;
        }
        try {
            MemoryUserProfile profile = profileMapper.selectById(userId.trim());
            if (profile == null || profile.getProfileJson() == null) {
                return null;
            }
            return profile.getProfileJson();
        } catch (Exception e) {
            log.warn("[MemoryProfile] 画像读取 PG 失败: userId={}, error={}",
                    userId, e.getMessage());
            return null;
        }
    }

    /**
     * 读取指定用户最近 N 条会话压缩摘要（PG 直读）。
     *
     * @param userId 用户 ID
     * @param limit  最大条数
     * @return 摘要列表；无记录或异常返回空列表
     */
    public List<MemorySessionSummary> getRecentSummaries(String userId, int limit) {
        if (userId == null || userId.isBlank() || summaryMapper == null || limit <= 0) {
            return List.of();
        }
        try {
            List<MemorySessionSummary> list = summaryMapper.selectRecentByUserId(userId.trim(), limit);
            return list == null ? List.of() : list;
        } catch (Exception e) {
            log.warn("[MemoryProfile] 近期会话摘要读取 PG 失败: userId={}, error={}",
                    userId, e.getMessage());
            return List.of();
        }
    }

    /**
     * 组装注入 A2A Host 系统提示词尾部的跨会话长期记忆段落（用户画像 + 最近 K 条历史会话摘要）。
     * <p>
     * 若画像与摘要均为空，返回空字符串 {@code ""}。
     *
     * @param userId 用户 ID
     * @return 格式化的长期记忆 Markdown 段落，或空串
     */
    public String buildHostLongTermMemoryContext(String userId) {
        if (userId == null || userId.isBlank()) {
            return "";
        }
        String profileJson = getProfile(userId);
        int recentCount = memoryProperties.getLongterm().getSummary().getInjectRecentCount();
        List<MemorySessionSummary> summaries = getRecentSummaries(userId, recentCount);

        boolean hasProfile = profileJson != null && !profileJson.isBlank();
        StringBuilder sumBuf = new StringBuilder();
        if (!summaries.isEmpty()) {
            int maxSummaryChars = memoryProperties.getLongterm().getSummary().getInjectMaxChars();
            for (MemorySessionSummary s : summaries) {
                if (s == null || s.getSummary() == null || s.getSummary().isBlank()) {
                    continue;
                }
                String line = "  * " + s.getSummary().trim() + "\n";
                if (maxSummaryChars > 0 && sumBuf.length() + line.length() > maxSummaryChars) {
                    int remain = maxSummaryChars - sumBuf.length();
                    if (remain > 4) {
                        sumBuf.append(line, 0, remain);
                    }
                    break;
                }
                sumBuf.append(line);
            }
        }
        boolean hasSummaries = !sumBuf.isEmpty();
        if (!hasProfile && !hasSummaries) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("## 用户长期记忆（跨会话背景，仅供参考指代与偏好；数据问题仍必须调用 call_sql_agent 实时查询）\n");
        if (hasProfile) {
            int maxProfileChars = memoryProperties.getLongterm().getProfile().getMaxChars();
            String truncatedProfile = (maxProfileChars > 0 && profileJson.length() > maxProfileChars)
                    ? profileJson.substring(0, maxProfileChars)
                    : profileJson;
            sb.append("- 用户画像: ").append(truncatedProfile).append("\n");
        }
        if (hasSummaries) {
            sb.append("- 近期会话摘要:\n").append(sumBuf);
        }
        return sb.toString().trim();
    }
}
