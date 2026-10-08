package dev.horizon.aquery.aip160;

import java.util.Set;

/**
 * The operator between a field and its value in a {@link Filter.Restriction}, for example the {@code =} in
 * {@code name = "dan"}.
 *
 * <p>
 * Each backend declares the operators that it supports. The generator refuses all other operators before it
 * calls the backend. Thus no backend must refuse them again.
 *
 * <p>
 * The symbol is the comparator of AIP-160. The CEL parser gives the same operators:
 *
 * <ul>
 * <li>{@code == != < <= > >=} are {@link #EQUALS}, {@link #NOT_EQUALS} and the order comparisons.
 * <li>{@code name.contains("x")} is {@link #HAS} with the value {@code "x"}.
 * <li>{@code has(labels.site)} is {@link #HAS} with the presence wildcard {@code *} on {@code labels.site}.
 * <li>{@code name.startsWith("x")} and {@code name.endsWith("x")} are {@link #STARTS_WITH} and
 * {@link #ENDS_WITH}. AIP-160 has no symbol for them.
 * <li>{@code "x" in tags} is {@link #IN} on {@code tags}. The field contains the value as an element or as a key.
 * AIP-160 has no symbol for it.
 * </ul>
 *
 * <p>
 * {@code ORDERED} contains the operators of a field with ordered values: equality, inequality and the four order
 * comparisons. {@code EQUALITY} contains the operators of a field with values that you can only compare for
 * equality.
 *
 * @see <a href="https://google.aip.dev/assets/misc/ebnf-filtering.txt">AIP-160 EBNF: comparator</a>
 */
public enum Operator {

  LESS_EQUALS("<=", "<="),
  LESS_THAN("<", "<"),
  GREATER_EQUALS(">=", ">="),
  GREATER_THAN(">", ">"),
  NOT_EQUALS("!=", "<>"),
  EQUALS("=", "="),
  HAS(":", null),
  STARTS_WITH("startsWith", null),
  ENDS_WITH("endsWith", null),
  IN("in", null);

  public static final Set<Operator> ORDERED = Set.of(EQUALS, NOT_EQUALS, LESS_THAN, LESS_EQUALS, GREATER_THAN,
      GREATER_EQUALS);

  public static final Set<Operator> EQUALITY = Set.of(EQUALS, NOT_EQUALS);

  private final String symbol;
  private final String sql;

  Operator(String symbol, String sql) {
    this.symbol = symbol;
    this.sql = sql;
  }

  /**
   * Gives the symbol of the operator, for filter text and error messages.
   *
   * @return the symbol, for example {@code !=}. For an operator without an AIP-160 comparator, the name of the CEL
   * function, for example {@code startsWith}
   */
  public String symbol() {
    return symbol;
  }

  /**
   * Gives the Standard SQL operator that compares two values in the same way.
   *
   * @return the SQL operator, for example {@code <>} for {@code !=}
   * @throws IllegalStateException for {@link #HAS}, {@link #STARTS_WITH}, {@link #ENDS_WITH} and {@link #IN}. Each
   * backend writes its own SQL for these operators
   */
  public String sql() {
    if (sql == null) {
      throw new IllegalStateException("the operator '" + symbol + "' has no single SQL operator");
    }
    return sql;
  }

  /**
   * Gives the operator that has the symbol.
   *
   * @param symbol the symbol of the operator, for example {@code <=}
   * @return the operator
   * @throws IllegalArgumentException if no operator has the symbol
   */
  public static Operator ofSymbol(String symbol) {
    for (Operator operator : values()) {
      if (operator.symbol.equals(symbol)) {
        return operator;
      }
    }
    throw new IllegalArgumentException("no operator is written '" + symbol + "'");
  }
}
