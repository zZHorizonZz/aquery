package dev.horizon.aquery.cel.expression.operation;

import dev.horizon.aquery.cel.expression.Call;
import dev.horizon.aquery.cel.expression.Expression;
import dev.horizon.aquery.cel.expression.Next;
import dev.horizon.aquery.cel.expression.Operation;
import dev.horizon.aquery.cel.expression.Overload;

import java.util.List;

/** {@code condition ? then : otherwise}: one of two values, whose types the type checker found to be the same. */
public final class Conditional implements Operation {

  @Override
  public String function() {
    return "_?_:_";
  }

  @Override
  public List<Overload> overloads() {
    return List.of();
  }

  @Override
  public Expression translate(Call call, Next next) {
    return new Expression.Conditional(call.argument(0), call.argument(1), call.argument(2), call.type(), call.position());
  }
}
