package dev.horizon.aquery.aip160;

import dev.horizon.aquery.InvalidQueryException;

/**
 * The error that refuses a filter.
 *
 * <p>
 * The constructors get the message as a template and the values for the template. The error names the
 * {@code filter} part of the request. The message and the field are the full error. They tell the client what
 * is wrong and which argument to correct.
 *
 * <p>
 * This package throws this error, or one of its subtypes, for all filter text that it refuses. The parser also
 * gives the position: the offset in the text where the parser stopped.
 */
public class InvalidFilterException extends InvalidQueryException {

  public InvalidFilterException(String message, Object... arguments) {
    super(format(message, arguments), FILTER);
  }

  public InvalidFilterException(int position, String message, Object... arguments) {
    super(format(message, arguments), FILTER, position);
  }
}
