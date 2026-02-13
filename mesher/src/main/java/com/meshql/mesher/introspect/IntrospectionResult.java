package com.meshql.mesher.introspect;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record IntrospectionResult(
        @JsonProperty("tables") List<TableInfo> tables,
        @JsonProperty("columns") List<ColumnInfo> columns,
        @JsonProperty("primaryKeys") List<PrimaryKeyInfo> primaryKeys,
        @JsonProperty("foreignKeys") List<ForeignKeyInfo> foreignKeys,
        @JsonProperty("uniqueConstraints") List<UniqueConstraintInfo> uniqueConstraints,
        @JsonProperty("checkConstraints") List<CheckConstraintInfo> checkConstraints
) {}
