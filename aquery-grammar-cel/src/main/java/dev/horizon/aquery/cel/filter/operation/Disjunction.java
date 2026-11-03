package dev.horizon.aquery.cel.filter.operation;

import dev.horizon.aquery.aip160.Filter.Condition;
import dev.horizon.aquery.aip160.Filter.Or;
import dev.horizon.aquery.cel.filter.Call;
import dev.horizon.aquery.cel.filter.Next;
import dev.horizon.aquery.cel.filter.Operand;
import dev.horizon.aquery.cel.filter.Operation;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code a || b}: one or more of the conditions. A chain of {@code ||} becomes one flat {@link Or}, as a chain of
 * {@code &&} becomes one {@link dev.horizon.aquery.aip160.Filter.And}.
 */
public final class Disjunction implements Operation {

  @Override
  public String function() {
    return "_||_";
  }

  @Override
  public Condition translate(Call call, Next next) {
    List<Condition> operands = new ArrayList<>();
    for (Operand argument : call.arguments()) {
      switch (argument.condition()) {
        case Or or when argument.calls(function()) -> operands.addAll(or.operands());
        case Condition condition -> operands.add(condition);
      }
    }
    return new Or(operands);
  }
}
