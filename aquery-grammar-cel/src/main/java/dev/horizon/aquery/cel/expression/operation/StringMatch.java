package dev.horizon.aquery.cel.expression.operation;

import dev.horizon.aquery.cel.Type;
import dev.horizon.aquery.cel.expression.Call;
import dev.horizon.aquery.cel.expression.Expression;
import dev.horizon.aquery.cel.expression.Next;
import dev.horizon.aquery.cel.expression.Operation;
import dev.horizon.aquery.cel.expression.Overload;
import java.util.List;

/** {@code s.startsWith(x)}, {@code s.endsWith(x)} or {@code s.contains(x)} on a string. */
public final class StringMatch implements Operation {

  private final String function;
  private final Expression.StringMatch.Kind kind;

  /**
   * Translates the calls of one function.
   *
   * @param function the function, for example {@code startsWith}
   * @param kind the kind of the match of the node
   */
  public StringMatch(String function, Expression.StringMatch.Kind kind) {
    this.function = function;
    this.kind = kind;
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
    Expression string = call.target().orElseThrow(() -> call.refuse("expected s.%s(x)", function));
    if (string.type() != Type.Scalar.STRING) {
      throw call.refuse("%s() is supported only on strings", function);
    }
    return new Expression.StringMatch(kind, string, call.argument(0), call.position());
  }
}
