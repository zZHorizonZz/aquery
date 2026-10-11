package dev.horizon.aquery.cel;

import dev.horizon.aquery.cel.Expression.Not;
import dev.horizon.aquery.shaded.cel.common.ast.CelExpr;

/** {@code !a}: the condition must be false. */
final class ExpressionNegation implements ExpressionOperation {

  @Override
  public Expression translate(CelExpr call, ExpressionTranslation translation) {
    return new Not(translation.expression(call.call().args().getFirst()), translation.position(call));
  }
}
