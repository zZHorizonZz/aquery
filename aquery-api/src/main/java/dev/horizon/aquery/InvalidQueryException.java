package dev.horizon.aquery;

/**
 * The error that refuses a query.
 *
 * <p>
 * A query is text that a client wrote. The library throws this exception when it cannot read that text. It
 * also throws this exception when it cannot answer the query. The exception names the part of the request that
 * is wrong. An API can send an INVALID_ARGUMENT error that names that part.
 *
 * <p>
 * Throw one of the subtypes:
 *
 * <ul>
 * <li>{@link dev.horizon.aquery.aip160.InvalidFilterException} for filter text.
 * <li>{@link dev.horizon.aquery.aip132.InvalidOrderByException} for order_by text.
 * <li>{@link dev.horizon.aquery.aip158.InvalidPageTokenException} for a page token.
 * <li>{@link dev.horizon.aquery.aip157.InvalidReadMaskException} for a read mask.
 * </ul>
 *
 * <p>
 * Each subtype gets the message as a template and the values for the template. Catch this type if the API
 * answers all these errors in the same way.
 *
 * <p>
 * The message is plain text. It tells what the client wrote and what the library expected. If a parser stops at
 * one location in the text, the exception also gives the offset of that location.
 */
public class InvalidQueryException extends RuntimeException {

  public static final String FILTER = "filter";
  public static final String ORDER_BY = "order_by";
  public static final String PAGE_TOKEN = "page_token";
  public static final String READ_MASK = "read_mask";

  private final String field;
  private final int position;

  protected InvalidQueryException(String message, String field) {
    this(message, field, -1);
  }

  protected InvalidQueryException(String message, String field, int position) {
    super(message);
    this.field = field;
    this.position = position;
  }

  /**
   * Gives the part of the request that is wrong.
   *
   * @return the name of the part, for example {@code filter}
   */
  public String field() {
    return field;
  }

  /**
   * Gives the offset of the problem in the request text.
   *
   * @return the offset, or {@code -1} if the problem has no one location
   */
  public int position() {
    return position;
  }

  /**
   * Puts the values into the message template.
   *
   * <p>
   * If there are no values, the message stays as it is. Thus a {@code %} character in such a message stays text.
   *
   * @param message the message template, in the syntax of {@link String#format}
   * @param arguments the values for the template
   * @return the message with the values
   */
  protected static String format(String message, Object... arguments) {
    return arguments.length == 0 ? message : message.formatted(arguments);
  }
}
