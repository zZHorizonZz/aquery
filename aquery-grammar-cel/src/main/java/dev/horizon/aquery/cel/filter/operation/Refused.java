package dev.horizon.aquery.cel.filter.operation;

import dev.horizon.aquery.aip160.Filter.Condition;
import dev.horizon.aquery.cel.filter.Call;
import dev.horizon.aquery.cel.filter.Next;
import dev.horizon.aquery.cel.filter.Operation;

/** A function that a filter cannot call, with the reason that it is refused. */
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
  public Condition translate(Call call, Next next) {
    throw call.refuse("%s", reason);
  }
}
