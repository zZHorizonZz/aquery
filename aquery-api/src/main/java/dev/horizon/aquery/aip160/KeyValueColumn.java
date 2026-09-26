package dev.horizon.aquery.aip160;

import dev.horizon.aquery.common.Identifiers;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * A key-value field, for example a map of labels.
 *
 * <p>
 * The database keeps the pairs in one of two forms. The {@link Representation} tells the backend which form the
 * field has:
 *
 * <ul>
 * <li>{@link Representation#STRING_ARRAY}: an ISO SQL array of strings with one element for each pair, written
 * {@code key:value}. A key in this form cannot contain {@code :}. Only engines with array columns, for example
 * PostgreSQL and H2, can run this SQL. Make the field with {@link #KeyValueColumn(String, Representation)}.
 * <li>{@link Representation#CHILD_TABLE}: the rows of a child table with a key column and a value column. Each row
 * is one pair. All engines can run this SQL. Make the field with {@link #KeyValueColumn(ChildTable)}.
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
 * The key comes from the client. Thus the SQL contains the key only in a bound value. The SQL also escapes the LIKE
 * wildcards of the key. Each restriction binds strings only.
 *
 * <p>
 * An order_by clause cannot sort by the field. A bare value in the filter cannot match the field. A read mask can
 * select a field in a string array. It cannot select a field in a child table, because the main table has no
 * column for it.
 *
 * @see <a href="https://chromium.googlesource.com/infra/luci/luci-go/+/main/common/data/aip160/key_value_column.go">LUCI aip160: key_value_column.go (KeyValueColumn)</a>
 */
public class KeyValueColumn extends SimpleColumn {

  /** The form of the pairs in the database. */
  public enum Representation {
    STRING_ARRAY,
    CHILD_TABLE
  }

  /**
   * The child table that keeps the pairs of a key-value field. Each row of the table is one pair.
   *
   * <p>
   * The parent column of the child table refers to the referenced column of the main table, usually its id. For
   * example, the table {@code book_labels} with the columns {@code book_id}, {@code label_key} and
   * {@code label_value} keeps the labels of the table {@code books} with the id {@code id}:
   *
   * <pre>{@code
   * new ChildTable("book_labels", "book_id", "label_key", "label_value", "id")
   * }</pre>
   *
   * <p>
   * The names become part of the SQL text. Thus each name must be an SQL identifier: letters, digits and
   * underscores, with no digit at the start. The constructor throws {@link IllegalArgumentException} for a name that
   * does not obey this rule.
   *
   * <p>
   * The SQL names the child table {@code aquery_kv} and the main table by the table alias of the statement. Give the
   * statement a table alias. Without an alias, the referenced column can name a column of the child table.
   */
  public record ChildTable(String table, String parentColumn, String keyColumn, String valueColumn,
      String referencedColumn) {

    public ChildTable {
      requireIdentifier("table", table);
      requireIdentifier("parentColumn", parentColumn);
      requireIdentifier("keyColumn", keyColumn);
      requireIdentifier("valueColumn", valueColumn);
      requireIdentifier("referencedColumn", referencedColumn);
    }

    private static void requireIdentifier(String name, String value) {
      if (value == null || !Identifiers.isIdentifier(value)) {
        throw new IllegalArgumentException(
            "the " + name + " of a child table is an SQL identifier of letters, digits and '_', was '" + value + "'");
      }
    }
  }

  private static final Set<Operator> OPERATORS = Set.of(Operator.HAS, Operator.EQUALS, Operator.NOT_EQUALS);

  private static final String CHILD = "aquery_kv";

  private final Representation representation;
  private final ChildTable childTable;

  /**
   * Makes a key-value field in an array of {@code key:value} strings.
   *
   * @param databaseName the name of the array column
   * @param representation {@link Representation#STRING_ARRAY}
   * @throws IllegalArgumentException for {@link Representation#CHILD_TABLE}. Use {@link #KeyValueColumn(ChildTable)}
   * for a child table
   */
  public KeyValueColumn(String databaseName, Representation representation) {
    super(databaseName);
    this.representation = Objects.requireNonNull(representation, "representation");
    if (representation == Representation.CHILD_TABLE) {
      throw new IllegalArgumentException(
          "a key-value field in a child table needs the table and its columns; use KeyValueColumn(ChildTable)");
    }
    this.childTable = null;
  }

  /**
   * Makes a key-value field in a child table.
   *
   * @param childTable the child table and its columns
   */
  public KeyValueColumn(ChildTable childTable) {
    super(Objects.requireNonNull(childTable, "childTable").referencedColumn());
    this.representation = Representation.CHILD_TABLE;
    this.childTable = childTable;
  }

  /**
   * Gives the form of the pairs in the database.
   *
   * @return the representation of the field
   */
  public Representation representation() {
    return representation;
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
  public boolean supportsReading() {
    return representation == Representation.STRING_ARRAY;
  }

  @Override
  public List<String> selectExpressions(ColumnReferences columns) {
    if (representation == Representation.CHILD_TABLE) {
      throw new UnsupportedOperationException(typeName() + " fields in a child table cannot be read");
    }
    return super.selectExpressions(columns);
  }

  @Override
  public String restrictionQuery(RestrictionContext restriction, Generator generator) {
    if (restriction.nestedFields().isEmpty()) {
      return fieldQuery(restriction, generator);
    }
    if (restriction.nestedFields().size() > 1) {
      throw new InvalidFilterException("expected only a single '.' after key-value column named '%s'", restriction.fieldPath());
    }

    String keyUnsafe = key(restriction, restriction.nestedFields().getFirst());
    if (restriction.operator() == Operator.HAS && Args.isPresenceWildcard(restriction.arg())) {
      return keyPresence(keyUnsafe, generator);
    }
    // The argument is text from the client. The SQL contains it only in bound values.
    String valueUnsafe = argument(restriction, Args::coerceToStringConstant);
    return switch (representation) {
      case STRING_ARRAY -> stringArrayQuery(restriction.operator(), column(generator), keyUnsafe, valueUnsafe, generator);
      case CHILD_TABLE -> childTableQuery(restriction.operator(), keyUnsafe, valueUnsafe, generator);
    };
  }

  private String fieldQuery(RestrictionContext restriction, Generator generator) {
    if (restriction.operator() != Operator.HAS) {
      String written = restriction.fieldPath() + restriction.operator().symbol();
      throw new InvalidFilterException("key value columns must specify the key to search on. Instead of '%s' try '%s'", written,
          restriction.fieldPath() + ".key" + restriction.operator().symbol());
    }
    if (Args.isPresenceWildcard(restriction.arg())) {
      return switch (representation) {
        case STRING_ARRAY -> "(CARDINALITY(" + column(generator) + ") > 0)";
        case CHILD_TABLE -> childRows(generator, "");
      };
    }
    return keyPresence(key(restriction, argument(restriction, Args::coerceToKeyConstant)), generator);
  }

  private String key(RestrictionContext restriction, String keyUnsafe) {
    if (representation == Representation.STRING_ARRAY && keyUnsafe.indexOf(':') >= 0) {
      throw new InvalidFilterException("the keys of field '%s' cannot contain ':', because ':' separates a key from its value",
          restriction.fieldPath());
    }
    return keyUnsafe;
  }

  private String keyPresence(String keyUnsafe, Generator generator) {
    return switch (representation) {
      case STRING_ARRAY -> anyElement(column(generator), like(ELEMENT, quoteLike(keyUnsafe) + ":%", generator));
      case CHILD_TABLE ->
        childRows(generator, " AND " + childColumn(childTable.keyColumn()) + " = " + generator.bind(keyUnsafe));
    };
  }

  private static String stringArrayQuery(Operator operator, String column, String keyUnsafe, String valueUnsafe,
      Generator generator) {
    String keyPattern = quoteLike(keyUnsafe) + ":";
    String wildcard = wildcardPattern(valueUnsafe);
    return switch (operator) {
      case HAS -> anyElement(column, like(ELEMENT, keyPattern + containsPattern(valueUnsafe), generator));
      case EQUALS -> anyElement(column, wildcard == null ? ELEMENT + " = " + generator.bind(keyUnsafe + ":" + valueUnsafe)
          : like(ELEMENT, keyPattern + wildcard, generator));
      case NOT_EQUALS -> {
        String present = like(ELEMENT, keyPattern + "%", generator);
        String compared = wildcard == null ? ELEMENT + " <> " + generator.bind(keyUnsafe + ":" + valueUnsafe)
            : notLike(ELEMENT, keyPattern + wildcard, generator);
        yield anyElement(column, present + " AND " + compared);
      }
      default -> throw new IllegalStateException("the generator refuses operator " + operator.symbol());
    };
  }

  private String childTableQuery(Operator operator, String keyUnsafe, String valueUnsafe, Generator generator) {
    String key = " AND " + childColumn(childTable.keyColumn()) + " = " + generator.bind(keyUnsafe);
    String value = childColumn(childTable.valueColumn());
    String compared = operator == Operator.HAS ? like(value, containsPattern(valueUnsafe), generator)
        : stringEquality(value, operator, valueUnsafe, generator);
    return childRows(generator, key + " AND " + compared);
  }

  private String childRows(Generator generator, String conditions) {
    return "(EXISTS (SELECT 1 FROM " + childTable.table() + " " + CHILD + " WHERE " + childColumn(childTable.parentColumn())
        + " = " + column(generator) + conditions + "))";
  }

  private static String childColumn(String name) {
    return CHILD + "." + name;
  }
}
