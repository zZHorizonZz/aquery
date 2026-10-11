package dev.horizon.aquery.cel;

import dev.horizon.aquery.cel.Expression.BinaryOperation;
import dev.horizon.aquery.cel.Expression.Operator;
import dev.horizon.aquery.shaded.cel.common.ast.CelExpr;

/** {@code a && b} or {@code a || b}: both conditions, or one or more of them. */
final class ExpressionLogic implements ExpressionOperation {

  private final Operator operator;

  ExpressionLogic(Operator operator) {
    this.operator = operator;
  }

  @Override
  public Expression translate(CelExpr call, ExpressionTranslation translation) {
    Expression left = translation.expression(call.call().args().get(0));
    Expression right = translation.expression(call.call().args().get(1));
    return new BinaryOperation(operator, left, right, translation.type(call), translation.position(call));
  }
}
