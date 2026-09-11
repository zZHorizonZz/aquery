package dev.horizon.aquery.aip160;

import java.util.Objects;
import java.util.Set;

/**
 * A key-value field, for example a map of labels.
 *
 * <p>
 * The database keeps the pairs in one of two forms. The {@link Representation} tells the backend which form the
 * column has:
 *
 * <ul>
 * <li>{@link Representation#STRING_ARRAY}: an array of strings with one element for each pair, written
 * {@code key:value}. A key in this form cannot contain {@code :}.
 * <li>{@link Representation#REPEATED_STRUCT}: a repeated struct of a key string and a value string.
 * </ul>
 *
 * <p>
 * The field supports these restrictions, as AIP-160 specifies for maps:
 *
 * <ul>
 * <li>{@code labels:*} is true if the field has pairs.
 * <li>{@code labels:site} and {@code labels.site:*} are true if the field has the key {@code site}.
 * <li>{@code labels.site = "pilsen"} and {@code labels.site != "pilsen"} compare the value of the key. A
 * {@code *} at the start or at the end of the value is a wildcard. The inequality is true only if the key is
 * present, as AIP-160 specifies for a missing key.
 * <li>{@code labels.site : "pil"} is true if the value of the key contains {@code pil}. AIP-160 says that this
 * restriction compares for equality. The field matches a substring, as in LUCI.
 * </ul>
 *
 * <p>
 * The key comes from the client. Thus the SQL contains the key only in a bound parameter. The SQL also escapes
 * the LIKE wildcards of the key.
 *
 * <p>
 * An order_by clause cannot sort by the field. A bare value in the filter cannot match the field.
 *
 * @see <a href="https://chromium.googlesource.com/infra/luci/luci-go/+/main/common/data/aip160/key_value_column.go">LUCI aip160: key_value_column.go (KeyValueColumn)</a>
 */
public class KeyValueColumn extends SimpleColumn {

  /** The form of the pairs in the database column. */
  public enum Representation {
    STRING_ARRAY,
    REPEATED_STRUCT
  }

  private static final Set<Operator> OPERATORS = Set.of(Operator.HAS, Operator.EQUALS, Operator.NOT_EQUALS);

  private final Representation representation;

  public KeyValueColumn(String databaseName, Representation representation) {
    super(databaseName);
    this.representation = Objects.requireNonNull(representation, "representation");
  }

  @Override
  public String typeName() {
    return "KEY-VALUE";
  }

  @Override
  public Set<Operator> operators() {
    return OPERATORS;
  }

  @Override
  public boolean acceptsNestedFields() {
    return true;
  }

  @Override
  public boolean supportsSorting() {
    return false;
  }

  @Override
  public String restrictionQuery(RestrictionContext restriction, Generator generator) {
    String column = column(generator);
    if (restriction.nestedFields().isEmpty()) {
      return fieldQuery(restriction, column, generator);
    }
    if (restriction.nestedFields().size() > 1) {
      throw new InvalidFilterException("expected only a single '.' after key-value column named '%s'", restriction.fieldPath());
    }

    String keyUnsafe = key(restriction, restriction.nestedFields().getFirst());
    if (restriction.operator() == Operator.HAS && Args.isPresenceWildcard(restriction.arg())) {
      return keyPresence(column, keyUnsafe, generator);
    }
    // The argument is text from the client. The SQL contains it only in bound parameters.
    String valueUnsafe = argument(restriction, Args::coerceToStringConstant);
    return switch (representation) {
      case STRING_ARRAY -> stringArrayQuery(restriction.operator(), column, keyUnsafe, valueUnsafe, generator);
      case REPEATED_STRUCT -> repeatedStructQuery(restriction.operator(), column, keyUnsafe, valueUnsafe, generator);
    };
  }

  private String fieldQuery(RestrictionContext restriction, String column, Generator generator) {
    if (restriction.operator() != Operator.HAS) {
      String written = restriction.fieldPath() + restriction.operator().symbol();
      throw new InvalidFilterException("key value columns must specify the key to search on. Instead of '%s' try '%s'", written,
          restriction.fieldPath() + ".key" + restriction.operator().symbol());
    }
    if (Args.isPresenceWildcard(restriction.arg())) {
      return "(ARRAY_LENGTH(" + column + ") > 0)";
    }
    return keyPresence(column, key(restriction, argument(restriction, Args::coerceToKeyConstant)), generator);
  }

  private String key(RestrictionContext restriction, String keyUnsafe) {
    if (representation == Representation.STRING_ARRAY && keyUnsafe.indexOf(':') >= 0) {
      throw new InvalidFilterException("the keys of field '%s' cannot contain ':', because ':' separates a key from its value",
          restriction.fieldPath());
    }
    return keyUnsafe;
  }

  private String keyPresence(String column, String keyUnsafe, Generator generator) {
    return switch (representation) {
      case STRING_ARRAY -> exists(column, "STARTS_WITH(_v, " + generator.bindString(keyUnsafe + ":") + ")");
      case REPEATED_STRUCT -> exists(column, "_v.key = " + generator.bindString(keyUnsafe));
    };
  }

  private static String stringArrayQuery(Operator operator, String column, String keyUnsafe, String valueUnsafe,
      Generator generator) {
    String keyPattern = quoteLike(keyUnsafe) + ":";
    String wildcard = wildcardPattern(valueUnsafe);
    return switch (operator) {
      case HAS -> exists(column, "_v LIKE " + generator.bindString(keyPattern + containsPattern(valueUnsafe)));
      case EQUALS ->
        wildcard == null ? "(" + generator.bindString(keyUnsafe + ":" + valueUnsafe) + " IN UNNEST(" + column + "))"
            : exists(column, "_v LIKE " + generator.bindString(keyPattern + wildcard));
      case NOT_EQUALS -> {
        String compared = wildcard == null ? "_v <> " + generator.bindString(keyUnsafe + ":" + valueUnsafe)
            : "_v NOT LIKE " + generator.bindString(keyPattern + wildcard);
        yield exists(column, "STARTS_WITH(_v, " + generator.bindString(keyUnsafe + ":") + ") AND " + compared);
      }
      default -> throw new IllegalStateException("the generator refuses operator " + operator.symbol());
    };
  }

  private static String repeatedStructQuery(Operator operator, String column, String keyUnsafe, String valueUnsafe,
      Generator generator) {
    String key = generator.bindString(keyUnsafe);
    String compared = operator == Operator.HAS ? "_v.value LIKE " + generator.bindString(containsPattern(valueUnsafe))
        : stringEquality("_v.value", operator, valueUnsafe, generator);
    return "(EXISTS (SELECT _v.key, _v.value FROM UNNEST(" + column + ") as _v WHERE _v.key = " + key + " AND " + compared
        + "))";
  }

  private static String exists(String column, String condition) {
    return "(EXISTS (SELECT 1 FROM UNNEST(" + column + ") as _v WHERE " + condition + "))";
  }
}
