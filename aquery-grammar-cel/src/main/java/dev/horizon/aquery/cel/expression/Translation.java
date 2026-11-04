package dev.horizon.aquery.cel.expression;

import dev.horizon.aquery.cel.expression.Expression.ListLiteral;
import dev.horizon.aquery.cel.internal.CelTree;
import dev.horizon.aquery.shaded.cel.common.ast.CelExpr;
import java.util.Optional;

final class Translation {

  private final CelTree tree;
  private final CelGrammar grammar;
  private final Checker checker;
  private final Namespace names;

  Translation(CelTree tree, CelGrammar grammar, Checker checker, Namespace names) {
    this.tree = tree;
    this.grammar = grammar;
    this.checker = checker;
    this.names = names;
  }

  Expression expression(CelExpr node) {
    return grammar.decorate(translate(node));
  }

  Expression within(String variable, Name binding, CelExpr body) {
    return names.within(variable, binding, () -> expression(body));
  }

  Optional<Overload> overload(Site call) {
    return checker.overload(call);
  }

  private Expression translate(CelExpr node) {
    Site site = new Site(tree, node, grammar);
    return switch (node.getKind()) {
      case CONSTANT -> new Constant(node.constant(), site).literal();
      case IDENT -> name(node).value(site);
      case SELECT -> select(node, site);
      case CALL -> call(site);
      case LIST -> new ListLiteral(node.list().elements().stream().map(this::expression).toList(), tree.type(node),
          site.position());
      case COMPREHENSION -> quantify(new Quantification(node.comprehension(), site));
      case MAP -> throw site.refuse("a map literal is not supported in an expression");
      case STRUCT -> throw site.refuse("a message literal is not supported in an expression");
      default -> throw site.refuse("the expression is empty");
    };
  }

  private Name name(CelExpr node) {
    Site site = new Site(tree, node, grammar);
    return switch (node.getKind()) {
      case IDENT -> names.find(node.ident().name(), site);
      case SELECT -> member(node, site);
      default -> new Name.ValueName(translate(node));
    };
  }

  private Name operand(CelExpr node) {
    return name(node).decorate(new Site(tree, node, grammar));
  }

  private Expression select(CelExpr node, Site site) {
    CelExpr.CelSelect select = node.select();
    return select.testOnly() ? operand(select.operand()).presence(select.field(), site) : name(node).value(site);
  }

  private Name member(CelExpr node, Site site) {
    CelExpr.CelSelect select = node.select();
    return select.testOnly() ? new Name.ValueName(translate(node))
        : operand(select.operand()).member(select.field(), site);
  }

  private Expression call(Site site) {
    Call call = new CheckedCall(site, this);
    return grammar.chain(call.function(), _ -> {
      throw unsupported(site);
    }).translate(call);
  }

  private Expression quantify(Quantification quantification) {
    return operand(quantification.range()).quantify(quantification, this);
  }

  private InvalidExpressionException unsupported(Site site) {
    String function = site.node().call().function();
    return tree.isOperator(function) ? site.refuse("the operator '%s' is not supported in an expression", function)
        : site.refuse("%s() is not supported in an expression", function);
  }
}
