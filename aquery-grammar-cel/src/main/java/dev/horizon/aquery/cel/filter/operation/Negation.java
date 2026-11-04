package dev.horizon.aquery.cel.filter.operation;

import dev.horizon.aquery.aip160.Filter.Condition;
import dev.horizon.aquery.aip160.Filter.Not;
import dev.horizon.aquery.cel.filter.Call;
import dev.horizon.aquery.cel.filter.Next;
import dev.horizon.aquery.cel.filter.Operation;

/** {@code !a}: the condition must be false. */
public final class Negation implements Operation {

  @Override
  public String function() {
    return "!_";
  }

  @Override
  public Condition translate(Call call, Next next) {
    return new Not(call.argument(0).condition());
  }
}
