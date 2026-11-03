package dev.horizon.aquery.cel.expression.operation;

import dev.horizon.aquery.cel.expression.Call;
import dev.horizon.aquery.cel.expression.Expression;
import dev.horizon.aquery.cel.expression.Expression.Literal;
import dev.horizon.aquery.cel.expression.Next;
import dev.horizon.aquery.cel.expression.Operation;
import dev.horizon.aquery.cel.expression.Overload;
import java.util.List;

/** {@code -a}: the negative of a number or a duration. The negative of a constant number is a constant. */
public final class Negative implements Operation {

  @Override
  public String function() {
    return "-_";
  }

  @Override
  public List<Overload> overloads() {
    return List.of();
  }

  @Override
  public Expression translate(Call call, Next next) {
    return switch (call.argument(0)) {
      case Literal(var type, Long number, var _) when number != Long.MIN_VALUE ->
        new Literal(type, -number, call.position());
      case Literal(var type, Double number, var _) -> new Literal(type, -number, call.position());
      case Expression operand -> new Expression.Negative(operand, call.position());
    };
  }
}
