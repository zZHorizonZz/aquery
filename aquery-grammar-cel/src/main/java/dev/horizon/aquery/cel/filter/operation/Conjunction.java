package dev.horizon.aquery.cel.filter.operation;

import dev.horizon.aquery.aip160.Filter.And;
import dev.horizon.aquery.aip160.Filter.Condition;
import dev.horizon.aquery.cel.filter.Call;
import dev.horizon.aquery.cel.filter.Next;
import dev.horizon.aquery.cel.filter.Operand;
import dev.horizon.aquery.cel.filter.Operation;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code a && b}: both conditions. cel-java balances a chain of {@code &&} as a tree, and the chain becomes one flat
 * {@link And}, which gives shorter SQL with the same meaning.
 */
public final class Conjunction implements Operation {

  @Override
  public String function() {
    return "_&&_";
  }

  @Override
  public Condition translate(Call call, Next next) {
    List<Condition> operands = new ArrayList<>();
    for (Operand argument : call.arguments()) {
      switch (argument.condition()) {
        case And and when argument.calls(function()) -> operands.addAll(and.operands());
        case Condition condition -> operands.add(condition);
      }
    }
    return new And(operands);
  }
}
