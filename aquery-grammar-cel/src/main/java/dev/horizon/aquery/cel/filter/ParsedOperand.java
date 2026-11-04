package dev.horizon.aquery.cel.filter;

import dev.horizon.aquery.aip132.FieldPath;
import dev.horizon.aquery.aip160.Filter.Condition;
import dev.horizon.aquery.aip160.Filter.Value;
import dev.horizon.aquery.aip160.InvalidFilterException;
import dev.horizon.aquery.shaded.cel.common.ast.CelExpr;
import java.util.List;
import java.util.Optional;

record ParsedOperand(Site site, Translation translation) implements Operand {

  @Override
  public boolean isField() {
    return new FieldPathNode(site.node()).valid();
  }

  @Override
  public FieldPath path() {
    FieldPathNode path = new FieldPathNode(site.node());
    if (!path.valid()) {
      throw site.refuse("expected a field but found %s", site.describe());
    }
    return new FieldPath(path.segments());
  }

  @Override
  public Value value() {
    return new ValueNode(site).value();
  }

  @Override
  public Condition condition() {
    return translation.condition(site.node());
  }

  @Override
  public Optional<List<Operand>> list() {
    if (site.node().getKind() != CelExpr.ExprKind.Kind.LIST) {
      return Optional.empty();
    }
    return Optional.of(site.node().list().elements().stream()
        .<Operand> map(element -> new ParsedOperand(site.at(element), translation)).toList());
  }

  @Override
  public boolean calls(String function) {
    return site.node().getKind() == CelExpr.ExprKind.Kind.CALL && site.node().call().function().equals(function);
  }

  @Override
  public String describe() {
    return site.describe();
  }

  @Override
  public InvalidFilterException refuse(String message, Object... arguments) {
    return site.refuse(message, arguments);
  }
}
