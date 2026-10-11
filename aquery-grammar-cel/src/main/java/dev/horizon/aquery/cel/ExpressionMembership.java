package dev.horizon.aquery.cel;

import dev.horizon.aquery.cel.Expression.ListLiteral;
import dev.horizon.aquery.cel.Expression.Membership;
import dev.horizon.aquery.cel.Expression.Parameter;
import dev.horizon.aquery.shaded.cel.common.ast.CelExpr;

/**
 * {@code a in b}: a value is an element of a list, a list parameter or a list field, or a key of a map field. A key
 * in a map must be a constant or a parameter.
 */
final class ExpressionMembership implements ExpressionOperation {

  @Override
  public Expression translate(CelExpr call, ExpressionTranslation translation) {
    Expression element = translation.expression(call.call().args().get(0));
    CelExpr collectionNode = call.call().args().get(1);
    return translation.inside(translation.expression(collectionNode), collection -> {
      if (collection instanceof ListLiteral || collection instanceof Parameter) {
        return new Membership(element, collection, translation.position(call));
      }
      if (!translation.isJson(collection)) {
        throw translation.tree().refuse(collectionNode,
            "expected a list, a list parameter, or a list or map field after 'in'");
      }
      if (collection.type() instanceof Type.MapOf && !translation.isConstant(element)) {
        throw translation.tree().refuse(call.call().args().get(0), "a key 'in' a map must be a constant or a parameter");
      }
      return new Membership(element, collection, translation.position(call));
    });
  }
}
