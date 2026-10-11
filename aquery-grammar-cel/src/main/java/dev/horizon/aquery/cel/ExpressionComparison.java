package dev.horizon.aquery.cel;

import dev.horizon.aquery.cel.Expression.BinaryOperation;
import dev.horizon.aquery.cel.Expression.Operator;
import dev.horizon.aquery.shaded.cel.common.ast.CelExpr;

/**
 * A comparison {@code == != < <= > >=} of two values, whose types the checker of cel-java already found comparable.
 */
final class ExpressionComparison implements ExpressionOperation {

  private final Operator operator;

  ExpressionComparison(Operator operator) {
    this.operator = operator;
  }

  @Override
  public Expression translate(CelExpr call, ExpressionTranslation translation) {
    Expression left = translation.expression(call.call().args().get(0));
    Expression right = translation.expression(call.call().args().get(1));
    return new BinaryOperation(operator, left, right, translation.type(call), translation.position(call));
  }
}
