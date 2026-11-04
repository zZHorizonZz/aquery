package dev.horizon.aquery.cel.expression;

import java.util.function.Function;

/**
 * Rewrites the nodes of a compiled expression.
 *
 * <p>
 * {@link CelGrammar#with(Decorator)} adds a decorator to a grammar. The grammar gives the decorator the node of each
 * part of the text, after the nodes of the parts inside it, and keeps the node that the decorator returns.
 */
@FunctionalInterface
public interface Decorator {

  /**
   * Rewrites a node.
   *
   * @param node the node
   * @return the node to keep, which can be {@code node} itself
   */
  Expression decorate(Expression node);

  /**
   * Gives a decorator that applies this decorator and then {@code next}.
   *
   * @param next the decorator to apply second
   * @return the combined decorator
   */
  default Decorator then(Decorator next) {
    return node -> next.decorate(decorate(node));
  }

  /**
   * Gives a decorator that rewrites the nodes of one type and keeps all other nodes.
   *
   * @param type the type of the nodes
   * @param rewrite the rewrite of a node of the type
   * @param <N> the type of the nodes
   * @return the decorator
   */
  static <N extends Expression> Decorator of(Class<N> type, Function<? super N, ? extends Expression> rewrite) {
    return node -> type.isInstance(node) ? rewrite.apply(type.cast(node)) : node;
  }

  /**
   * Gives a decorator that keeps every node.
   *
   * @return the decorator
   */
  static Decorator none() {
    return node -> node;
  }
}
