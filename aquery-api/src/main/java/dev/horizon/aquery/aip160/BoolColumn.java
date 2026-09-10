package dev.horizon.aquery.aip160;

import java.util.Set;

/**
 * A boolean field in a BOOL database column.
 *
 * <p>
 * The field supports equality and inequality only. AIP-160 says that a boolean field does not support the order
 * comparisons. The client writes the word {@code true} or {@code false} without quotes. A value in quotes, as in
 * {@code "true"}, is a string. The field refuses it.
 *
 * <p>
 * The SQL contains the literal {@code TRUE} or {@code FALSE}. A boolean binds no parameter.
 *
 * @see <a href=
 *      "https://chromium.googlesource.com/infra/luci/luci-go/+/main/common/data/aip160/bool_column.go">LUCI
 *      aip160: bool_column.go (BoolColumn)</a>
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
    return comparison(restriction, generator, generator.literal(argument(restriction, Args::coerceToBoolConstant)));
  }

  @Override
  public String cursorArgument(String cursorValue, Generator generator) {
    return switch (cursorValue) {
      case "true" -> generator.literal(true);
      case "false" -> generator.literal(false);
      default -> throw new IllegalArgumentException("'" + cursorValue + "' is not a boolean");
    };
  }
}
