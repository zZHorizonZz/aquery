package dev.horizon.aquery.cel.expression;

import dev.horizon.aquery.cel.expression.Expression.Row;
import dev.horizon.aquery.cel.internal.CelTree;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.function.Supplier;

final class Namespace {

  private final CelEnvironment environment;
  private final Name.RowName row;
  private final Deque<Map.Entry<String, Name>> variables = new ArrayDeque<>();

  Namespace(CelEnvironment environment) {
    this.environment = environment;
    this.row = new Name.RowName(environment.scope(), Row.CURRENT, "");
  }

  Name find(String name, Site site) {
    for (Map.Entry<String, Name> variable : variables) {
      if (variable.getKey().equals(name)) {
        return variable.getValue();
      }
    }
    return switch (name) {
      case Checker.PARAMS -> new Name.ParameterName(environment.parameters());
      case CelTree.RECORD -> row;
      default -> environment.row(name).<Name> map(scope -> new Name.NamedRowName(new Row.Named(name), scope))
          .orElseGet(() -> row.member(name, site));
    };
  }

  Expression within(String name, Name binding, Supplier<Expression> body) {
    variables.push(Map.entry(name, binding));
    try {
      return body.get();
    } finally {
      variables.pop();
    }
  }
}
