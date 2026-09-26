package dev.horizon.aquery.aip160;

import java.util.Set;

/**
 * A repeated string field in an ISO SQL array of strings, for example {@code VARCHAR ARRAY} or {@code TEXT[]}.
 *
 * <p>
 * The field supports the has operator and equality. The SQL uses EXISTS on the UNNEST of the array, because the
 * restriction is true if one element matches. Each restriction binds a {@link String}:
 *
 * <ul>
 * <li>{@code tags : "prod"} is true if an element contains {@code prod}.
 * <li>{@code tags = "prod"} is true if an element is equal to {@code prod}. A {@code *} at the start or at the end
 * of the value is a wildcard.
 * <li>{@code tags : *} is true if the array has elements, as AIP-160 specifies. The SQL uses CARDINALITY.
 * </ul>
 *
 * <p>
 * The has operator with a value matches a substring of an element, as in LUCI. AIP-160 says that it matches an
 * equal element.
 *
 * <p>
 * Only engines with array columns, for example PostgreSQL and H2, can run this SQL. An order_by clause cannot sort
 * by the field. A bare value in the filter cannot match the field.
 *
 * @see <a href="https://chromium.googlesource.com/infra/luci/luci-go/+/main/common/data/aip160/repeated_string_column.go">LUCI aip160: repeated_string_column.go (RepeatedStringColumn)</a>
 */
public class RepeatedStringColumn extends SimpleColumn {

  private static final Set<Operator> OPERATORS = Set.of(Operator.HAS, Operator.EQUALS);

  public RepeatedStringColumn(String databaseName) {
    super(databaseName);
  }

  @Override
  public String typeName() {
    return "REPEATED STRING";
  }

  @Override
  public Set<Operator> operators() {
    return OPERATORS;
  }

  @Override
  public String restrictionQuery(RestrictionContext restriction, Generator generator) {
    String column = column(generator);
    if (restriction.operator() == Operator.HAS && Args.isPresenceWildcard(restriction.arg())) {
      return "(CARDINALITY(" + column + ") > 0)";
    }
    String value = argument(restriction, Args::coerceToStringConstant);
    String condition = restriction.operator() == Operator.HAS ? like(ELEMENT, containsPattern(value), generator)
        : stringEquality(ELEMENT, Operator.EQUALS, value, generator);
    return anyElement(column, condition);
  }

  @Override
  public boolean supportsSorting() {
    return false;
  }
}
