package dev.horizon.aquery.cel;

/**
 * The error that {@link CelFilterTranslator} throws for a CEL expression that the filter does not support.
 *
 * <p>
 * The error keeps the id of the expression. The translator changes it into an
 * {@link dev.horizon.aquery.aip160.InvalidFilterException} with the offset of the expression in the text.
 */
final class Refusal extends RuntimeException {

  private final long nodeId;

  Refusal(long nodeId, String message) {
    super(message, null, false, false);
    this.nodeId = nodeId;
  }

  /**
   * Gives the id of the refused expression.
   *
   * @return the id in the CEL syntax tree
   */
  long nodeId() {
    return nodeId;
  }
}
