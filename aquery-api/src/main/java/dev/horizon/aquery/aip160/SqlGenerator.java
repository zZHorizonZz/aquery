package dev.horizon.aquery.aip160;

import dev.horizon.aquery.Parameters;
import dev.horizon.aquery.aip160.FieldBackend.Generator;
import java.util.Objects;

/**
 * The {@link Generator} of one SQL fragment. It adds the table alias to the columns, and binds values in the
 * parameters of the statement.
 *
 * <p>
 * {@link WhereClause} and {@code Keyset} write through this generator. A caller can also use it to test a backend.
 *
 * <p>
 * The table alias follows the rules of {@link TableAlias}. The constructor throws {@link IllegalArgumentException}
 * for an alias that does not obey these rules.
 */
public final class SqlGenerator implements Generator {

  private final TableAlias tableAlias;
  private final Parameters parameters;

  public SqlGenerator(String tableAlias, Parameters parameters) {
    this.tableAlias = new TableAlias(tableAlias);
    this.parameters = Objects.requireNonNull(parameters, "parameters");
  }

  @Override
  public String columnReference(String databaseName) {
    return tableAlias.columnReference(databaseName);
  }

  @Override
  public String bind(Object value) {
    return parameters.bind(value);
  }
}
