package dev.horizon.aquery.aip157;

import dev.horizon.aquery.InvalidQueryException;

/**
 * The error that refuses a read mask.
 *
 * <p>
 * The constructors get the message as a template and the values for the template. The error names the
 * {@code read_mask} part of the request. The message and the field are the full error. They tell the client what
 * is wrong and which argument to correct.
 *
 * <p>
 * The parser of the mask also gives the position: the offset in the text where the parser stopped.
 */
public class InvalidReadMaskException extends InvalidQueryException {

  public InvalidReadMaskException(String message, Object... arguments) {
    super(format(message, arguments), READ_MASK);
  }

  public InvalidReadMaskException(int position, String message, Object... arguments) {
    super(format(message, arguments), READ_MASK, position);
  }
}
