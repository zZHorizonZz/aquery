package dev.horizon.aquery.aip160;

import dev.horizon.aquery.aip160.Filter.Arg;
import java.util.Objects;
import java.util.Set;

/**
 * An enum field in a database column with the number or the name of the value.
 *
 * <p>
 * The client writes the name of the value without quotes, for example {@code ACTIVE}. A value in quotes, as in
 * {@code "ACTIVE"}, is a string. The field refuses it. The field also refuses a name that is not in the definition.
 * The error message gives the names that the client can use.
 *
 * <p>
 * The {@link Storage} tells what the column keeps:
 *
 * <ul>
 * <li>{@link Storage#NUMBER}: the number of the value in an integer column. The enum definition gives the number
 * of the name. The restriction binds the number as a {@link Long}. The text of a cursor is the decimal text of the
 * number.
 * <li>{@link Storage#NAME}: the name of the value in a character column. The restriction binds the name as a
 * {@link String}. The text of a cursor is the name.
 * </ul>
 *
 * <p>
 * The field supports equality and inequality only. AIP-160 says that an enum field does not support the order
 * comparisons.
 *
 * @see <a href="https://chromium.googlesource.com/infra/luci/luci-go/+/main/common/data/aip160/enum_column.go">LUCI aip160: enum_column.go (EnumColumn)</a>
 */
public class EnumColumn extends SimpleColumn {

  /** What the database column keeps for an enum value. */
  public enum Storage {
    NUMBER,
    NAME
  }

  private final Args.EnumDefinition definition;
  private final Storage storage;

  public EnumColumn(String databaseName, Args.EnumDefinition definition) {
    this(databaseName, definition, Storage.NUMBER);
  }

  public EnumColumn(String databaseName, Args.EnumDefinition definition, Storage storage) {
    super(databaseName);
    this.definition = Objects.requireNonNull(definition, "definition");
    this.storage = Objects.requireNonNull(storage, "storage");
  }

  @Override
  public String typeName() {
    return definition.typeName();
  }

  @Override
  public Set<Operator> operators() {
    return Operator.EQUALITY;
  }

  @Override
  public String restrictionQuery(RestrictionContext restriction, Generator generator) {
    Object value = switch (storage) {
      case NUMBER -> argument(restriction, arg -> (long) Args.coerceToEnumConstant(arg, definition));
      case NAME -> argument(restriction, this::name);
    };
    return comparison(restriction, generator, generator.bind(value));
  }

  @Override
  public Object cursorValue(String cursorText) {
    return switch (storage) {
      case NUMBER -> Int64Column.integerValue(cursorText);
      case NAME -> cursorText;
    };
  }

  @Override
  public String cursorText(Object value) {
    return switch (storage) {
      case NUMBER -> Int64Column.integerText(value);
      case NAME -> String.valueOf(value);
    };
  }

  private String name(Arg arg) {
    Args.coerceToEnumConstant(arg, definition);
    return Args.coerceToKeyConstant(arg);
  }
}
