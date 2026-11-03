package dev.horizon.aquery.cel.expression;

import dev.horizon.aquery.cel.internal.CelTree;

import dev.horizon.aquery.shaded.cel.common.ast.CelExpr;

record Site(CelTree tree, CelExpr node) {

  int position() {
    return tree.position(node);
  }

  Site at(CelExpr other) {
    return new Site(tree, other);
  }

  InvalidExpressionException refuse(String message, Object... arguments) {
    return new InvalidExpressionException(position(), message, arguments);
  }
}
