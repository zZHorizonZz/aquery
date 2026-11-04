package dev.horizon.aquery.cel.expression.operation;

import dev.horizon.aquery.cel.expression.Call;
import dev.horizon.aquery.cel.expression.Expression;
import dev.horizon.aquery.cel.expression.Expression.IndexAccess;
import dev.horizon.aquery.cel.expression.Next;
import dev.horizon.aquery.cel.expression.Operation;
import dev.horizon.aquery.cel.expression.Overload;
import java.util.List;

/** {@code value[key]}: an element of a list field or the value of a key of a map field, by a constant key. */
public final class Indexing implements Operation {

  @Override
  public String function() {
    return "_[_]";
  }

  @Override
  public List<Overload> overloads() {
    return List.of();
  }

  @Override
  public Expression translate(Call call, Next next) {
    Expression key = call.argument(1);
    if (!key.constant()) {
      throw call.refuse(1, "an index or a key must be a constant or a parameter");
    }
    return call.argument(0).inside(operand -> {
      if (!operand.json()) {
        throw call.refuse("only a list or a map field can be indexed");
      }
      return new IndexAccess(operand, key, call.type(), call.position());
    });
  }
}
