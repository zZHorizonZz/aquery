package dev.horizon.aquery.internal.aip161;

/**
 * The syntax error of a field path.
 *
 * <p>
 * The {@link FieldPathScanner} throws this error. Each parser changes it into the error of its request part, for
 * example an order_by error or a read mask error. Thus the scanner does not know which part of the request it
 * reads.
 */
public final class FieldPathSyntaxException extends RuntimeException {

  private final int position;

  public FieldPathSyntaxException(int position, String message) {
    super(message);
    this.position = position;
  }

  /**
   * Gives the offset in the text where the scanner stopped.
   *
   * @return the offset of the problem
   */
  public int position() {
    return position;
  }
}
