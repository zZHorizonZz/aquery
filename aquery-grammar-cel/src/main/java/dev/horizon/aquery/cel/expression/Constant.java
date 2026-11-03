package dev.horizon.aquery.cel.expression;

import dev.horizon.aquery.cel.Type;

import dev.horizon.aquery.cel.expression.Expression.Literal;
import dev.horizon.aquery.shaded.cel.common.ast.CelConstant;

record Constant(CelConstant constant, Site site) {

  Literal literal() {
    return switch (constant.getKind()) {
      case BOOLEAN_VALUE -> literal(Type.Scalar.BOOL, constant.booleanValue());
      case INT64_VALUE -> literal(Type.Scalar.INT, constant.int64Value());
      case DOUBLE_VALUE -> real(constant.doubleValue());
      case STRING_VALUE -> literal(Type.Scalar.STRING, constant.stringValue());
      case BYTES_VALUE -> literal(Type.Scalar.BYTES, constant.bytesValue().toByteArray());
      case UINT64_VALUE -> throw site.refuse("unsigned integers are not supported; write the number without 'u'");
      case NULL_VALUE -> throw site.refuse("null is supported only after == or !=, as in expire_time == null");
      default -> throw site.refuse("the constant is not supported");
    };
  }

  private Literal real(double value) {
    if (!Double.isFinite(value)) {
      throw site.refuse("a double must be finite");
    }
    return literal(Type.Scalar.DOUBLE, value);
  }

  private Literal literal(Type type, Object value) {
    return new Literal(type, value, site.position());
  }
}
