package dev.horizon.aquery.cel;

import java.util.Map;
import java.util.Objects;

/**
 * A compiled expression.
 *
 * <p>
 * The compiler checked the text against a scope and the types of the parameters. The expression does not hold the
 * values of the parameters. Thus one expression is good for many statements, with other values each time. Keep it,
 * and give the values when the SQL is written.
 *
 * @param text the text of the expression
 * @param root the root of the tree
 * @param parameters the types of the parameters that the expression reads, by name
 */
public record CompiledExpression(String text, Expression root, Map<String, Type> parameters) {

  public CompiledExpression {
    Objects.requireNonNull(text, "text");
    Objects.requireNonNull(root, "root");
    parameters = Map.copyOf(parameters);
  }

  /**
   * Gives the type of the value of the expression.
   *
   * @return the type, for example {@link Type.Scalar#BOOL} for a condition
   */
  public Type type() {
    return root.type();
  }
}
