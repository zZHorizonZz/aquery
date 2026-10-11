package dev.horizon.aquery.cel;

import dev.horizon.aquery.aip160.Filter.Condition;
import dev.horizon.aquery.aip160.Filter.Not;
import dev.horizon.aquery.shaded.cel.common.ast.CelExpr;

/** {@code !a} in a filter: the operand must be false. */
final class FilterNegation implements FilterOperation {

  @Override
  public Condition translate(CelExpr call, FilterTranslation translation) {
    return new Not(translation.condition(call.call().args().getFirst()));
  }
}
