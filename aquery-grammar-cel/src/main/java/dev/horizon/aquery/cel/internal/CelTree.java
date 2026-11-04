package dev.horizon.aquery.cel.internal;

import dev.horizon.aquery.cel.Type;

import dev.horizon.aquery.shaded.cel.common.CelAbstractSyntaxTree;
import dev.horizon.aquery.shaded.cel.common.Operator;
import dev.horizon.aquery.shaded.cel.common.ast.CelExpr;
import java.util.List;

/**
 * The syntax tree that cel-java gave for one text: where a node is in the text, how to name a node in an error, and
 * the type and the overloads of a node if the type checker of cel-java checked the text.
 */
public final class CelTree {

  public static final String RECORD = "aquery__record";

  private final CelAbstractSyntaxTree tree;
  private final CelSourceText source;
  private final CelTypes types;

  public CelTree(CelAbstractSyntaxTree tree, CelTypes types) {
    this.tree = tree;
    this.source = new CelSourceText(tree.getSource());
    this.types = types;
  }

  public CelExpr root() {
    return tree.getExpr();
  }

  public int position(CelExpr node) {
    return source.offsetOf(node.id());
  }

  public Type type(CelExpr node) {
    return types == null ? Type.ANY : tree.getType(node.id()).map(types::aquery).orElse(Type.ANY);
  }

  public List<String> overloads(CelExpr node) {
    return tree.getReference(node.id()).<List<String>> map(reference -> reference.overloadIds()).orElse(List.of());
  }

  public String symbol(String function) {
    return Operator.lookupBinaryOperator(function).or(() -> Operator.lookupUnaryOperator(function)).orElse(function);
  }

  public boolean isOperator(String function) {
    return Operator.findReverse(function).isPresent();
  }

  public String describe(CelExpr node) {
    return switch (node.getKind()) {
      case CONSTANT -> "a constant";
      case IDENT -> "the identifier '" + node.ident().name() + "'";
      case SELECT -> node.select().testOnly() ? "has()" : "the field '" + node.select().field() + "'";
      case CALL -> function(node.call().function());
      case LIST -> "a list";
      case MAP -> "a map";
      case STRUCT -> "a message";
      case COMPREHENSION -> "a comprehension";
      default -> "an empty expression";
    };
  }

  private String function(String function) {
    String symbol = symbol(function);
    return symbol.equals(function) ? "the function '" + function + "'" : "the operator '" + symbol + "'";
  }
}
