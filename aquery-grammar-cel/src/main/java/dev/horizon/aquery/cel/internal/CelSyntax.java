package dev.horizon.aquery.cel.internal;

import dev.horizon.aquery.cel.Syntax;
import dev.horizon.aquery.shaded.cel.common.ast.CelExpr;
import java.util.Optional;

record CelSyntax(CelExpr node) implements Syntax {

  static CelExpr node(Syntax syntax) {
    if (syntax instanceof CelSyntax(CelExpr node)) {
      return node;
    }
    throw new IllegalArgumentException("a macro gives only syntax of its MacroCall, not " + syntax);
  }

  @Override
  public Optional<String> name() {
    return node.getKind() == CelExpr.ExprKind.Kind.IDENT ? Optional.of(node.ident().name()) : Optional.empty();
  }

  @Override
  public Optional<Selection> selection() {
    if (node.getKind() != CelExpr.ExprKind.Kind.SELECT || node.select().testOnly()) {
      return Optional.empty();
    }
    return Optional.of(new Selection(new CelSyntax(node.select().operand()), node.select().field()));
  }
}
