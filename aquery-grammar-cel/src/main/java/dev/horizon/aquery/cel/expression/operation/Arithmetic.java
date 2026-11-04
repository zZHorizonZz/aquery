package dev.horizon.aquery.cel.expression.operation;

import dev.horizon.aquery.cel.Type;
import dev.horizon.aquery.cel.expression.Call;
import dev.horizon.aquery.cel.expression.Expression;
import dev.horizon.aquery.cel.expression.Expression.BinaryOperation;
import dev.horizon.aquery.cel.expression.Expression.Operator;
import dev.horizon.aquery.cel.expression.Next;
import dev.horizon.aquery.cel.expression.Operation;
import dev.horizon.aquery.cel.expression.Overload;
import java.util.List;
import java.util.Set;

/**
 * {@code + - *} on numbers, {@code +} on strings, a timestamp plus or minus a duration, and the duration between two
 * timestamps.
 */
public final class Arithmetic implements Operation {

  /** The results of {@code - *}: numbers, timestamps and durations. */
  public static final Set<Type> NUMBERS = Set.of(Type.Scalar.INT, Type.Scalar.DOUBLE, Type.Scalar.TIMESTAMP,
      Type.Scalar.DURATION);

  /** The results of {@code +}: numbers, timestamps, durations and strings. */
  public static final Set<Type> SUMS = Set.of(Type.Scalar.INT, Type.Scalar.DOUBLE, Type.Scalar.TIMESTAMP,
      Type.Scalar.DURATION, Type.Scalar.STRING);

  private final String function;
  private final Operator operator;
  private final String symbol;
  private final Set<Type> types;

  /**
   * Translates the calls of one function.
   *
   * @param function the function, for example {@code _+_}
   * @param operator the operator of the node
   * @param symbol the operator as the text writes it, for the errors
   * @param types the types of the result that the operator supports, as {@link #NUMBERS} or {@link #SUMS}
   */
  public Arithmetic(String function, Operator operator, String symbol, Set<Type> types) {
    this.function = function;
    this.operator = operator;
    this.symbol = symbol;
    this.types = types;
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
    Expression left = call.argument(0);
    if (!types.contains(call.type())) {
      throw call.refuse("'%s' on a %s is not supported in an expression", symbol, left.type().describe());
    }
    return new BinaryOperation(operator, left, call.argument(1), call.type(), call.position());
  }
}
