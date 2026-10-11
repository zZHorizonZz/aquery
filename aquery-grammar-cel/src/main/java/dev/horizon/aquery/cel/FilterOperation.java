package dev.horizon.aquery.cel;

import dev.horizon.aquery.aip160.Filter.Condition;
import dev.horizon.aquery.shaded.cel.common.ast.CelExpr;

/**
 * What a call of one CEL operator or function is in a filter, such as {@code ==} or {@code startsWith()}.
 *
 * <p>
 * {@link CelFilterTranslator} knows the operation of each function that a filter supports. An operation checks the
 * arguments of the call, refuses those that a filter cannot answer, and gives the condition.
 */
interface FilterOperation {

  /**
   * Gives the condition of a call.
   *
   * @param call the call
   * @param translation the translation of the filter, which translates the arguments
   * @return the condition
   * @throws Refusal if the call is not supported
   */
  Condition translate(CelExpr call, FilterTranslation translation);
}
