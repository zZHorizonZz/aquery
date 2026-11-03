package dev.horizon.aquery.cel.expression;

import dev.horizon.aquery.cel.Type;

import dev.horizon.aquery.shaded.cel.common.ast.CelConstant;
import dev.horizon.aquery.shaded.cel.common.ast.CelExpr;
import java.util.List;
import java.util.Optional;

final class CheckedCall implements Call {

  private final Site site;
  private final Translation translation;

  CheckedCall(Site site, Translation translation) {
    this.site = site;
    this.translation = translation;
  }

  @Override
  public String function() {
    return site.node().call().function();
  }

  @Override
  public Optional<Expression> target() {
    return site.node().call().target().map(translation::expression);
  }

  @Override
  public Expression argument(int index) {
    return translation.expression(node(index));
  }

  @Override
  public List<Expression> arguments() {
    return site.node().call().args().stream().map(translation::expression).toList();
  }

  @Override
  public boolean isNull(int index) {
    CelExpr argument = node(index);
    return argument.getKind() == CelExpr.ExprKind.Kind.CONSTANT
        && argument.constant().getKind() == CelConstant.Kind.NULL_VALUE;
  }

  @Override
  public Optional<Overload> overload() {
    return translation.overload(site);
  }

  @Override
  public Type type() {
    return site.tree().type(site.node());
  }

  @Override
  public int position() {
    return site.position();
  }

  @Override
  public InvalidExpressionException refuse(String message, Object... arguments) {
    return site.refuse(message, arguments);
  }

  @Override
  public InvalidExpressionException refuse(int index, String message, Object... arguments) {
    return site.at(node(index)).refuse(message, arguments);
  }

  private CelExpr node(int index) {
    return site.node().call().args().get(index);
  }
}
