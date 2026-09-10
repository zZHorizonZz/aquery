package dev.horizon.aquery.aip160;

import dev.horizon.aquery.aip132.FieldPath;

/**
 * The error for a filter operator that the field does not support.
 *
 * <p>
 * Each backend declares its operators. A boolean supports equality and inequality, but not order comparisons. An
 * opaque string supports an exact match, but not a substring. The generator refuses all other operators with this
 * error. The message names the operator, the field and the field type. Thus the client knows what failed.
 *
 * @see <a href="https://chromium.googlesource.com/infra/luci/luci-go/+/main/common/data/aip160/field_backends.go">LUCI aip160:
 *      field_backends.go (OperatorNotImplementedError)</a>
 */
public final class OperatorNotImplementedException extends InvalidFilterException {

  public OperatorNotImplementedException(Operator operator, FieldPath fieldPath, String fieldType) {
    super("operator '%s' not implemented for field '%s' of type %s", operator.symbol(), fieldPath, fieldType);
  }
}
