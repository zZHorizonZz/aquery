package dev.horizon.aquery.cel.expression.operation;

import dev.horizon.aquery.cel.expression.Call;
import dev.horizon.aquery.cel.expression.Expression;
import dev.horizon.aquery.cel.expression.Next;
import dev.horizon.aquery.cel.expression.Operation;
import dev.horizon.aquery.cel.expression.Overload;
import java.util.List;

/** A function that an expression cannot call, which the type checker knows, with the reason that it is refused. */
public final class Refused implements Operation {

  private final String function;
  private final String reason;

  /**
   * Refuses a function.
   *
   * @param function the name of the function, for example {@code matches}
   * @param reason the message of the error
   */
  public Refused(String function, String reason) {
    this.function = function;
    this.reason = reason;
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
    throw call.refuse("%s", reason);
  }
}
