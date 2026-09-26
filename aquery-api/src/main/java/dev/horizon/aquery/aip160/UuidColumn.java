package dev.horizon.aquery.aip160;

import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * A UUID field in a UUID database column.
 *
 * <p>
 * The client writes the UUID as a string in double quotes, for example
 * {@code "123e4567-e89b-12d3-a456-426614174000"}. The UUID has 32 hexadecimal digits in five groups of 8, 4, 4, 4
 * and 12 digits. The digits can be uppercase or lowercase. The field refuses other forms.
 *
 * <p>
 * The field supports equality and inequality only. The restriction binds a {@link UUID}.
 *
 * <p>
 * An order_by clause can sort by the field in the order of the database. The text of a cursor is the UUID in
 * lowercase. Keyset pagination binds it as a {@link UUID}. The server can give the value of a row as a {@link UUID}
 * or as its text.
 */
public class UuidColumn extends SimpleColumn {

  private static final Pattern UUID_TEXT = Pattern
      .compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

  public UuidColumn(String databaseName) {
    super(databaseName);
  }

  @Override
  public String typeName() {
    return "UUID";
  }

  @Override
  public Set<Operator> operators() {
    return Operator.EQUALITY;
  }

  @Override
  public String restrictionQuery(RestrictionContext restriction, Generator generator) {
    UUID value = argument(restriction, arg -> uuidArgument(Args.coerceToStringConstant(arg)));
    return comparison(restriction, generator, generator.bind(value));
  }

  @Override
  public Object cursorValue(String cursorText) {
    if (!UUID_TEXT.matcher(cursorText).matches()) {
      throw new IllegalArgumentException("'" + cursorText + "' is not a UUID");
    }
    return UUID.fromString(cursorText);
  }

  @Override
  public String cursorText(Object value) {
    return switch (value) {
      case UUID uuid -> uuid.toString();
      case String text -> cursorValue(text).toString();
      case null, default -> throw new IllegalArgumentException("'" + value + "' is not a UUID");
    };
  }

  private static UUID uuidArgument(String value) {
    if (!UUID_TEXT.matcher(value).matches()) {
      throw new InvalidFilterException("'%s' is not a valid UUID, expected e.g. \"123e4567-e89b-12d3-a456-426614174000\"",
          value);
    }
    return UUID.fromString(value);
  }
}
