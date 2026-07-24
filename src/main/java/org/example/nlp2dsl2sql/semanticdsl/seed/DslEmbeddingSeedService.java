package org.example.nlp2dsl2sql.semanticdsl.seed;

import org.example.nlp2dsl2sql.config.EmbeddingClient;
import org.example.nlp2dsl2sql.mapper.dsl.DslAttributeMapper;
import org.example.nlp2dsl2sql.mapper.dsl.DslDimensionMapper;
import org.example.nlp2dsl2sql.mapper.dsl.DslDimensionValueMapper;
import org.example.nlp2dsl2sql.mapper.dsl.DslEntityMapper;
import org.example.nlp2dsl2sql.mapper.dsl.DslFilterMapper;
import org.example.nlp2dsl2sql.mapper.dsl.DslMetricMapper;
import org.example.nlp2dsl2sql.mapper.dsl.DslSynonymMapper;
import org.example.nlp2dsl2sql.models.entity.dsl.DslAttribute;
import org.example.nlp2dsl2sql.models.entity.dsl.DslDimension;
import org.example.nlp2dsl2sql.models.entity.dsl.DslDimensionValue;
import org.example.nlp2dsl2sql.models.entity.dsl.DslEntity;
import org.example.nlp2dsl2sql.models.entity.dsl.DslMetric;
import org.example.nlp2dsl2sql.models.entity.dsl.DslSynonym;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 启动时为 DSL 元数据表中 embedding 为空的记录补全向量。
 * 改造：用 EmbeddingClient 替代 Spring AI EmbeddingModel。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DslEmbeddingSeedService {

    private final EmbeddingClient embeddingClient;
    private final DslEntityMapper dslEntityMapper;
    private final DslAttributeMapper dslAttributeMapper;
    private final DslMetricMapper dslMetricMapper;
    private final DslDimensionMapper dslDimensionMapper;
    private final DslDimensionValueMapper dslDimensionValueMapper;
    private final DslFilterMapper dslFilterMapper;
    private final DslSynonymMapper dslSynonymMapper;

    @PostConstruct
    public void initDslEmbeddings() {
        log.info("━━━ DSL Embedding 初始化开始 ━━━");
        try {
            seedEntities();
            seedAttributes();
            seedMetrics();
            seedDimensions();
            seedDimensionValues();
            seedSynonyms();
            log.info("━━━ DSL Embedding 初始化完成 ━━━");
        } catch (Exception e) {
            log.error("DSL Embedding 初始化失败: {}", e.getMessage(), e);
        }
    }

    private void seedEntities() {
        seedTable(
                "dsl_entity",
                dslEntityMapper::selectWithNullEmbedding,
                row -> joinText(row.getEntityName(), row.getDescription()),
                DslEntity::getId,
                dslEntityMapper::updateEmbedding
        );
    }

    private void seedAttributes() {
        seedTable(
                "dsl_attribute",
                dslAttributeMapper::selectWithNullEmbedding,
                row -> joinText(row.getAttributeName(), row.getDescription(), row.getEntityName()),
                DslAttribute::getId,
                dslAttributeMapper::updateEmbedding
        );
    }

    private void seedMetrics() {
        seedTable(
                "dsl_metric",
                dslMetricMapper::selectWithNullEmbedding,
                row -> joinText(
                        row.getMetricName(),
                        row.getMetricCode(),
                        row.getDescription(),
                        row.getExpression()),
                DslMetric::getId,
                dslMetricMapper::updateEmbedding
        );
    }

    private void seedDimensions() {
        seedTable(
                "dsl_dimension",
                dslDimensionMapper::selectWithNullEmbedding,
                row -> joinText(row.getDimensionName(), row.getDescription()),
                DslDimension::getId,
                dslDimensionMapper::updateEmbedding
        );
    }

    private void seedDimensionValues() {
        seedTable(
                "dsl_dimension_value",
                dslDimensionValueMapper::selectWithNullEmbedding,
                row -> joinText(
                        row.getValueName(),
                        row.getDimensionName(),
                        row.getDescription()),
                DslDimensionValue::getId,
                dslDimensionValueMapper::updateEmbedding
        );
    }

    private void seedSynonyms() {
        seedTable(
                "dsl_synonym",
                dslSynonymMapper::selectWithNullEmbedding,
                row -> joinText(
                        row.getSynonymText(),
                        row.getStandardName(),
                        row.getDescription()),
                DslSynonym::getId,
                dslSynonymMapper::updateEmbedding
        );
    }

    private <T> void seedTable(String tableName,
                               Supplier<List<T>> loader,
                               Function<T, String> textBuilder,
                               Function<T, Integer> idGetter,
                               BiConsumer<Integer, String> updater) {
        List<T> rows = loader.get();
        if (rows == null || rows.isEmpty()) {
            log.info("[{}] 无需补全 embedding", tableName);
            return;
        }
        int success = 0;
        for (T row : rows) {
            Integer id = idGetter.apply(row);
            String text = textBuilder.apply(row);
            if (text == null || text.isBlank()) {
                log.warn("[{}] id={} 文本为空，跳过", tableName, id);
                continue;
            }
            try {
                float[] vector = embeddingClient.embed(text);
                updater.accept(id, EmbeddingClient.toVectorStr(vector));
                success++;
            } catch (Exception e) {
                log.warn("[{}] id={} 生成 embedding 失败: {}", tableName, id, e.getMessage());
            }
        }
        log.info("[{}] 已补全 embedding {}/{} 条", tableName, success, rows.size());
    }

    private String joinText(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(part.trim());
        }
        return sb.toString();
    }
}
