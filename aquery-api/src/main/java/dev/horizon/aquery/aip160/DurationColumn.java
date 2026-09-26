package dev.horizon.aquery.aip160;

import java.util.Set;

/**
 * A duration field in a 64-bit integer database column with nanoseconds, for example BIGINT.
 *
 * <p>
 * The client writes a number with an {@code s} suffix and without quotes, as AIP-160 specifies. Examples are
 * {@code 1.5s} and {@code 20.1s}. The SQL compares the column with the exact number of nanoseconds. A value in
 * quotes, as in {@code "1.5s"}, is a string. The field refuses it.
 *
 * <p>
 * The restriction binds the nanoseconds as a {@link Long}. The text of a cursor is the decimal text of the
 * nanoseconds. The field supports all order operators: {@code = != < <= > >=}.
 *
 * @see <a href="https://chromium.googlesource.com/infra/luci/luci-go/+/main/common/data/aip160/duration_column.go">LUCI aip160: duration_column.go (DurationColumn)</a>
 */
public class DurationColumn extends Int64Column {

  public DurationColumn(String databaseName) {
    super(databaseName);
  }

  @Override
  public String typeName() {
    return "DURATION";
  }

  @Override
  public Set<Operator> operators() {
    return Operator.ORDERED;
  }

  @Override
  public String restrictionQuery(RestrictionContext restriction, Generator generator) {
    // The reader makes sure that the nanoseconds fit in 64 bits.
    Long nanoseconds = argument(restriction, Args::coerceToDurationConstant).toNanos();
    return comparison(restriction, generator, generator.bind(nanoseconds));
  }
}
