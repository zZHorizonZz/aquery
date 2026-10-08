package dev.horizon.aquery.aip160;

import static java.util.stream.Collectors.joining;

import dev.horizon.aquery.aip132.FieldPath;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * A parsed filter. The filter can be empty.
 *
 * <p>
 * The filter is a tree of conditions. The tree does not depend on the language of the filter text. Each language
 * has its own {@link FilterParser} in its own module, for example {@code aquery-grammar-ebnf} for AIP-160 text and
 * {@code aquery-grammar-cel} for CEL text. All parsers give the same tree. Thus {@link WhereClause} and the backends compile
 * all languages in the same way.
 *
 * <p>
 * The tree has these nodes:
 *
 * <ul>
 * <li>{@link And}: all operands must be true.
 * <li>{@link Or}: one or more operands must be true.
 * <li>{@link Not}: the operand must be false.
 * <li>{@link Restriction}: a field, an operator and a value, for example {@code name = "dan"}.
 * <li>{@link Global}: a value without a field, for example {@code prod}. All fields for implicit filters match it.
 * </ul>
 *
 * <p>
 * A {@link Value} keeps the form that the client wrote. The rules for literals are different in each language.
 * For example, a word without quotes in AIP-160 can be a boolean, an integer, a duration or an enum value. The
 * field type tells which. Thus the parser does not read the word. The backend of the field reads it with
 * {@link Args}.
 *
 * <p>
 * An empty filter matches all rows. Blank text gives an empty filter. An empty filter compiles to {@code (1 = 1)}
 * and binds no values. The filter text can have at most {@code MAX_LENGTH} (16 KB) characters.
 *
 * <p>
 * {@link #toString()} writes the tree in a stable form. Tests, error messages and page token fingerprints use
 * it. It is not the text that the client wrote.
 *
 * @see <a href="https://chromium.googlesource.com/infra/luci/luci-go/+/main/common/data/aip160/filter_parser.go">LUCI aip160: filter_parser.go (Filter AST)</a>
 */
public final class Filter {

  public static final int MAX_LENGTH = 16 * 1024;

  private final Condition condition;

  public Filter(Condition condition) {
    this.condition = condition;
  }

  /**
   * Gives the condition of the filter.
   *
   * @return the root of the tree, or null if the filter is empty
   */
  public Condition condition() {
    return condition;
  }

  @Override
  public String toString() {
    return "filter{" + (condition == null ? "" : condition) + "}";
  }

  /**
   * Quotes a value in the string syntax of the filter language, for error messages.
   *
   * @param value the value to quote
   * @return the value in double quotes, with escapes for quotes, backslashes and control characters
   */
  public static String quote(String value) {
    StringBuilder quoted = new StringBuilder("\"");
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      switch (c) {
        case '"' -> quoted.append("\\\"");
        case '\\' -> quoted.append("\\\\");
        case '\n' -> quoted.append("\\n");
        case '\r' -> quoted.append("\\r");
        case '\t' -> quoted.append("\\t");
        default -> {
          if (c < 0x20) {
            quoted.append(String.format("\\u%04x", (int) c));
          } else {
            quoted.append(c);
          }
        }
      }
    }
    return quoted.append('"').toString();
  }

  private static String joined(String prefix, List<?> parts) {
    return parts.stream().map(String::valueOf).collect(joining(",", prefix + "{", "}"));
  }

  private static List<Condition> nonEmpty(String node, List<Condition> operands) {
    if (operands.isEmpty()) {
      throw new IllegalArgumentException("a condition '" + node + "' needs one or more operands");
    }
    return List.copyOf(operands);
  }

  /** A node of the filter tree. */
  public sealed interface Condition permits And, Or, Not, Restriction, Global {
  }

  /**
   * A conjunction: all operands must be true.
   *
   * <p>
   * AIP-160 writes it as {@code a AND b} or as a sequence {@code a b}. CEL writes it as {@code a && b}. The list
   * must have one or more operands.
   */
  public record And(List<Condition> operands) implements Condition {

    public And {
      operands = nonEmpty("and", operands);
    }

    @Override
    public String toString() {
      return joined("and", operands);
    }
  }

  /**
   * A disjunction: one or more operands must be true.
   *
   * <p>
   * AIP-160 writes it as {@code a OR b}. CEL writes it as {@code a || b}. The list must have one or more operands.
   */
  public record Or(List<Condition> operands) implements Condition {

    public Or {
      operands = nonEmpty("or", operands);
    }

    @Override
    public String toString() {
      return joined("or", operands);
    }
  }

  /**
   * A negation: the operand must be false.
   *
   * <p>
   * AIP-160 writes it as {@code NOT a} or {@code -a}. CEL writes it as {@code !a}.
   */
  public record Not(Condition operand) implements Condition {

    @Override
    public String toString() {
      return "not{" + operand + "}";
    }
  }

  /**
   * A restriction: a field, an operator and a value.
   *
   * <p>
   * The path can name more than the field, as in {@code labels.site = "pilsen"}. The table finds the field from
   * the start of the path. The backend of the field reads the other segments.
   *
   * <p>
   * Examples:
   *
   * <ul>
   * <li>equality: {@code package = "com.google"} in AIP-160, {@code package == "com.google"} in CEL
   * <li>has: {@code labels:site} in AIP-160, {@code "site" in labels} in CEL
   * <li>prefix: {@code name.startsWith("da")} in CEL
   * </ul>
   */
  public record Restriction(FieldPath fieldPath, Operator operator, Value value) implements Condition {

    @Override
    public String toString() {
      return "restriction{" + quote(fieldPath.toString()) + "," + quote(operator.symbol()) + "," + value + "}";
    }
  }

  /**
   * A value without a field, for example {@code prod}.
   *
   * <p>
   * The generator matches the value with all fields for implicit filters. Only AIP-160 has this condition.
   */
  public record Global(String value) implements Condition {

    @Override
    public String toString() {
      return "global{" + quote(value) + "}";
    }
  }

  /**
   * The value of a restriction, in the form that the client wrote.
   *
   * <p>
   * AIP-160 has only two forms: {@link Text} without quotes and {@link StringLiteral} in quotes. CEL has typed
   * literals. A CEL identifier, as in {@code status == ACTIVE}, is {@link Text}.
   */
  public sealed interface Value permits Text, StringLiteral, BoolLiteral, IntLiteral, DoubleLiteral, DurationLiteral,
      TimestampLiteral {

    /**
     * Gives the value as the client wrote it, for error messages.
     *
     * @return the text of the value
     */
    String input();
  }

  /**
   * A word without quotes, for example {@code true}, {@code -30}, {@code 1.5s}, {@code ACTIVE} or {@code *}.
   *
   * <p>
   * The text can have dots, as in {@code 2.5} or {@code a.b}. A word with dots can be a reference to a field.
   */
  public record Text(String text) implements Value {

    @Override
    public String input() {
      return text;
    }

    @Override
    public String toString() {
      return "text{" + quote(text) + "}";
    }
  }

  /**
   * A string in quotes, without the quotes and with the escapes applied.
   *
   * <p>
   * In AIP-160, a {@code *} at the start or at the end of a string in an equality is a wildcard. Then
   * {@code wildcards} is true. In CEL, a {@code *} is a usual character, and {@code wildcards} is false.
   */
  public record StringLiteral(String value, boolean wildcards) implements Value {

    @Override
    public String input() {
      return quote(value);
    }

    @Override
    public String toString() {
      return "string{" + (wildcards ? "wildcards," : "") + quote(value) + "}";
    }
  }

  /** A boolean literal of CEL: {@code true} or {@code false}. */
  public record BoolLiteral(boolean value) implements Value {

    @Override
    public String input() {
      return Boolean.toString(value);
    }

    @Override
    public String toString() {
      return "bool{" + value + "}";
    }
  }

  /** An integer literal of CEL, for example {@code 42} or {@code -30}. */
  public record IntLiteral(long value) implements Value {

    @Override
    public String input() {
      return Long.toString(value);
    }

    @Override
    public String toString() {
      return "int{" + value + "}";
    }
  }

  /** A floating-point literal of CEL, for example {@code 2.5}. */
  public record DoubleLiteral(double value) implements Value {

    @Override
    public String input() {
      return Double.toString(value);
    }

    @Override
    public String toString() {
      return "double{" + value + "}";
    }
  }

  /** A duration of CEL, for example {@code duration("1.5s")}. */
  public record DurationLiteral(Duration value) implements Value {

    @Override
    public String input() {
      return "duration(" + quote(seconds()) + ")";
    }

    @Override
    public String toString() {
      return "duration{" + seconds() + "}";
    }

    private String seconds() {
      BigDecimal seconds = BigDecimal.valueOf(value.getSeconds()).add(BigDecimal.valueOf(value.getNano(), 9));
      return seconds.stripTrailingZeros().toPlainString() + "s";
    }
  }

  /** A timestamp of CEL, for example {@code timestamp("2012-04-21T11:30:00-04:00")}. */
  public record TimestampLiteral(OffsetDateTime value) implements Value {

    @Override
    public String input() {
      return "timestamp(" + quote(DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(value)) + ")";
    }

    @Override
    public String toString() {
      return "timestamp{" + DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(value) + "}";
    }
  }
}
