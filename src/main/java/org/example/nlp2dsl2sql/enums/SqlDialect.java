package org.example.nlp2dsl2sql.enums;

public enum SqlDialect {
    POSTGRESQL("postgresql"),
    MYSQL("mysql"),
    SQLSERVER("sqlserver");

    private final String value;

    SqlDialect(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static SqlDialect fromValue(String value) {
        for (SqlDialect dialect : values()) {
            if (dialect.value.equalsIgnoreCase(value)) {
                return dialect;
            }
        }
        return POSTGRESQL;
    }
}
