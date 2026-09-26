package dev.horizon.aquery.aip160;

import java.util.Set;

/**
 * An integer field in a 64-bit integer database column, for example BIGINT.
 *
 * <p>
 * The field supports all order operators: {@code = != < <= > >=}. The client writes an integer without quotes,
 * for example {@code 42} or {@code -30}. A value in quotes, as in {@code "123"}, is a string. The field refuses
 * it. The field also refuses a float, for example {@code 2.5}.
 *
 * <p>
 * The SQL compares the column with a placeholder. The restriction binds the integer as a {@link Long}. The text of
 * a cursor is the decimal text of the integer.
 *
 * @see <a href="https://chromium.googlesource.com/infra/luci/luci-go/+/main/common/data/aip160/int_column.go">LUCI aip160: int_column.go (IntegerColumn)</a>
 */
public class IntegerColumn extends Int64Column {

  public IntegerColumn(String databaseName) {
    super(databaseName);
  }

  @Override
  public String typeName() {
    return "INTEGER";
  }

  @Override
  public Set<Operator> operators() {
    return Operator.ORDERED;
  }

  @Override
  public String restrictionQuery(RestrictionContext restriction, Generator generator) {
    Long value = argument(restriction, Args::coerceToIntegerConstant);
    return comparison(restriction, generator, generator.bind(value));
  }
}
