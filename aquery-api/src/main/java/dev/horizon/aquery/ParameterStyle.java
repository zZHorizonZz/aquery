package dev.horizon.aquery;

/**
 * Writes the placeholder of a bound value in the SQL text.
 *
 * <p>
 * Database drivers use different placeholders. JDBC uses {@code ?} for each value. Other drivers number the values,
 * for example {@code $1} or {@code @p1}. Choose the style of the driver that runs the statement.
 *
 * <p>
 * The index is the position of the value in the {@link Parameters} of the statement. The first value has index 0.
 * A style with numbers starts at 1. The placeholder becomes part of the SQL text. Thus it must not contain text from
 * a client.
 */
public interface ParameterStyle {

  /** The placeholder {@code ?} for each value, as in JDBC. */
  ParameterStyle QUESTION_MARK = index -> "?";

  /** The placeholders {@code $1}, {@code $2} and so on, as in the PostgreSQL protocol. */
  ParameterStyle DOLLAR = index -> "$" + (index + 1);

  /** The placeholders {@code @p1}, {@code @p2} and so on, as in SQL Server. */
  ParameterStyle AT_P = index -> "@p" + (index + 1);

  /** The placeholders {@code :1}, {@code :2} and so on, as in Oracle. */
  ParameterStyle COLON = index -> ":" + (index + 1);

  /**
   * Gives the placeholder of one value.
   *
   * @param index the position of the value in the parameters of the statement. The first value has index 0
   * @return the placeholder, for example {@code ?} or {@code $1}
   */
  String placeholder(int index);
}
