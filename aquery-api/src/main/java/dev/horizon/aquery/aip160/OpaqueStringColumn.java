package dev.horizon.aquery.aip160;

import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * A string field with values that the server encodes before it keeps them, for example with a hash.
 *
 * <p>
 * The encode function changes the value from the client into the value in the column. The restriction binds the
 * encoded value as a {@link String}. The client does not see the encoding, and the client does not write it.
 *
 * <p>
 * The field supports equality and inequality only. A substring match on an encoded value cannot work. The client
 * must write the encoded form of the substring, and the client must not know the encoding. For the same reason,
 * the field does not support wildcards.
 *
 * <p>
 * An order_by clause cannot sort by the field. The database order of the encoded values is not the logical order
 * of the field. A bare value in the filter cannot match the field.
 *
 * @see <a href="https://chromium.googlesource.com/infra/luci/luci-go/+/main/common/data/aip160/opaque_string_column.go">LUCI aip160: opaque_string_column.go (OpaqueStringColumn)</a>
 */
public class OpaqueStringColumn extends SimpleColumn {

  private final UnaryOperator<String> encodeFunction;

  public OpaqueStringColumn(String databaseName, UnaryOperator<String> encodeFunction) {
    super(databaseName);
    this.encodeFunction = encodeFunction;
  }

  @Override
  public String typeName() {
    return "OPAQUE STRING";
  }

  @Override
  public Set<Operator> operators() {
    return Operator.EQUALITY;
  }

  @Override
  public String restrictionQuery(RestrictionContext restriction, Generator generator) {
    String value = argument(restriction, Args::coerceToStringConstant);
    return comparison(restriction, generator, generator.bind(encodeFunction.apply(value)));
  }

  @Override
  public boolean supportsSorting() {
    return false;
  }
}
