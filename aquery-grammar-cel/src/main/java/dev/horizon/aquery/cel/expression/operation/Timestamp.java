package dev.horizon.aquery.cel.expression.operation;

import dev.horizon.aquery.aip160.Args;
import dev.horizon.aquery.aip160.InvalidFilterException;
import dev.horizon.aquery.cel.Type;
import dev.horizon.aquery.cel.expression.Call;
import dev.horizon.aquery.cel.expression.Expression;
import dev.horizon.aquery.cel.expression.Expression.Literal;
import dev.horizon.aquery.cel.expression.Next;
import dev.horizon.aquery.cel.expression.Operation;
import dev.horizon.aquery.cel.expression.Overload;
import java.util.List;

/** {@code timestamp('2012-04-21T11:30:00Z')}: a constant instant, in RFC 3339. */
public final class Timestamp implements Operation {

  @Override
  public String function() {
    return "timestamp";
  }

  @Override
  public List<Overload> overloads() {
    return List.of();
  }

  @Override
  public Expression translate(Call call, Next next) {
    if (!(call.argument(0) instanceof Literal(var _, String text, var _))) {
      throw call.refuse(0, "expected a string constant in timestamp(), as in timestamp(\"2012-04-21T11:30:00Z\")");
    }
    try {
      return new Literal(Type.Scalar.TIMESTAMP, Args.parseTimestamp(text).toInstant(), call.position());
    } catch (InvalidFilterException invalid) {
      throw call.refuse(0, "%s", invalid.getMessage());
    }
  }
}
