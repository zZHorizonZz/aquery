package dev.horizon.aquery.aip160;

import dev.horizon.aquery.aip160.FieldBackend.ColumnReferences;
import dev.horizon.aquery.common.Identifiers;

/**
 * The alias of a table in an SQL statement. It gives the column references of the table.
 *
 * <p>
 * Use null or an empty string if the table has no alias. Then the column references are the column names.
 *
 * <p>
 * The alias becomes part of the SQL text. Thus it must be an SQL identifier: letters, digits and underscores, with
 * no digit at the start. An alias must not start with {@code _}. The library keeps these aliases for the SQL that
 * it writes, for example the {@code _v} of an UNNEST. The constructor throws {@link IllegalArgumentException} for an
 * alias that does not obey these rules.
 */
public final class TableAlias implements ColumnReferences {

  private final String prefix;

  public TableAlias(String alias) {
    if (alias == null || alias.isEmpty()) {
      this.prefix = "";
      return;
    }
    if (alias.startsWith("_")) {
      throw new IllegalArgumentException("table aliases starting with '_' are reserved for use within generated SQL");
    }
    if (!Identifiers.isIdentifier(alias)) {
      throw new IllegalArgumentException("table aliases are SQL identifiers of letters, digits and '_', was '" + alias + "'");
    }
    this.prefix = alias + ".";
  }

  @Override
  public String columnReference(String databaseName) {
    return prefix + databaseName;
  }
}
