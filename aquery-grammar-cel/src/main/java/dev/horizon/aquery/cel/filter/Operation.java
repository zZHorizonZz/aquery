package dev.horizon.aquery.cel.filter;

import dev.horizon.aquery.aip160.Filter.Condition;

/**
 * A function or an operator of CEL, and the condition of a filter that a call of it is.
 *
 * <p>
 * A {@link CelFilterGrammar} chains the operations of each function. A call goes to the first operation of its
 * function, which translates it, passes it to the {@link Next} operations, or changes what they give. An
 * operation that a caller adds comes first, so it decorates the operations of the grammar.
 */
public interface Operation {

  /**
   * Gives the name of the function in CEL.
   *
   * @return the name, for example {@code _==_} or {@code startsWith}
   */
  String function();

  /**
   * Translates a call.
   *
   * @param call the call
   * @param next the operations of the function after this one
   * @return the condition of the call
   * @throws dev.horizon.aquery.aip160.InvalidFilterException if the call has a form that this operation refuses, as
   * {@link Call#refuse} gives it
   */
  Condition translate(Call call, Next next);
}
