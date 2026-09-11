package dev.horizon.aquery.aip160;

import java.util.Set;

/**
 * A string field in a STRING database column.
 *
 * <p>
 * The field supports all operators:
 *
 * <ul>
 * <li>{@code name = "dan"} and {@code name != "dan"} compare the column with the bound value. A {@code *} at the
 * start or at the end of the value is a wildcard, as AIP-160 specifies. For example, {@code name = "*.foo"}
 * matches all names that end with {@code .foo}.
 * <li>{@code name < "dan"}, {@code <=}, {@code >} and {@code >=} compare the strings in lexical order.
 * <li>{@code name : "dan"} matches a substring: {@code LIKE '%dan%'}. The SQL escapes the {@code %} and
 * {@code _} characters of the client. Thus they match as usual characters.
 * </ul>
 *
 * <p>
 * The field also answers implicit restrictions in the same way. A bare value in the filter matches as a
 * substring.
 *
 * <p>
 * The has operator ({@code :}) matches a substring, as in LUCI. AIP-160 does not define the has operator for a
 * string field.
 *
 * @see <a href=
 * "https://chromium.googlesource.com/infra/luci/luci-go/+/main/common/data/aip160/string_column.go">LUCI
 * aip160: string_column.go (StringColumn)</a>
 */
public class StringColumn extends SimpleColumn {

  private static final Set<Operator> OPERATORS = Set.of(Operator.values());

  public StringColumn(String databaseName) {
    super(databaseName);
  }

  @Override
  public String typeName() {
    return "STRING";
  }

  @Override
  public Set<Operator> operators() {
    return OPERATORS;
  }

  @Override
  public String restrictionQuery(RestrictionContext restriction, Generator generator) {
    String value = argument(restriction, Args::coerceToStringConstant);
    return switch (restriction.operator()) {
      case HAS -> "(" + column(generator) + " LIKE " + generator.bindString(containsPattern(value)) + ")";
      case EQUALS, NOT_EQUALS -> "(" + stringEquality(column(generator), restriction.operator(), value, generator) + ")";
      default -> comparison(restriction, generator, generator.bindString(value));
    };
  }

  @Override
  public boolean supportsImplicitRestrictions() {
    return true;
  }

  @Override
  public String implicitRestrictionQuery(ImplicitRestrictionContext restriction, Generator generator) {
    return "(" + column(generator) + " LIKE " + generator.bindString(containsPattern(restriction.argValueUnsafe())) + ")";
  }
}
