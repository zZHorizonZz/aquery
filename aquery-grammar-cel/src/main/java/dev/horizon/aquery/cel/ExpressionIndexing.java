package dev.horizon.aquery.cel;

import dev.horizon.aquery.cel.Expression.IndexAccess;
import dev.horizon.aquery.shaded.cel.common.ast.CelExpr;

/** {@code labels['tier']} or {@code tags[0]}: a key of a map field or an element of a list field. */
final class ExpressionIndexing implements ExpressionOperation {

  @Override
  public Expression translate(CelExpr call, ExpressionTranslation translation) {
    CelExpr keyNode = call.call().args().get(1);
    Expression key = translation.expression(keyNode);
    if (!translation.isConstant(key)) {
      throw translation.tree().refuse(keyNode, "an index or a key must be a constant or a parameter");
    }
    return translation.inside(translation.expression(call.call().args().get(0)), operand -> {
      if (!translation.isJson(operand)) {
        throw translation.tree().refuse(call, "only a list or a map field can be indexed");
      }
      return new IndexAccess(operand, key, translation.type(call), translation.position(call));
    });
  }
}
