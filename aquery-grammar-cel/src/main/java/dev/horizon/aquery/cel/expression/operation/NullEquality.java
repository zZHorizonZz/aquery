package dev.horizon.aquery.cel.expression.operation;

import dev.horizon.aquery.cel.expression.Call;
import dev.horizon.aquery.cel.expression.Expression;
import dev.horizon.aquery.cel.expression.Expression.Not;
import dev.horizon.aquery.cel.expression.Next;
import dev.horizon.aquery.cel.expression.Operation;
import dev.horizon.aquery.cel.expression.Overload;
import java.util.List;

/**
 * {@code value == null}: the value is not set, which is the negation of its {@link Expression.Presence}. Only a
 * field that can be unset, a member that is optional or a key of a map can be compared with {@code null}.
 */
public final class NullEquality implements Operation {

  @Override
  public String function() {
    return "_==_";
  }

  @Override
  public List<Overload> overloads() {
    return NullOperand.OVERLOADS;
  }

  @Override
  public Expression translate(Call call, Next next) {
    NullOperand operand = new NullOperand(call);
    return operand.comparesNull() ? new Not(operand.presence(), call.position()) : next.translate(call);
  }
}
