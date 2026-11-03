package dev.horizon.aquery.cel.filter;

import dev.horizon.aquery.aip160.Filter.Condition;
import java.util.function.Function;

/**
 * Rewrites the conditions of a filter.
 *
 * <p>
 * {@link CelFilterGrammar#with(Decorator)} adds a decorator to a grammar. The grammar gives the decorator the
 * condition of each part of the text, after the conditions of the parts inside it, and keeps the condition that the
 * decorator returns.
 */
@FunctionalInterface
public interface Decorator {

  /**
   * Rewrites a condition.
   *
   * @param condition the condition
   * @return the condition to keep, which can be {@code condition} itself
   */
  Condition decorate(Condition condition);

  /**
   * Gives a decorator that applies this decorator and then {@code next}.
   *
   * @param next the decorator to apply second
   * @return the combined decorator
   */
  default Decorator then(Decorator next) {
    return condition -> next.decorate(decorate(condition));
  }

  /**
   * Gives a decorator that rewrites the conditions of one type and keeps all other conditions.
   *
   * @param type the type of the conditions
   * @param rewrite the rewrite of a condition of the type
   * @param <C> the type of the conditions
   * @return the decorator
   */
  static <C extends Condition> Decorator of(Class<C> type, Function<? super C, ? extends Condition> rewrite) {
    return condition -> type.isInstance(condition) ? rewrite.apply(type.cast(condition)) : condition;
  }

  /**
   * Gives a decorator that keeps every condition.
   *
   * @return the decorator
   */
  static Decorator none() {
    return condition -> condition;
  }
}
