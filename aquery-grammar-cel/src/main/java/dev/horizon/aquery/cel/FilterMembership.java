package dev.horizon.aquery.cel;

import static dev.horizon.aquery.aip160.Operator.EQUALS;
import static dev.horizon.aquery.aip160.Operator.IN;

import dev.horizon.aquery.aip132.FieldPath;
import dev.horizon.aquery.aip160.Filter.Condition;
import dev.horizon.aquery.aip160.Filter.Or;
import dev.horizon.aquery.aip160.Filter.Restriction;
import dev.horizon.aquery.shaded.cel.common.ast.CelExpr;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code in} in a filter: {@code status in [ACTIVE, PENDING]} is one of the constants of a list, and
 * {@code "x" in tags} tells if a field has the value as an element or as a key.
 */
final class FilterMembership implements FilterOperation {

  @Override
  public Condition translate(CelExpr call, FilterTranslation translation) {
    CelTree tree = translation.tree();
    CelExpr element = call.call().args().get(0);
    CelExpr collection = call.call().args().get(1);
    if (collection.getKind() == CelExpr.ExprKind.Kind.LIST) {
      if (!translation.isField(element)) {
        throw tree.refuse(element, "expected a field before 'in' a list but found %s", tree.describe(element));
      }
      List<CelExpr> elements = collection.list().elements();
      if (elements.isEmpty()) {
        throw tree.refuse(collection, "the list after 'in' is empty");
      }
      FieldPath field = translation.fieldPath(element);
      List<Condition> equalities = new ArrayList<>(elements.size());
      for (CelExpr value : elements) {
        equalities.add(new Restriction(field, EQUALS, translation.value(value)));
      }
      return equalities.size() == 1 ? equalities.getFirst() : new Or(equalities);
    }
    if (translation.isField(collection)) {
      return new Restriction(translation.fieldPath(collection), IN, translation.value(element));
    }
    throw tree.refuse(collection, "expected a field or a list after 'in' but found %s", tree.describe(collection));
  }
}
