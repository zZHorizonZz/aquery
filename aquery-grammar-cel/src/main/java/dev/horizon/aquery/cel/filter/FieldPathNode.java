package dev.horizon.aquery.cel.filter;

import dev.horizon.aquery.cel.internal.CelTree;

import dev.horizon.aquery.shaded.cel.common.ast.CelConstant;
import dev.horizon.aquery.shaded.cel.common.ast.CelExpr;
import java.util.ArrayList;
import java.util.List;

record FieldPathNode(CelExpr node) {

  boolean valid() {
    return switch (node.getKind()) {
      case IDENT -> true;
      case SELECT -> !node.select().testOnly() && new FieldPathNode(node.select().operand()).valid();
      case CALL -> key();
      default -> false;
    };
  }

  List<String> segments() {
    List<String> segments = new ArrayList<>();
    add(node, segments);
    return segments;
  }

  private static void add(CelExpr node, List<String> segments) {
    switch (node.getKind()) {
      case IDENT -> segments.add(node.ident().name());
      case SELECT -> {
        add(node.select().operand(), segments);
        segments.add(node.select().field());
      }
      default -> {
        add(node.call().args().get(0), segments);
        segments.add(node.call().args().get(1).constant().stringValue());
      }
    }
  }

  List<String> presence(Site site) {
    CelExpr operand = node.select().operand();
    List<String> segments = new ArrayList<>();
    if (!(operand.getKind() == CelExpr.ExprKind.Kind.IDENT && operand.ident().name().equals(CelTree.RECORD))) {
      FieldPathNode parent = new FieldPathNode(operand);
      if (!parent.valid()) {
        throw site.refuse("has() takes a field, as in has(note) or has(labels.tier)");
      }
      segments.addAll(parent.segments());
    }
    segments.add(node.select().field());
    return segments;
  }

  private boolean key() {
    List<CelExpr> arguments = node.call().args();
    return node.call().function().equals("_[_]") && arguments.size() == 2
        && new FieldPathNode(arguments.get(0)).valid()
        && arguments.get(1).getKind() == CelExpr.ExprKind.Kind.CONSTANT
        && arguments.get(1).constant().getKind() == CelConstant.Kind.STRING_VALUE;
  }
}
