package dev.horizon.aquery.cel.expression;

import dev.horizon.aquery.cel.expression.Expression.Quantifier;
import dev.horizon.aquery.cel.expression.Expression.Related;
import dev.horizon.aquery.cel.expression.Expression.Row;
import dev.horizon.aquery.cel.expression.Expression.Variable;
import dev.horizon.aquery.shaded.cel.common.ast.CelConstant;
import dev.horizon.aquery.shaded.cel.common.ast.CelExpr;
import dev.horizon.aquery.shaded.cel.common.ast.CelExpr.CelComprehension;

record Quantification(CelComprehension comprehension, Site site) {

  String variable() {
    return comprehension.iterVar();
  }

  CelExpr range() {
    return comprehension.iterRange();
  }

  CelExpr predicate() {
    CelExpr first = comprehension.loopStep().call().args().get(0);
    boolean accumulator = first.getKind() == CelExpr.ExprKind.Kind.IDENT
        && first.ident().name().equals(comprehension.accuVar());
    return accumulator ? comprehension.loopStep().call().args().get(1) : first;
  }

  String function() {
    return kind().name().toLowerCase();
  }

  Expression quantifier(Variable variable, Expression range, Expression predicate) {
    return new Quantifier(kind(), variable, range, predicate, site.position());
  }

  Expression related(Row.Related row, Expression predicate) {
    return kind() == Quantifier.Kind.EXISTS ? new Related.Exists(row, predicate, site.position())
        : new Related.All(row, predicate, site.position());
  }

  private Quantifier.Kind kind() {
    CelExpr start = comprehension.accuInit();
    CelExpr step = comprehension.loopStep();
    boolean simple = comprehension.iterVar2().isEmpty() && start.getKind() == CelExpr.ExprKind.Kind.CONSTANT
        && start.constant().getKind() == CelConstant.Kind.BOOLEAN_VALUE && step.getKind() == CelExpr.ExprKind.Kind.CALL
        && step.call().args().size() == 2;
    if (simple && !start.constant().booleanValue() && step.call().function().equals("_||_")) {
      return Quantifier.Kind.EXISTS;
    }
    if (simple && start.constant().booleanValue() && step.call().function().equals("_&&_")) {
      return Quantifier.Kind.ALL;
    }
    throw site.refuse("only the macros exists() and all() are supported");
  }
}
