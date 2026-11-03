package dev.horizon.aquery.cel.expression.operation;

import dev.horizon.aquery.cel.expression.Call;
import dev.horizon.aquery.cel.expression.Expression;
import dev.horizon.aquery.cel.expression.Expression.BinaryOperation;
import dev.horizon.aquery.cel.expression.Expression.Literal;
import dev.horizon.aquery.cel.expression.Expression.Operator;
import dev.horizon.aquery.cel.expression.Next;
import dev.horizon.aquery.cel.expression.Operation;
import dev.horizon.aquery.cel.expression.Overload;
import java.util.List;

/**
 * {@code /} and {@code %} on numbers. The divisor must be a constant that is not zero, since the databases disagree
 * on a division by zero: some fail and some give NULL.
 */
public final class Division implements Operation {

  private final String function;
  private final Operator operator;
  private final String symbol;

  /**
   * Translates the calls of one function.
   *
   * @param function the function, for example {@code _/_}
   * @param operator the operator of the node
   * @param symbol the operator as the text writes it, for the errors
   */
  public Division(String function, Operator operator, String symbol) {
    this.function = function;
    this.operator = operator;
    this.symbol = symbol;
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
    Expression divisor = call.argument(1);
    boolean constant = divisor instanceof Literal(var _, Number number, var _) && number.doubleValue() != 0;
    if (!constant) {
      throw call.refuse(1, "the divisor of '%s' must be a constant that is not zero", symbol);
    }
    return new BinaryOperation(operator, call.argument(0), divisor, call.type(), call.position());
  }
}
