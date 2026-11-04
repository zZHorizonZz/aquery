package dev.horizon.aquery.cel.filter;

import dev.horizon.aquery.aip160.InvalidFilterException;
import dev.horizon.aquery.cel.internal.CelTree;
import dev.horizon.aquery.shaded.cel.common.ast.CelExpr;

record Site(CelTree tree, CelExpr node) {

  Site at(CelExpr other) {
    return new Site(tree, other);
  }

  String describe() {
    return tree.describe(node);
  }

  InvalidFilterException refuse(String message, Object... arguments) {
    return new InvalidFilterException(tree.position(node), message, arguments);
  }
}
