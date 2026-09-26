package dev.horizon.aquery.aip160;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Set;

/**
 * A timestamp field in a timestamp database column, for example TIMESTAMP WITH TIME ZONE.
 *
 * <p>
 * The client writes an RFC 3339 timestamp in double quotes, as AIP-160 specifies. An example is
 * {@code "2012-04-21T11:30:00-04:00"}. The timestamp must have seconds and a UTC offset or {@code Z}. The field
 * refuses other forms.
 *
 * <p>
 * The restriction binds the instant as an {@link OffsetDateTime} in UTC. Two timestamps of the same instant in
 * different zones are thus equal. The field supports all order operators: {@code = != < <= > >=}.
 *
 * <p>
 * The text of a cursor is an RFC 3339 timestamp in UTC, for example {@code 2012-04-21T15:30:00Z}. Keyset pagination
 * binds it as an {@link OffsetDateTime} in UTC. The server can give the value of a row as an
 * {@link OffsetDateTime}, an {@link Instant} or a {@link LocalDateTime}. A {@link LocalDateTime} is a time in UTC.
 *
 * <p>
 * This field has no LUCI equivalent.
 */
public class TimestampColumn extends SimpleColumn {

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
    OffsetDateTime value = argument(restriction, Args::coerceToTimestampConstant).withOffsetSameInstant(ZoneOffset.UTC);
    return comparison(restriction, generator, generator.bind(value));
  }

  @Override
  public Object cursorValue(String cursorText) {
    try {
      return OffsetDateTime.parse(cursorText).withOffsetSameInstant(ZoneOffset.UTC);
    } catch (DateTimeParseException unreadable) {
      throw new IllegalArgumentException("'" + cursorText + "' is not an RFC 3339 timestamp", unreadable);
    }
  }

  @Override
  public String cursorText(Object value) {
    Instant instant = switch (value) {
      case OffsetDateTime timestamp -> timestamp.toInstant();
      case Instant timestamp -> timestamp;
      case LocalDateTime timestamp -> timestamp.toInstant(ZoneOffset.UTC);
      case null, default -> throw new IllegalArgumentException("'" + value + "' is not a timestamp");
    };
    return DateTimeFormatter.ISO_INSTANT.format(instant);
  }
}
