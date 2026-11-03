package dev.horizon.aquery.cel.filter;

import dev.horizon.aquery.aip160.Filter.Condition;
import java.util.List;

record Chain(List<Operation> operations, Next last) implements Next {

  @Override
  public Condition translate(Call call) {
    if (operations.isEmpty()) {
      return last.translate(call);
    }
    return operations.getFirst().translate(call, new Chain(operations.subList(1, operations.size()), last));
  }
}
