package org.example.nlp2dsl2sql.semanticdsl.enricher;

import org.example.nlp2dsl2sql.models.dto.dsl.DslCandidate;
import org.example.nlp2dsl2sql.models.dto.dsl.EnrichedQueryDSL;
import org.example.nlp2dsl2sql.models.dto.dsl.SemanticFilter;
import org.example.nlp2dsl2sql.models.dto.dsl.SemanticQueryDSL;
import org.example.nlp2dsl2sql.models.entity.dsl.DslDimension;
import org.example.nlp2dsl2sql.models.entity.dsl.DslDimensionValue;
import org.example.nlp2dsl2sql.models.entity.dsl.DslEntity;
import org.example.nlp2dsl2sql.models.entity.dsl.DslMetric;
import org.example.nlp2dsl2sql.models.entity.dsl.DslRelation;
import org.example.nlp2dsl2sql.semanticdsl.metadata.IDslMetaDataService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DSL 富化器")
@ExtendWith(MockitoExtension.class)
class SemanticDslEnricherTest {

    @Mock
    private IDslMetaDataService metaDataService;

    @Test
    @DisplayName("指标查询富化出主表、聚合列与参数化过滤")
    void enrichMetricQuery() {
        SemanticDslEnricher enricher = new SemanticDslEnricher(metaDataService);

        SemanticFilter filter = new SemanticFilter();
        filter.setDimension("grade");
        filter.setValue("G1");
        SemanticQueryDSL dsl = new SemanticQueryDSL();
        dsl.setMetric("avg_score");
        dsl.setEntity("student");
        dsl.setDimensions(List.of("grade"));
        dsl.setFilters(List.of(filter));

        EnrichedQueryDSL enriched = enricher.enrich(dsl, candidate());

        assertThat(enriched.getMainPhysicalTable()).isEqualTo("t_student");
        assertThat(enriched.getSelectColumns()).hasSize(2);
        assertThat(enriched.getSelectColumns().get(0).getExpression())
                .isEqualTo("AVG(score)");
        assertThat(enriched.getSelectColumns().get(0).getAlias())
                .isEqualTo("avg_score");
        assertThat(enriched.getGroupBy()).containsExactly("t_student.grade");
        assertThat(enriched.getJoins()).isEmpty();
        assertThat(enriched.getWhereConditions()).hasSize(1);
        assertThat(enriched.getWhereConditions().get(0).getExpression())
                .isEqualTo("t_student.grade = ?");
        assertThat(enriched.getWhereConditions().get(0).getParameters())
                .containsExactly("一年级");
        assertThat(enriched.getLimit()).isEqualTo(1000);
    }

    @Test
    @DisplayName("跨实体维度经 BFS 求出 JOIN")
    void enrichCrossEntityDimensionFindsJoin() {
        SemanticDslEnricher enricher = new SemanticDslEnricher(metaDataService);

        SemanticQueryDSL dsl = new SemanticQueryDSL();
        dsl.setEntity("student");
        dsl.setDimensions(List.of("school_name"));

        EnrichedQueryDSL enriched = enricher.enrich(dsl, candidateWithSchool());

        assertThat(enriched.getJoins()).hasSize(1);
        assertThat(enriched.getJoins().get(0).getJoinType()).isEqualTo("LEFT JOIN");
        assertThat(enriched.getJoins().get(0).getPhysicalTable()).isEqualTo("t_school");
        assertThat(enriched.getGroupBy()).containsExactly("t_school.name");
    }

    @Test
    @DisplayName("找不到主实体返回空富化结果")
    void unknownEntityReturnsEmpty() {
        SemanticDslEnricher enricher = new SemanticDslEnricher(metaDataService);

        SemanticQueryDSL dsl = new SemanticQueryDSL();
        dsl.setEntity("ghost");
        DslCandidate candidate = new DslCandidate();

        EnrichedQueryDSL enriched = enricher.enrich(dsl, candidate);

        assertThat(enriched.getMainPhysicalTable()).isNull();
        assertThat(enriched.getSelectColumns()).isNull();
    }

    private static DslCandidate candidate() {
        DslEntity student = new DslEntity();
        student.setEntityCode("student");
        student.setPhysicalTable("t_student");
        student.setPrimaryKey("id");

        DslMetric metric = new DslMetric();
        metric.setMetricCode("avg_score");
        metric.setEntityCode("student");
        metric.setAggregationType("AVG");
        metric.setExpression("score");

        DslDimension grade = new DslDimension();
        grade.setDimensionCode("grade");
        grade.setEntityCode("student");
        grade.setPhysicalColumn("grade");

        DslDimensionValue value = new DslDimensionValue();
        value.setDimensionCode("grade");
        value.setValueCode("G1");
        value.setPhysicalValue("一年级");

        DslCandidate candidate = new DslCandidate();
        candidate.setEntities(List.of(student));
        candidate.setMetrics(List.of(metric));
        candidate.setDimensions(List.of(grade));
        candidate.setDimensionValues(List.of(value));
        return candidate;
    }

    private static DslCandidate candidateWithSchool() {
        DslEntity student = new DslEntity();
        student.setEntityCode("student");
        student.setPhysicalTable("t_student");
        student.setPrimaryKey("id");

        DslEntity school = new DslEntity();
        school.setEntityCode("school");
        school.setPhysicalTable("t_school");
        school.setPrimaryKey("id");

        DslDimension schoolName = new DslDimension();
        schoolName.setDimensionCode("school_name");
        schoolName.setEntityCode("school");
        schoolName.setPhysicalColumn("name");

        DslRelation relation = new DslRelation();
        relation.setSourceEntity("student");
        relation.setTargetEntity("school");
        relation.setJoinType("LEFT");
        relation.setJoinCondition("t_student.school_id = t_school.id");

        DslCandidate candidate = new DslCandidate();
        candidate.setEntities(List.of(student, school));
        candidate.setDimensions(List.of(schoolName));
        candidate.setRelations(List.of(relation));
        return candidate;
    }
}
