package dev.horizon.aquery.cel;

import dev.horizon.aquery.aip160.Filter.Condition;
import dev.horizon.aquery.aip160.Filter.Restriction;
import dev.horizon.aquery.aip160.Operator;
import dev.horizon.aquery.shaded.cel.common.ast.CelExpr;
import dev.horizon.aquery.shaded.cel.common.ast.CelExpr.CelCall;

/** {@code name.startsWith("x")}, {@code name.endsWith("x")} or {@code name.contains("x")} in a filter. */
final class FilterStringMatch implements FilterOperation {

  private final Operator operator;

  /**
   * Makes the operation.
   *
   * @param operator the operator of the restriction: {@link Operator#STARTS_WITH}, {@link Operator#ENDS_WITH}, or
   * {@link Operator#HAS} for {@code contains}
   */
  FilterStringMatch(Operator operator) {
    this.operator = operator;
  }

  @Override
  public Condition translate(CelExpr call, FilterTranslation translation) {
    CelTree tree = translation.tree();
    CelCall method = call.call();
    if (method.target().isEmpty() || method.args().size() != 1) {
      throw tree.refuse(call, "expected %s() on a field with one argument, as in name.%s(\"x\")", method.function(),
          method.function());
    }
    CelExpr target = method.target().get();
    if (!translation.isField(target)) {
      throw tree.refuse(target, "expected a field before .%s() but found %s", method.function(), tree.describe(target));
    }
    return new Restriction(translation.fieldPath(target), operator, translation.value(method.args().getFirst()));
  }
}
