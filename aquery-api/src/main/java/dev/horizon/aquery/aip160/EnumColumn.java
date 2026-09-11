package dev.horizon.aquery.aip160;

import java.util.Set;

/**
 * An enum field in an INT64 database column.
 *
 * <p>
 * The client writes the name of the value without quotes, for example {@code ACTIVE}. The enum definition gives
 * the number of that name. The SQL compares the column with the number. A value in quotes, as in
 * {@code "ACTIVE"}, is a string. The field refuses it. The field also refuses a name that is not in the
 * definition. The error message gives the names that the client can use.
 *
 * <p>
 * The field supports equality and inequality only. AIP-160 says that an enum field does not support the order
 * comparisons.
 *
 * @see <a href=
 * "https://chromium.googlesource.com/infra/luci/luci-go/+/main/common/data/aip160/enum_column.go">LUCI
 * aip160: enum_column.go (EnumColumn)</a>
 */
public class EnumColumn extends Int64Column {

  private final Args.EnumDefinition enumDefinition;

  public EnumColumn(String databaseName, Args.EnumDefinition enumDefinition) {
    super(databaseName);
    this.enumDefinition = enumDefinition;
  }

  @Override
  public String typeName() {
    return enumDefinition.typeName();
  }

  @Override
  public Set<Operator> operators() {
    return Operator.EQUALITY;
  }

  @Override
  public String restrictionQuery(RestrictionContext restriction, Generator generator) {
    int value = argument(restriction, arg -> Args.coerceToEnumConstant(arg, enumDefinition));
    return comparison(restriction, generator, generator.literal(value));
  }
}
