package dev.horizon.aquery;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The values that one SQL statement binds, in the sequence of their placeholders.
 *
 * <p>
 * Use one instance for all clauses of one statement, for example the filter and the keyset. Then the placeholders
 * of the clauses do not collide, and numbered placeholders continue from one clause to the next. Put the clauses
 * into the statement in the sequence in which you wrote them. Then give the values to the driver in the sequence of
 * {@link #values()}.
 *
 * <p>
 * Each placeholder in the SQL text binds its own value. A value that the SQL uses two times is bound two times. Thus
 * the sequence of the values is the sequence of the placeholders in the text, and the {@code ?} style works.
 *
 * <p>
 * If a clause throws an exception, the parameters can contain some values of that clause. Do not use them for a
 * statement.
 *
 * <p>
 * An instance is not safe for use by several threads at the same time.
 */
public final class Parameters {

  private final ParameterStyle style;
  private final List<Object> values = new ArrayList<>();

  public Parameters(ParameterStyle style) {
    this.style = Objects.requireNonNull(style, "style");
  }

  /**
   * Adds the value, and gives the placeholder for it.
   *
   * <p>
   * The value never becomes part of the SQL text.
   *
   * @param value the value to bind. It cannot be null
   * @return the placeholder of the value, in the style of the statement
   */
  public String bind(Object value) {
    Objects.requireNonNull(value, "value");
    values.add(value);
    return style.placeholder(values.size() - 1);
  }

  /**
   * Gives the bound values.
   *
   * @return the values, in the sequence of the binds. The list cannot change
   */
  public List<Object> values() {
    return List.copyOf(values);
  }

  /**
   * Gives the number of bound values.
   *
   * @return the number of values
   */
  public int size() {
    return values.size();
  }
}
