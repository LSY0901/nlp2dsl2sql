package org.example.nlp2dsl2sql.semanticdsl.validator;

import org.example.nlp2dsl2sql.semanticdsl.metadata.IDslMetaDataService;
import org.example.nlp2dsl2sql.semanticdsl.metadata.entity.DslDimension;
import org.example.nlp2dsl2sql.semanticdsl.metadata.entity.DslDimensionValue;
import org.example.nlp2dsl2sql.semanticdsl.metadata.entity.DslMetric;
import org.example.nlp2dsl2sql.semanticdsl.metadata.entity.DslMetricDimension;
import org.example.nlp2dsl2sql.semanticdsl.model.IntentResult;
import org.example.nlp2dsl2sql.semanticdsl.model.SemanticFilter;
import org.example.nlp2dsl2sql.semanticdsl.model.SemanticQueryDSL;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class SemanticDslValidator {

    private final IDslMetaDataService metaDataService;

    public ValidationResult validate(SemanticQueryDSL dsl, IntentResult.IntentType intent) {
        List<String> errors = new ArrayList<>();

        if (dsl == null) {
            return new ValidationResult(false, List.of("DSL为空"));
        }
        if (intent == IntentResult.IntentType.NON_BUSINESS) {
            return new ValidationResult(true, errors);
        }

        validateByIntent(dsl, intent, errors);
        validateMetricAndEntity(dsl, errors);
        validateDimensions(dsl, errors);
        warnMetricDimensionCompat(dsl);
        validateFilters(dsl, errors);

        boolean valid = errors.isEmpty();
        log.info("DSL校验结果: valid={}, errors={}", valid, errors);
        return new ValidationResult(valid, errors);
    }

    private void validateByIntent(SemanticQueryDSL dsl, IntentResult.IntentType intent,
                                  List<String> errors) {
        switch (intent) {
            case METRIC_QUERY -> {
                if (isBlank(dsl.getMetric())) {
                    errors.add("METRIC_QUERY意图必须指定metric");
                }
            }
            case DIMENSION_ANALYSIS -> {
                if (isBlank(dsl.getMetric())) {
                    errors.add("DIMENSION_ANALYSIS意图必须指定metric");
                }
                if (dsl.getDimensions() == null || dsl.getDimensions().isEmpty()) {
                    errors.add("DIMENSION_ANALYSIS意图必须指定至少一个dimension");
                }
            }
            case DETAIL_QUERY -> {
                if (isBlank(dsl.getEntity())) {
                    errors.add("DETAIL_QUERY意图必须指定entity");
                }
            }
            default -> {
            }
        }
    }

    private void validateMetricAndEntity(SemanticQueryDSL dsl, List<String> errors) {
        if (isBlank(dsl.getMetric())) {
            return;
        }
        DslMetric metric = metaDataService.getMetricByCode(dsl.getMetric());
        if (metric == null) {
            errors.add("指标不存在: " + dsl.getMetric());
            return;
        }
        if (dsl.getEntity() != null && !dsl.getEntity().equals(metric.getEntityCode())) {
            errors.add("实体与指标不匹配: 指标[" + dsl.getMetric() + "]属于实体["
                    + metric.getEntityCode() + "]，但DSL中实体为[" + dsl.getEntity() + "]");
        }
    }

    private void validateDimensions(SemanticQueryDSL dsl, List<String> errors) {
        if (dsl.getDimensions() == null) {
            return;
        }
        Set<String> validDimensionCodes = new HashSet<>();
        for (DslDimension dim : metaDataService.getAllDimensions()) {
            validDimensionCodes.add(dim.getDimensionCode());
        }
        for (String dimCode : dsl.getDimensions()) {
            if (!validDimensionCodes.contains(dimCode)) {
                errors.add("维度不存在: " + dimCode);
            }
        }
    }

    private void warnMetricDimensionCompat(SemanticQueryDSL dsl) {
        if (isBlank(dsl.getMetric()) || dsl.getDimensions() == null) {
            return;
        }
        for (String dimCode : dsl.getDimensions()) {
            DslMetricDimension relation =
                    metaDataService.getMetricDimension(dsl.getMetric(), dimCode);
            if (relation == null) {
                log.warn("指标与维度可能不兼容或未配置: metric={}, dimension={}",
                        dsl.getMetric(), dimCode);
            }
        }
    }

    private void validateFilters(SemanticQueryDSL dsl, List<String> errors) {
        if (dsl.getFilters() == null) {
            return;
        }
        for (SemanticFilter filter : dsl.getFilters()) {
            DslDimension dim = metaDataService.getDimensionByCode(filter.getDimension());
            if (dim == null) {
                errors.add("过滤维度不存在: " + filter.getDimension());
                continue;
            }
            List<DslDimensionValue> values =
                    metaDataService.getDimensionValuesByCodes(List.of(filter.getDimension()));
            boolean valueValid = values.stream()
                    .anyMatch(v -> v.getValueCode().equals(filter.getValue()));
            if (!valueValid) {
                errors.add("过滤值不存在: dimension=" + filter.getDimension()
                        + ", value=" + filter.getValue());
            }
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record ValidationResult(boolean valid, List<String> errors) {}
}
