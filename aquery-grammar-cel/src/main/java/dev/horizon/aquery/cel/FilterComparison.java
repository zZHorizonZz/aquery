package dev.horizon.aquery.cel;

import dev.horizon.aquery.aip160.Filter.Condition;
import dev.horizon.aquery.aip160.Filter.Restriction;
import dev.horizon.aquery.aip160.Operator;
import dev.horizon.aquery.shaded.cel.common.ast.CelExpr;

/**
 * A comparison in a filter, as {@code age >= 18}, between a field and a constant. The constant can be on the left
 * side, as in {@code 18 <= age}, which compares the other way.
 */
final class FilterComparison implements FilterOperation {

  private final Operator operator;
  private final Operator flipped;

  /**
   * Makes the operation.
   *
   * @param operator the operator of the restriction when the field is on the left side
   * @param flipped the operator when the field is on the right side
   */
  FilterComparison(Operator operator, Operator flipped) {
    this.operator = operator;
    this.flipped = flipped;
  }

  @Override
  public Condition translate(CelExpr call, FilterTranslation translation) {
    CelExpr left = call.call().args().get(0);
    CelExpr right = call.call().args().get(1);
    if (translation.isField(left)) {
      return new Restriction(translation.fieldPath(left), operator, translation.value(right));
    }
    if (translation.isField(right)) {
      return new Restriction(translation.fieldPath(right), flipped, translation.value(left));
    }
    CelTree tree = translation.tree();
    throw tree.refuse(call, "expected a field on one side of '%s' but found %s and %s",
        tree.symbol(call.call().function()), tree.describe(left), tree.describe(right));
  }
}
