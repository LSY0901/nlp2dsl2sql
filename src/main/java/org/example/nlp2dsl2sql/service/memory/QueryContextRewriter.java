package org.example.nlp2dsl2sql.service.memory;

import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.UserMessage;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import lombok.extern.slf4j.Slf4j;
import org.example.nlp2dsl2sql.a2a.A2aHostModelRouter;
import org.example.nlp2dsl2sql.config.MemoryProperties;
import org.example.nlp2dsl2sql.models.dto.dsl.SemanticFilter;
import org.example.nlp2dsl2sql.models.dto.dsl.SemanticQueryDSL;
import org.example.nlp2dsl2sql.models.dto.dsl.SemanticSessionSnapshot;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 多轮问数上下文消歧与改写器：
 * 规则预检 + Fast LLM 改写，将指代词/短句追问补全为自包含完整查询。
 */
@Slf4j
@Component
public class QueryContextRewriter {

    /**
     * 追问/省略特征词：代词开头、替换下钻动词、对比省略尾。
     * <p>
     * 只收真正无法独立成句的表达。像"按""其他""最高""最低"这类本身就是完整
     * 问句起手的词不列入，避免为自包含问句白花一次 Fast LLM 调用。
     */
    private static final Pattern ELLIPSIS_PATTERN = Pattern.compile(
            "^(那|这个|这些|那些|其中|换成|改成|如果换成|改为|只要|只看|除了|排除|相比|同上|同理)"
                    + "|(呢[？?]?$)"
                    + "|(的话[？?]?$)");

    private final A2aHostModelRouter modelRouter;
    private final MemoryProperties memoryProperties;

    public QueryContextRewriter(
            A2aHostModelRouter modelRouter,
            MemoryProperties memoryProperties) {
        this.modelRouter = modelRouter;
        this.memoryProperties = memoryProperties;
    }

    /**
     * 如有需要，对当前子问题执行上下文补全改写（不注入画像）。
     *
     * @param currentQuery     当前子问题
     * @param previousSnapshot 上一轮语义快照
     * @return 改写后的自包含完整查询（若无需改写或改写失败则返回原句）
     */
    public String rewriteIfNeeded(String currentQuery, SemanticSessionSnapshot previousSnapshot) {
        return rewriteIfNeeded(currentQuery, previousSnapshot, null);
    }

    /**
     * 如有需要，对当前子问题执行上下文补全改写（注入用户画像增强改写质量）。
     *
     * @param currentQuery     当前子问题
     * @param previousSnapshot 上一轮语义快照
     * @param profileJson      用户画像 JSON，可为 null（等价于不注入）
     * @return 改写后的自包含完整查询（若无需改写或改写失败则返回原句）
     */
    public String rewriteIfNeeded(String currentQuery,
                                  SemanticSessionSnapshot previousSnapshot,
                                  String profileJson) {
        if (currentQuery == null || currentQuery.isBlank()) {
            return currentQuery;
        }
        if (previousSnapshot == null || previousSnapshot.getDsl() == null) {
            return currentQuery;
        }

        String trimmed = currentQuery.trim();
        if (!shouldRewrite(trimmed)) {
            log.debug("[Rewriter] 判定为独立完整查询，无需改写: {}", trimmed);
            return trimmed;
        }

        log.info("[Rewriter] 检测到多轮追问/省略特征，触发 Fast LLM 上下文改写: '{}'", trimmed);
        try {
            String rewritten = callFastLlmRewrite(trimmed, previousSnapshot, profileJson);
            if (rewritten != null && !rewritten.isBlank()) {
                log.info("[Rewriter] 改写成功: '{}' -> '{}'", trimmed, rewritten);
                return rewritten;
            }
        } catch (Exception e) {
            log.warn("[Rewriter] 上下文改写异常，降级回退原句: {}", e.getMessage());
        }
        return trimmed;
    }

    /**
     * 规则预检：判断是否符合追问/省略特征。
     * <p>
     * 纯词表判定，不看长度——短问句完全可以是自包含的（"三年级多少人"），
     * 长度启发式会把这类问句误送去改写。
     *
     * @param query 当前问句
     * @return 命中追问/省略特征返回 true
     */
    private boolean shouldRewrite(String query) {
        return ELLIPSIS_PATTERN.matcher(query).find();
    }

    /**
     * 画像注入截断：超过 {@code memory.longterm.profile.max-chars} 时截断。
     */
    private String truncateProfile(String profileJson) {
        int maxChars = memoryProperties.getLongterm().getProfile().getMaxChars();
        return profileJson.length() > maxChars
                ? profileJson.substring(0, maxChars)
                : profileJson;
    }

    /**
     * 调用 Fast 档位模型进行 1-shot 轻量改写（画像非空时注入前序上下文）
     */
    private String callFastLlmRewrite(
            String query, SemanticSessionSnapshot snapshot, String profileJson) {
        OpenAIChatModel fastModel = modelRouter.resolveTier("fast");
        SemanticQueryDSL dsl = snapshot.getDsl();

        StringBuilder contextDesc = new StringBuilder();
        if (profileJson != null && !profileJson.isBlank()) {
            contextDesc.append("用户画像: ")
                    .append(truncateProfile(profileJson))
                    .append("\n");
        }
        contextDesc.append("上一轮问题: ").append(snapshot.getQuestion()).append("\n");
        if (dsl.getMetric() != null) {
            contextDesc.append("上一轮涉及指标: ").append(dsl.getMetric()).append("\n");
        }
        if (dsl.getEntity() != null) {
            contextDesc.append("上一轮涉及实体: ").append(dsl.getEntity()).append("\n");
        }
        if (dsl.getDimensions() != null && !dsl.getDimensions().isEmpty()) {
            contextDesc.append("上一轮分析维度: ").append(String.join(",", dsl.getDimensions())).append("\n");
        }
        if (dsl.getFilters() != null && !dsl.getFilters().isEmpty()) {
            contextDesc.append("上一轮过滤条件: ");
            for (SemanticFilter f : dsl.getFilters()) {
                contextDesc.append(f.getDimension()).append("=").append(f.getValue()).append(" ");
            }
            contextDesc.append("\n");
        }

        String prompt = """
                你是一个自然语言数据查询上下文补全助手。
                【前序上下文】
                %s
                【当前用户问题】
                %s

                【任务要求】
                请结合上一轮的上下文信息，将当前问题的省略、代词或指代消解补全为一句自包含、完整、通顺的查询语句。
                - 如果当前问题是针对上一轮的追问（如切换地区、年份、下钻维度、过滤调整），请继承上一轮的核心指标与未冲突的条件；
                - 如果当前问题已经自包含且与上一轮无关，请直接输出当前原句；
                - 必须直接输出改写后的一句话，严禁包含任何前缀、解释、标点引号或问候语。
                """.formatted(contextDesc.toString(), query);

        StringBuilder sb = new StringBuilder();
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
                .blockLast(Duration.ofSeconds(10));

        String result = sb.toString().trim();
        // 清理可能被模型包裹的两端引号
        if (result.startsWith("\"") && result.endsWith("\"") && result.length() > 2) {
            result = result.substring(1, result.length() - 1).trim();
        }
        if (result.startsWith("“") && result.endsWith("”") && result.length() > 2) {
            result = result.substring(1, result.length() - 1).trim();
        }
        return result.isEmpty() ? query : result;
    }
}
