package dev.horizon.aquery.aip132;

import dev.horizon.aquery.InvalidQueryException;

/**
 * The error that refuses an order_by clause.
 *
 * <p>
 * The constructors get the message as a template and the values for the template. The error names the
 * {@code order_by} part of the request. The message and the field are the full error. They tell the client
 * what is wrong and which argument to correct.
 *
 * <p>
 * This package throws this error for all order_by text that it refuses. The parser of the clause, the parser
 * of a field path and the writer of the SQL clause use it. The parsers also give the position: the offset in the
 * text where the parser stopped.
 */
public class InvalidOrderByException extends InvalidQueryException {

  public InvalidOrderByException(String message, Object... arguments) {
    super(format(message, arguments), ORDER_BY);
  }

  public InvalidOrderByException(int position, String message, Object... arguments) {
    super(format(message, arguments), ORDER_BY, position);
  }
}
