package dev.horizon.aquery.cel.filter.operation;

import dev.horizon.aquery.aip160.Filter.Condition;
import dev.horizon.aquery.aip160.Filter.Restriction;
import dev.horizon.aquery.aip160.Operator;
import dev.horizon.aquery.cel.filter.Call;
import dev.horizon.aquery.cel.filter.Next;
import dev.horizon.aquery.cel.filter.Operand;
import dev.horizon.aquery.cel.filter.Operation;

/**
 * A comparison {@code == != < <= > >=} of a field and a constant. The constant can be on the left side, as in
 * {@code 18 <= age}, which is {@code age >= 18}.
 */
public final class Comparison implements Operation {

  private final String function;
  private final Operator operator;
  private final Operator flipped;

  /**
   * Translates the calls of one function.
   *
   * @param function the function, for example {@code _<=_}
   * @param operator the operator if the field is on the left side
   * @param flipped the operator if the field is on the right side
   */
  public Comparison(String function, Operator operator, Operator flipped) {
    this.function = function;
    this.operator = operator;
    this.flipped = flipped;
  }

  @Override
  public String function() {
    return function;
  }

  @Override
  public Condition translate(Call call, Next next) {
    Operand left = call.argument(0);
    Operand right = call.argument(1);
    if (left.isField()) {
      return new Restriction(left.path(), operator, right.value());
    }
    if (right.isField()) {
      return new Restriction(right.path(), flipped, left.value());
    }
    throw call.refuse("expected a field on one side of '%s' but found %s and %s", call.symbol(), left.describe(),
        right.describe());
  }
}
