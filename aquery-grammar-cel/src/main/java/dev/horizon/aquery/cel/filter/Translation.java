package dev.horizon.aquery.cel.filter;

import dev.horizon.aquery.aip132.FieldPath;
import dev.horizon.aquery.aip160.Filter.BoolLiteral;
import dev.horizon.aquery.aip160.Filter.Condition;
import dev.horizon.aquery.aip160.Filter.Restriction;
import dev.horizon.aquery.aip160.Filter.Text;
import dev.horizon.aquery.aip160.Operator;
import dev.horizon.aquery.cel.internal.CelTree;
import dev.horizon.aquery.shaded.cel.common.ast.CelExpr;

final class Translation {

  private final CelTree tree;
  private final CelFilterGrammar grammar;

  Translation(CelTree tree, CelFilterGrammar grammar) {
    this.tree = tree;
    this.grammar = grammar;
  }

  Condition condition(CelExpr node) {
    return grammar.decorate(translate(new Site(tree, node)));
  }

  private Condition translate(Site site) {
    CelExpr node = site.node();
    FieldPathNode path = new FieldPathNode(node);
    if (node.getKind() == CelExpr.ExprKind.Kind.SELECT && node.select().testOnly()) {
      return new Restriction(new FieldPath(path.segments()), Operator.HAS, new Text("*"));
    }
    if (path.valid()) {
      return new Restriction(new FieldPath(path.segments()), Operator.EQUALS, new BoolLiteral(true));
    }
    if (node.getKind() != CelExpr.ExprKind.Kind.CALL) {
      throw site.refuse("expected a condition but found %s", site.describe());
    }
    Call call = new ParsedCall(site, this);
    return grammar.chain(call.function(), _ -> {
      throw site.refuse("%s is not supported in a filter", site.describe());
    }).translate(call);
  }
}
