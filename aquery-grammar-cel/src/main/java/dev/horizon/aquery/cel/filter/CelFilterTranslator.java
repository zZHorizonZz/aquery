package dev.horizon.aquery.cel.filter;

import dev.horizon.aquery.aip160.Filter;
import dev.horizon.aquery.aip160.FilterTranslator;
import dev.horizon.aquery.cel.internal.CelTree;
import dev.horizon.aquery.shaded.cel.common.CelAbstractSyntaxTree;

record CelFilterTranslator(CelFilterGrammar grammar) implements FilterTranslator<CelAbstractSyntaxTree> {

  @Override
  public Filter translate(CelAbstractSyntaxTree syntax) {
    return new Filter(new Translation(new CelTree(syntax, null), grammar).condition(syntax.getExpr()));
  }
}
