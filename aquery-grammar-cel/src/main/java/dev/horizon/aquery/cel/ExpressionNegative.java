package dev.horizon.aquery.cel;

import dev.horizon.aquery.cel.Expression.Literal;
import dev.horizon.aquery.cel.Expression.Negative;
import dev.horizon.aquery.shaded.cel.common.ast.CelExpr;

/** {@code -a}: the negative of a number or a duration. The negative of a constant number is a constant. */
final class ExpressionNegative implements ExpressionOperation {

  @Override
  public Expression translate(CelExpr call, ExpressionTranslation translation) {
    Expression operand = translation.expression(call.call().args().getFirst());
    int position = translation.position(call);
    if (operand instanceof Literal literal && literal.value() instanceof Long number && number != Long.MIN_VALUE) {
      return new Literal(Type.Scalar.INT, -number, position);
    }
    if (operand instanceof Literal literal && literal.value() instanceof Double number) {
      return new Literal(Type.Scalar.DOUBLE, -number, position);
    }
    return new Negative(operand, position);
  }
}
