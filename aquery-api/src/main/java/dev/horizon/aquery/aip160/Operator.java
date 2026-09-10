package dev.horizon.aquery.aip160;

import java.util.Set;

/**
 * A comparator of the AIP-160 filter language. It is the operator between a field and its argument, for example
 * the {@code =} in {@code name = "dan"}.
 *
 * <p>
 * The filter text contains the symbol. Each backend declares the operators that it supports. The generator
 * refuses all other operators before it calls the backend. Thus no backend must refuse them again.
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
  HAS(":", null);

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
   * Gives the symbol in the filter text.
   *
   * @return the symbol, for example {@code !=}
   */
  public String symbol() {
    return symbol;
  }

  /**
   * Gives the Standard SQL operator that compares two values in the same way.
   *
   * @return the SQL operator, for example {@code <>} for {@code !=}
   * @throws IllegalStateException for {@link #HAS}. Each backend writes its own SQL for this operator
   */
  public String sql() {
    if (sql == null) {
      throw new IllegalStateException("the has operator ':' has no single SQL operator");
    }
    return sql;
  }

  /**
   * Gives the operator that has the symbol.
   *
   * @param symbol the symbol in the filter text, for example {@code <=}
   * @return the operator
   * @throws IllegalArgumentException if no operator has the symbol
   */
  public static Operator ofSymbol(String symbol) {
    for (Operator operator : values()) {
      if (operator.symbol.equals(symbol)) {
        return operator;
      }
    }
    throw new IllegalArgumentException("no AIP-160 comparator is written '" + symbol + "'");
  }
}
