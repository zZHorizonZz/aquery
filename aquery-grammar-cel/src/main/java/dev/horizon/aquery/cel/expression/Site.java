package dev.horizon.aquery.cel.expression;

import dev.horizon.aquery.cel.internal.CelTree;

import dev.horizon.aquery.shaded.cel.common.ast.CelExpr;

record Site(CelTree tree, CelExpr node, CelGrammar grammar) {

  int position() {
    return tree.position(node);
  }

  Site at(CelExpr other) {
    return new Site(tree, other, grammar);
  }

  Expression decorate(Expression made) {
    return grammar.decorate(made);
  }

  InvalidExpressionException refuse(String message, Object... arguments) {
    return new InvalidExpressionException(position(), message, arguments);
  }
}
