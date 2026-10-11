package dev.horizon.aquery.cel;

import dev.horizon.aquery.aip160.Filter.And;
import dev.horizon.aquery.aip160.Filter.Condition;
import dev.horizon.aquery.aip160.Filter.Or;
import dev.horizon.aquery.shaded.cel.common.ast.CelExpr;
import java.util.ArrayList;
import java.util.List;

/** {@code a && b} or {@code a || b} in a filter: all of the operands, or one or more of them. */
final class FilterJunction implements FilterOperation {

  private final boolean all;

  /**
   * Makes the operation.
   *
   * @param all true for {@code &&}, false for {@code ||}
   */
  FilterJunction(boolean all) {
    this.all = all;
  }

  @Override
  public Condition translate(CelExpr call, FilterTranslation translation) {
    List<Condition> operands = new ArrayList<>();
    for (CelExpr argument : call.call().args()) {
      Condition operand = translation.condition(argument);
      // cel-java balances a chain of && as a tree. One flat list gives shorter SQL with the same meaning.
      boolean same = argument.getKind() == CelExpr.ExprKind.Kind.CALL
          && argument.call().function().equals(call.call().function());
      if (same && operand instanceof And and) {
        operands.addAll(and.operands());
      } else if (same && operand instanceof Or or) {
        operands.addAll(or.operands());
      } else {
        operands.add(operand);
      }
    }
    return all ? new And(operands) : new Or(operands);
  }
}
