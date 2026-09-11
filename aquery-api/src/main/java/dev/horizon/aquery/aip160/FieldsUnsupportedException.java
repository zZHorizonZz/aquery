package dev.horizon.aquery.aip160;

import dev.horizon.aquery.aip132.FieldPath;

/**
 * The error for a filter that goes into a field with a dot, if the field has no members.
 *
 * <p>
 * Only a key-value field has members. The generator refuses the dot for all other fields. The message tells the
 * client to remove the dot.
 *
 * @see <a href="https://chromium.googlesource.com/infra/luci/luci-go/+/main/common/data/aip160/field_backends.go">LUCI aip160: field_backends.go (FieldsUnsupportedError)</a>
 */
public final class FieldsUnsupportedException extends InvalidFilterException {

  public FieldsUnsupportedException(FieldPath fieldPath) {
    super("fields are only supported for key-value columns. Try removing the '.' from after your field named '%s'", fieldPath);
  }
}
