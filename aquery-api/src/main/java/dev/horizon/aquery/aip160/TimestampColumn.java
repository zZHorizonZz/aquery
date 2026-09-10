package dev.horizon.aquery.aip160;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;

/**
 * A timestamp field in an INT64 database column with microseconds since the epoch.
 *
 * <p>
 * The client writes an RFC 3339 timestamp in double quotes, as AIP-160 specifies. An example is
 * {@code "2012-04-21T11:30:00-04:00"}. The timestamp must have seconds and a UTC offset or {@code Z}. The field
 * refuses other forms.
 *
 * <p>
 * The SQL compares the column with the microseconds of the instant. Two timestamps of the same instant in
 * different zones are thus equal. The field supports all order operators: {@code = != < <= > >=}.
 *
 * <p>
 * This field has no LUCI equivalent. It connects a text literal to an integer column, as {@code DurationColumn}
 * does.
 */
public class TimestampColumn extends Int64Column {

  public TimestampColumn(String databaseName) {
    super(databaseName);
  }

  @Override
  public String typeName() {
    return "TIMESTAMP";
  }

  @Override
  public Set<Operator> operators() {
    return Operator.ORDERED;
  }

  @Override
  public String restrictionQuery(RestrictionContext restriction, Generator generator) {
    Instant instant = argument(restriction, Args::coerceToTimestampConstant).toInstant();
    // RFC 3339 years have four digits. The microseconds of these years always fit in an INT64.
    return comparison(restriction, generator, generator.literal(ChronoUnit.MICROS.between(Instant.EPOCH, instant)));
  }
}
