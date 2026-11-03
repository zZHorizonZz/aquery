package dev.horizon.aquery.cel.filter.operation;

import dev.horizon.aquery.aip160.Filter.Condition;
import dev.horizon.aquery.aip160.Filter.Restriction;
import dev.horizon.aquery.aip160.Operator;
import dev.horizon.aquery.cel.filter.Call;
import dev.horizon.aquery.cel.filter.Next;
import dev.horizon.aquery.cel.filter.Operand;
import dev.horizon.aquery.cel.filter.Operation;

/** {@code name.contains("x")}, {@code name.startsWith("x")} or {@code name.endsWith("x")} on a field. */
public final class StringMatch implements Operation {

  private final String function;
  private final Operator operator;

  /**
   * Translates the calls of one function.
   *
   * @param function the function, for example {@code startsWith}
   * @param operator the operator of the restriction
   */
  public StringMatch(String function, Operator operator) {
    this.function = function;
    this.operator = operator;
  }

  @Override
  public String function() {
    return function;
  }

  @Override
  public Condition translate(Call call, Next next) {
    Operand target = call.target().filter(_ -> call.arguments().size() == 1).orElseThrow(() -> call.refuse(
        "expected %s() on a field with one argument, as in name.%s(\"x\")", function, function));
    if (!target.isField()) {
      throw target.refuse("expected a field before .%s() but found %s", function, target.describe());
    }
    return new Restriction(target.path(), operator, call.argument(0).value());
  }
}
