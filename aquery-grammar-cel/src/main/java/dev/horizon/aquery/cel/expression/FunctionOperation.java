package dev.horizon.aquery.cel.expression;

import dev.horizon.aquery.cel.expression.Expression.FunctionCall;
import java.util.ArrayList;
import java.util.List;

record FunctionOperation(Function declaration) implements Operation {

  @Override
  public String function() {
    return declaration.name();
  }

  @Override
  public List<Overload> overloads() {
    return List.of(declaration.overload());
  }

  @Override
  public Expression translate(Call call, Next next) {
    if (call.overload().filter(declaration.overload()::equals).isEmpty()) {
      return next.translate(call);
    }
    List<Expression> arguments = new ArrayList<>();
    call.target().ifPresent(arguments::add);
    arguments.addAll(call.arguments());
    return new FunctionCall(declaration, arguments, call.position());
  }
}
