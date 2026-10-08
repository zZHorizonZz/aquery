package dev.horizon.aquery.cel;

/**
 * The error that {@link CelTranslator} throws for a CEL expression that the filter does not support.
 *
 * <p>
 * The error keeps the id of the expression. The translator changes it into an
 * {@link dev.horizon.aquery.aip160.InvalidFilterException} with the offset of the expression in the text.
 */
final class Refusal extends RuntimeException {

  private final long exprId;

  Refusal(long exprId, String message) {
    super(message, null, false, false);
    this.exprId = exprId;
  }

  /**
   * Gives the id of the refused expression.
   *
   * @return the id in the CEL syntax tree
   */
  long exprId() {
    return exprId;
  }
}
