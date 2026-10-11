package dev.horizon.aquery.cel;

import dev.horizon.aquery.cel.Expression.Literal;
import dev.horizon.aquery.shaded.cel.common.ast.CelExpr;

/** {@code timestamp('2024-01-01T00:00:00Z')} or {@code duration('1h30m')} of a constant: a constant time. */
final class ExpressionConversion implements ExpressionOperation {

  private final Type.Scalar type;

  /**
   * Makes the operation.
   *
   * @param type {@link Type.Scalar#TIMESTAMP} or {@link Type.Scalar#DURATION}
   */
  ExpressionConversion(Type.Scalar type) {
    this.type = type;
  }

  @Override
  public Expression translate(CelExpr call, ExpressionTranslation translation) {
    CelTree tree = translation.tree();
    Object value = type == Type.Scalar.TIMESTAMP ? tree.timestamp(call).toInstant() : tree.duration(call);
    return new Literal(type, value, translation.position(call));
  }
}
