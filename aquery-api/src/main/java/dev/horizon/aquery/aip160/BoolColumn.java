package dev.horizon.aquery.aip160;

import java.util.Set;

/**
 * A boolean field in a BOOLEAN database column.
 *
 * <p>
 * The field supports equality and inequality only. AIP-160 says that a boolean field does not support the order
 * comparisons. The client writes the word {@code true} or {@code false} without quotes. A value in quotes, as in
 * {@code "true"}, is a string. The field refuses it.
 *
 * <p>
 * The SQL compares the column with a placeholder, as in {@code (T.locked = ?)}. The restriction binds a
 * {@link Boolean}. The text of a cursor is {@code true} or {@code false}.
 *
 * @see <a href="https://chromium.googlesource.com/infra/luci/luci-go/+/main/common/data/aip160/bool_column.go">LUCI aip160: bool_column.go (BoolColumn)</a>
 */
public class BoolColumn extends SimpleColumn {

  public BoolColumn(String databaseName) {
    super(databaseName);
  }

  @Override
  public String typeName() {
    return "BOOL";
  }

  @Override
  public Set<Operator> operators() {
    return Operator.EQUALITY;
  }

  @Override
  public String restrictionQuery(RestrictionContext restriction, Generator generator) {
    Boolean value = argument(restriction, Args::coerceToBoolConstant);
    return comparison(restriction, generator, generator.bind(value));
  }

  @Override
  public Object cursorValue(String cursorText) {
    return switch (cursorText) {
      case "true" -> Boolean.TRUE;
      case "false" -> Boolean.FALSE;
      default -> throw new IllegalArgumentException("'" + cursorText + "' is not a boolean");
    };
  }

  @Override
  public String cursorText(Object value) {
    if (value instanceof Boolean bool) {
      return bool.toString();
    }
    throw new IllegalArgumentException("'" + value + "' is not a boolean");
  }
}
