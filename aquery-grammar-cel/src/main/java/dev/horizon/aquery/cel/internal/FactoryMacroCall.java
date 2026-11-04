package dev.horizon.aquery.cel.internal;

import dev.horizon.aquery.cel.MacroCall;
import dev.horizon.aquery.cel.Syntax;
import dev.horizon.aquery.shaded.cel.common.ast.CelExpr;
import dev.horizon.aquery.shaded.cel.parser.CelMacroExprFactory;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

record FactoryMacroCall(CelMacroExprFactory factory, CelExpr receiver, List<CelExpr> arguments) implements MacroCall {

  @Override
  public Optional<Syntax> target() {
    return receiver == null || receiver.getKind() == CelExpr.ExprKind.Kind.NOT_SET ? Optional.empty()
        : Optional.of(new CelSyntax(receiver));
  }

  @Override
  public Syntax argument(int index) {
    return new CelSyntax(arguments.get(index));
  }

  @Override
  public Syntax row() {
    return new CelSyntax(factory.newIdentifier(CelTree.RECORD));
  }

  @Override
  public Syntax select(Syntax operand, String field) {
    return new CelSyntax(factory.newSelect(CelSyntax.node(operand), field, false));
  }

  @Override
  public Syntax presence(Syntax operand, String field) {
    return new CelSyntax(factory.newSelect(CelSyntax.node(operand), field, true));
  }

  @Override
  public Syntax call(String function, Syntax... arguments) {
    return new CelSyntax(factory.newGlobalCall(function, Stream.of(arguments).map(CelSyntax::node).toList()));
  }

  @Override
  public Syntax refuse(String message) {
    return new CelSyntax(factory.reportError(message));
  }
}
