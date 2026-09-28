package org.example.nlp2dsl2sql.enums;

public enum SqlDialect {
    POSTGRESQL("postgresql");

    private final String value;

    SqlDialect(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    /**
     * 解析配置值。
     * <p>
     * 未匹配时抛异常而非回退默认：方言配错却静默用 PostgreSQL 翻译，
     * 产出的 SQL 会在目标库上直接报错，且报错点离配置点很远。
     */
    public static SqlDialect fromValue(String value) {
        for (SqlDialect dialect : values()) {
            if (dialect.value.equalsIgnoreCase(value)) {
                return dialect;
            }
        }
        throw new IllegalArgumentException("不支持的 SQL 方言: " + value
                + "（当前仅实现 " + POSTGRESQL.value + "）");
    }
}
