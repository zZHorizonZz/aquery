package dev.horizon.aquery.cel;

import dev.horizon.aquery.cel.Expression.StringMatch;
import dev.horizon.aquery.shaded.cel.common.ast.CelExpr;
import dev.horizon.aquery.shaded.cel.common.ast.CelExpr.CelCall;

/** {@code s.startsWith(x)}, {@code s.endsWith(x)} or {@code s.contains(x)} on a string. */
final class ExpressionStringMatch implements ExpressionOperation {

  private final StringMatch.Kind kind;

  ExpressionStringMatch(StringMatch.Kind kind) {
    this.kind = kind;
  }

  @Override
  public Expression translate(CelExpr call, ExpressionTranslation translation) {
    CelCall method = call.call();
    if (method.target().isEmpty() || method.args().size() != 1) {
      throw translation.tree().refuse(call, "expected s.%s(x)", method.function());
    }
    Expression string = translation.expression(method.target().get());
    if (string.type() != Type.Scalar.STRING) {
      throw translation.tree().refuse(call, "%s() is supported only on strings", method.function());
    }
    return new StringMatch(kind, string, translation.expression(method.args().getFirst()), translation.position(call));
  }
}
