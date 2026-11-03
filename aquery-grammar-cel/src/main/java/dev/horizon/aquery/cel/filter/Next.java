package dev.horizon.aquery.cel.filter;

import dev.horizon.aquery.aip160.Filter.Condition;

/**
 * The operations of a filter grammar below a {@link Operation}, which translate a call that the operation
 * passes on. The last of them refuses the call as not supported.
 */
@FunctionalInterface
public interface Next {

  /**
   * Translates a call with the operations below.
   *
   * @param call the call
   * @return the condition of the call
   * @throws dev.horizon.aquery.aip160.InvalidFilterException if no operation below translates the call
   */
  Condition translate(Call call);
}
