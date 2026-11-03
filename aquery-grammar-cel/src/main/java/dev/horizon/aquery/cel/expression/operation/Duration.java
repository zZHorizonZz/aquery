package dev.horizon.aquery.cel.expression.operation;

import dev.horizon.aquery.cel.Type;
import dev.horizon.aquery.cel.expression.Call;
import dev.horizon.aquery.cel.expression.Expression;
import dev.horizon.aquery.cel.expression.Expression.Literal;
import dev.horizon.aquery.cel.expression.Next;
import dev.horizon.aquery.cel.expression.Operation;
import dev.horizon.aquery.cel.expression.Overload;
import dev.horizon.aquery.cel.internal.DurationText;
import java.util.List;

/** {@code duration('1h30m')}: a constant length of time, with the units h, m, s, ms, us and ns. */
public final class Duration implements Operation {

  @Override
  public String function() {
    return "duration";
  }

  @Override
  public List<Overload> overloads() {
    return List.of();
  }

  @Override
  public Expression translate(Call call, Next next) {
    if (!(call.argument(0) instanceof Literal(var _, String text, var _))) {
      throw call.refuse(0, "expected a string constant in duration(), as in duration(\"1h30m\")");
    }
    try {
      return new Literal(Type.Scalar.DURATION, new DurationText(text).duration(), call.position());
    } catch (IllegalArgumentException invalid) {
      throw call.refuse(0, "%s", invalid.getMessage());
    }
  }
}
