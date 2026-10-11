package dev.horizon.aquery.cel;

import dev.horizon.aquery.aip160.Filter.Condition;
import dev.horizon.aquery.shaded.cel.common.ast.CelExpr;

/**
 * A call that aquery refuses with a reason, such as the conditional operator {@code ? :} or {@code matches()}, so
 * that the error says why and not only that the function is unknown.
 */
final class RefusedOperation implements FilterOperation, ExpressionOperation {

  private final String reason;

  /**
   * Makes the operation.
   *
   * @param reason the error, for example {@code the conditional operator '? :' is not supported in a filter}
   */
  RefusedOperation(String reason) {
    this.reason = reason;
  }

  @Override
  public Condition translate(CelExpr call, FilterTranslation translation) {
    throw translation.tree().refuse(call, "%s", reason);
  }

  @Override
  public Expression translate(CelExpr call, ExpressionTranslation translation) {
    throw translation.tree().refuse(call, "%s", reason);
  }
}
