package org.example.nlp2dsl2sql.semanticdsl.validator;

import org.example.nlp2dsl2sql.models.dto.dsl.IntentResult;
import org.example.nlp2dsl2sql.models.dto.dsl.SemanticFilter;
import org.example.nlp2dsl2sql.models.dto.dsl.SemanticQueryDSL;
import org.example.nlp2dsl2sql.models.entity.dsl.DslDimension;
import org.example.nlp2dsl2sql.models.entity.dsl.DslDimensionValue;
import org.example.nlp2dsl2sql.models.entity.dsl.DslMetric;
import org.example.nlp2dsl2sql.semanticdsl.metadata.IDslMetaDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@DisplayName("DSL 校验器")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SemanticDslValidatorTest {

    @Mock
    private IDslMetaDataService metaDataService;

    private SemanticDslValidator validator;

    @BeforeEach
    void setUp() {
        validator = new SemanticDslValidator(metaDataService);
        DslMetric metric = new DslMetric();
        metric.setMetricCode("avg_score");
        metric.setEntityCode("student");
        when(metaDataService.getMetricByCode("avg_score")).thenReturn(metric);

        DslDimension dimension = new DslDimension();
        dimension.setDimensionCode("grade");
        when(metaDataService.getAllDimensions()).thenReturn(List.of(dimension));
        when(metaDataService.getDimensionByCode("grade")).thenReturn(dimension);

        DslDimensionValue value = new DslDimensionValue();
        value.setDimensionCode("grade");
        value.setValueCode("G1");
        when(metaDataService.getDimensionValuesByCodes(List.of("grade")))
                .thenReturn(List.of(value));
    }

    @Test
    @DisplayName("空 DSL 校验不通过")
    void nullDslInvalid() {
        SemanticDslValidator.ValidationResult result = validator.validate(
                null, IntentResult.IntentType.METRIC_QUERY);

        assertThat(result.valid()).isFalse();
    }

    @Test
    @DisplayName("非业务意图直接通过")
    void nonBusinessPasses() {
        SemanticDslValidator.ValidationResult result = validator.validate(
                new SemanticQueryDSL(), IntentResult.IntentType.NON_BUSINESS);

        assertThat(result.valid()).isTrue();
    }

    @Test
    @DisplayName("指标查询缺 metric 不通过")
    void metricQueryWithoutMetricInvalid() {
        SemanticQueryDSL dsl = new SemanticQueryDSL();
        dsl.setEntity("student");

        SemanticDslValidator.ValidationResult result = validator.validate(
                dsl, IntentResult.IntentType.METRIC_QUERY);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("metric"));
    }

    @Test
    @DisplayName("未知指标不通过")
    void unknownMetricInvalid() {
        SemanticQueryDSL dsl = new SemanticQueryDSL();
        dsl.setMetric("no_such_metric");

        SemanticDslValidator.ValidationResult result = validator.validate(
                dsl, IntentResult.IntentType.METRIC_QUERY);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("no_such_metric"));
    }

    @Test
    @DisplayName("实体与指标归属不一致不通过")
    void entityMetricMismatchInvalid() {
        SemanticQueryDSL dsl = new SemanticQueryDSL();
        dsl.setMetric("avg_score");
        dsl.setEntity("teacher");

        SemanticDslValidator.ValidationResult result = validator.validate(
                dsl, IntentResult.IntentType.METRIC_QUERY);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("不匹配"));
    }

    @Test
    @DisplayName("未知维度不通过")
    void unknownDimensionInvalid() {
        SemanticQueryDSL dsl = new SemanticQueryDSL();
        dsl.setMetric("avg_score");
        dsl.setEntity("student");
        dsl.setDimensions(List.of("no_such_dim"));

        SemanticDslValidator.ValidationResult result = validator.validate(
                dsl, IntentResult.IntentType.DIMENSION_ANALYSIS);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("no_such_dim"));
    }

    @Test
    @DisplayName("未知过滤值不通过")
    void unknownFilterValueInvalid() {
        SemanticFilter filter = new SemanticFilter();
        filter.setDimension("grade");
        filter.setValue("G9");
        SemanticQueryDSL dsl = new SemanticQueryDSL();
        dsl.setMetric("avg_score");
        dsl.setEntity("student");
        dsl.setFilters(List.of(filter));

        SemanticDslValidator.ValidationResult result = validator.validate(
                dsl, IntentResult.IntentType.METRIC_QUERY);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("G9"));
    }

    @Test
    @DisplayName("合法 DSL 通过")
    void validDslPasses() {
        SemanticFilter filter = new SemanticFilter();
        filter.setDimension("grade");
        filter.setValue("G1");
        SemanticQueryDSL dsl = new SemanticQueryDSL();
        dsl.setMetric("avg_score");
        dsl.setEntity("student");
        dsl.setDimensions(List.of("grade"));
        dsl.setFilters(List.of(filter));

        SemanticDslValidator.ValidationResult result = validator.validate(
                dsl, IntentResult.IntentType.DIMENSION_ANALYSIS);

        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
    }
}
