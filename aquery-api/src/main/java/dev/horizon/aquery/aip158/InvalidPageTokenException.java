package dev.horizon.aquery.aip158;

import dev.horizon.aquery.InvalidQueryException;

/**
 * The error that refuses a page token.
 *
 * <p>
 * The constructor gets the message as a template and the values for the template. The error names the
 * {@code page_token} part of the request. The message and the field are the full error. They tell the client
 * what is wrong and which argument to correct.
 */
public class InvalidPageTokenException extends InvalidQueryException {

  public InvalidPageTokenException(String message, Object... arguments) {
    super(format(message, arguments), PAGE_TOKEN);
  }
}
