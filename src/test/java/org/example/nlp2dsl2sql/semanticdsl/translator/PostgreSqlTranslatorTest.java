package org.example.nlp2dsl2sql.semanticdsl.translator;

import org.example.nlp2dsl2sql.models.dto.dsl.EnrichedQueryDSL;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PostgreSQL 翻译器")
class PostgreSqlTranslatorTest {

    private final PostgreSqlTranslator translator = new PostgreSqlTranslator();

    @Test
    @DisplayName("简单查询翻译并引用标识符")
    void translateSimpleQuery() {
        EnrichedQueryDSL dsl = new EnrichedQueryDSL();
        dsl.setMainPhysicalTable("t_student");
        dsl.setSelectColumns(List.of(column("AVG(score)", "avg_score")));
        dsl.setLimit(1000);

        DslTranslator.TranslatedSql result = translator.translate(dsl);

        assertThat(result.sql()).isEqualTo(
                "SELECT AVG(score) AS \"avg_score\" FROM \"t_student\" LIMIT 1000");
        assertThat(result.parameters()).isEmpty();
    }

    @Test
    @DisplayName("JOIN、WHERE 参数与 GROUP BY 完整翻译")
    void translateJoinWhereGroupBy() {
        EnrichedQueryDSL.EnrichedJoin join = new EnrichedQueryDSL.EnrichedJoin();
        join.setJoinType("LEFT JOIN");
        join.setPhysicalTable("t_school");
        join.setOnCondition("t_student.school_id = t_school.id");

        EnrichedQueryDSL.WhereColumn where = new EnrichedQueryDSL.WhereColumn();
        where.setExpression("t_student.grade = ?");
        where.setParameters(List.of((Object) "一年级"));

        EnrichedQueryDSL dsl = new EnrichedQueryDSL();
        dsl.setMainPhysicalTable("t_student");
        dsl.setSelectColumns(List.of(
                column("AVG(score)", "avg_score"),
                column("t_student.grade", "grade")));
        dsl.setJoins(List.of(join));
        dsl.setWhereConditions(List.of(where));
        dsl.setGroupBy(List.of("t_student.grade"));

        DslTranslator.TranslatedSql result = translator.translate(dsl);

        assertThat(result.sql()).isEqualTo(
                "SELECT AVG(score) AS \"avg_score\", t_student.grade AS \"grade\""
                        + " FROM \"t_student\""
                        + " LEFT JOIN \"t_school\" ON t_student.school_id = t_school.id"
                        + " WHERE t_student.grade = ?"
                        + " GROUP BY \"t_student\".\"grade\"");
        assertThat(result.parameters()).containsExactly("一年级");
    }

    @Test
    @DisplayName("缺主表抛异常")
    void missingMainTableThrows() {
        EnrichedQueryDSL dsl = new EnrichedQueryDSL();
        dsl.setSelectColumns(List.of(column("AVG(score)", "avg_score")));

        assertThatThrownBy(() -> translator.translate(dsl))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("缺 SELECT 列抛异常")
    void missingSelectColumnsThrows() {
        EnrichedQueryDSL dsl = new EnrichedQueryDSL();
        dsl.setMainPhysicalTable("t_student");

        assertThatThrownBy(() -> translator.translate(dsl))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("非法标识符抛异常")
    void invalidIdentifierThrows() {
        EnrichedQueryDSL dsl = new EnrichedQueryDSL();
        dsl.setMainPhysicalTable("123bad");
        dsl.setSelectColumns(List.of(column("AVG(score)", "avg_score")));

        assertThatThrownBy(() -> translator.translate(dsl))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static EnrichedQueryDSL.SelectColumn column(String expression, String alias) {
        EnrichedQueryDSL.SelectColumn column = new EnrichedQueryDSL.SelectColumn();
        column.setExpression(expression);
        column.setAlias(alias);
        return column;
    }
}
