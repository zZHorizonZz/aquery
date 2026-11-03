package dev.horizon.aquery.cel.expression.operation;

import dev.horizon.aquery.cel.expression.Call;
import dev.horizon.aquery.cel.expression.Expression;
import dev.horizon.aquery.cel.expression.Expression.BinaryOperation;
import dev.horizon.aquery.cel.expression.Expression.Operator;
import dev.horizon.aquery.cel.expression.Next;
import dev.horizon.aquery.cel.expression.Operation;
import dev.horizon.aquery.cel.expression.Overload;
import java.util.List;

/** {@code a && b} or {@code a || b}: both conditions, or one or more of them. */
public final class Logic implements Operation {

  private final String function;
  private final Operator operator;

  /**
   * Translates the calls of one function.
   *
   * @param function the function, for example {@code _&&_}
   * @param operator the operator of the node
   */
  public Logic(String function, Operator operator) {
    this.function = function;
    this.operator = operator;
  }

  @Override
  public String function() {
    return function;
  }

  @Override
  public List<Overload> overloads() {
    return List.of();
  }

  @Override
  public Expression translate(Call call, Next next) {
    return new BinaryOperation(operator, call.argument(0), call.argument(1), call.type(), call.position());
  }
}
