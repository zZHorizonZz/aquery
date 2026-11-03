package dev.horizon.aquery.cel.expression;

import java.util.List;

record Chain(List<Operation> operations, Next last) implements Next {

  @Override
  public Expression translate(Call call) {
    if (operations.isEmpty()) {
      return last.translate(call);
    }
    return operations.getFirst().translate(call, new Chain(operations.subList(1, operations.size()), last));
  }
}
