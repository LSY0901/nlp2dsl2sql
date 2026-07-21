package org.example.nlp2dsl2sql.semanticdsl.tools;

import org.example.nlp2dsl2sql.semanticdsl.model.DslCandidate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 多 Agent 工具：候选元数据上下文构建。
 * <p>
 * 将 DslCandidate 转为 LLM 可理解的文本上下文，供 DSL 生成 Agent 使用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CandidateContextTool {

    /**
     * 将候选元数据构建为 LLM 可读的上下文文本。
     *
     * @param candidate 检索候选集
     * @return 上下文文本
     */
    public String buildCandidateContext(DslCandidate candidate) {
        StringBuilder context = new StringBuilder();
        context.append("可用指标:\n");
        if (candidate.getMetrics() != null) {
            candidate.getMetrics().forEach(m -> context.append("- ").append(m.getMetricCode())
                    .append("(").append(m.getMetricName()).append("): ")
                    .append(m.getDescription()).append("\n"));
        }
        context.append("\n可用维度:\n");
        if (candidate.getDimensions() != null) {
            candidate.getDimensions().forEach(d -> context.append("- ").append(d.getDimensionCode())
                    .append("(").append(d.getDimensionName()).append("): ")
                    .append(d.getDescription()).append("\n"));
        }
        context.append("\n可用实体:\n");
        if (candidate.getEntities() != null) {
            candidate.getEntities().forEach(e -> context.append("- ").append(e.getEntityCode())
                    .append("(").append(e.getEntityName()).append(")\n"));
        }
        context.append("\n维度值:\n");
        if (candidate.getDimensionValues() != null) {
            candidate.getDimensionValues().forEach(v -> context.append("- ")
                    .append(v.getDimensionCode()).append(".")
                    .append(v.getValueCode()).append(" = ")
                    .append(v.getValueName()).append("\n"));
        }
        context.append("\n同义词提示:\n");
        if (candidate.getSynonyms() != null) {
            candidate.getSynonyms().forEach(s -> context.append("- ")
                    .append(s.getSynonymText())
                    .append(" → ").append(s.getObjectType())
                    .append(":").append(s.getObjectCode())
                    .append("(").append(s.getStandardName()).append(")\n"));
        }
        return context.toString();
    }

    /**
     * 从候选集中提取所有实体编码。
     *
     * @param candidate 检索候选集
     * @return 实体编码列表
     */
    public List<String> extractEntityCodes(DslCandidate candidate) {
        if (candidate.getEntities() == null) {
            return List.of();
        }
        return candidate.getEntities().stream()
                .map(e -> e.getEntityCode())
                .toList();
    }
}
