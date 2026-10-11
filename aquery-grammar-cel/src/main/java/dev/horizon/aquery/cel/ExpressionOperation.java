package dev.horizon.aquery.cel;

import dev.horizon.aquery.shaded.cel.common.ast.CelExpr;

/**
 * What a call of one CEL operator or function is in an expression, such as {@code +} or {@code now()}.
 *
 * <p>
 * {@link CelExpressionCompiler} knows the operation of each function that an expression supports. An operation
 * checks the arguments of the call, refuses those that SQL cannot answer, and gives the node of the expression.
 */
interface ExpressionOperation {

  /**
   * Gives the node of a call.
   *
   * @param call the call
   * @param translation the translation of the expression, which translates the arguments
   * @return the node
   * @throws Refusal if the call is not supported
   */
  Expression translate(CelExpr call, ExpressionTranslation translation);
}
