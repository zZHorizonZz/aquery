package dev.horizon.aquery.cel;

import dev.horizon.aquery.InvalidQueryException;

/**
 * The error that refuses an expression.
 *
 * <p>
 * The compiler throws it for text that does not parse, that does not match the scope or the types of the
 * parameters, or that uses a part of the language that SQL cannot answer. A writer of SQL can throw it for an
 * expression that its database cannot answer. The error names the {@code expression} part of the request, and gives the offset
 * of the problem in the text when it has one.
 */
public class InvalidExpressionException extends InvalidQueryException {

  public InvalidExpressionException(String message, Object... arguments) {
    super(format(message, arguments), EXPRESSION);
  }

  public InvalidExpressionException(int position, String message, Object... arguments) {
    super(format(message, arguments), EXPRESSION, position);
  }
}
