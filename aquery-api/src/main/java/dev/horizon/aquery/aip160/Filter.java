package dev.horizon.aquery.aip160;

import static java.util.stream.Collectors.joining;

import dev.horizon.aquery.common.ServiceProvider;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * A parsed AIP-160 filter. The filter can be empty.
 *
 * <p>
 * This type is the root of the filter AST. The AST follows the EBNF of
 * <a href="https://google.aip.dev/160">AIP-160</a>. The parser does not support the function call syntax.
 *
 * <p>
 * An empty filter matches all rows. Blank text gives an empty filter. An empty filter compiles to {@code (1 = 1)}
 * and binds no values.
 *
 * <p>
 * The tree below this root follows the AIP-160 grammar. Each type is one level of the grammar. The shape of the
 * tree gives the operator precedence of the language:
 *
 * <ul>
 * <li>An {@link Expression} is a conjunction. The client writes {@code AND} between its sequences, or writes the
 * sequences one after the other.
 * <li>A {@link Sequence} is one or more factors, one after the other. With exact match semantics, a sequence has
 * the same meaning as AND.
 * <li>A {@link Factor} is a disjunction. The client writes {@code OR} between its terms.
 * <li>A {@link Term} is one {@link Simple}, with an optional negation. The client writes the negation as
 * {@code NOT} or {@code -}.
 * <li>A {@link Simple} is a {@link Restriction} or an expression in parentheses.
 * </ul>
 *
 * <p>
 * {@code OR} binds more tightly than a sequence. A sequence binds more tightly than {@code AND}. Thus
 * {@code a OR b c} is {@code (a OR b) AND c}, and {@code a b AND c} is {@code (a AND b) AND c}.
 *
 * <p>
 * The {@code AND}, {@code OR} and {@code NOT} keywords are case-sensitive. A word that contains a keyword, as in
 * {@code ORnotor}, is a usual value, not a keyword.
 *
 * <p>
 * The parser has two limits, as AIP-160 lets a service specify:
 *
 * <ul>
 * <li>The filter text can have at most {@code MAX_LENGTH} (16 KB) characters.
 * <li>Parentheses can have at most {@code MAX_DEPTH} (64) levels.
 * </ul>
 *
 * <p>
 * {@link #toString()} writes the tree in a stable form. Tests and error messages use it. It is not the text that
 * the client wrote.
 *
 * @see <a href="https://chromium.googlesource.com/infra/luci/luci-go/+/main/common/data/aip160/filter_parser.go">LUCI aip160: filter_parser.go (Filter AST)</a>
 */
public final class Filter {

  public static final int MAX_LENGTH = 16 * 1024;
  public static final int MAX_DEPTH = 64;

  private static final ServiceProvider<FilterParser> PARSER = new ServiceProvider<>(FilterParser.class);

  private final Expression expression;

  public Filter(Expression expression) {
    this.expression = expression;
  }

  /**
   * Parses AIP-160 filter text into an AST.
   *
   * <p>
   * The parser comes from the module path or the class path at runtime. The internal module supplies it.
   *
   * @param filter the text that the client wrote. Null or blank text means no filter
   * @return the parsed filter
   * @throws InvalidFilterException if the parser cannot read the text, or if the text is longer than {@code MAX_LENGTH}
   */
  public static Filter parse(String filter) {
    if (filter != null && filter.length() > MAX_LENGTH) {
      throw new InvalidFilterException("the filter is too long: %d characters, at most %d", filter.length(), MAX_LENGTH);
    }
    return PARSER.get().parse(filter);
  }

  /**
   * Gives the expression of the filter.
   *
   * @return the expression, or null if the filter is empty
   */
  public Expression expression() {
    return expression;
  }

  @Override
  public String toString() {
    return "filter{" + (expression == null ? "" : expression) + "}";
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

  private static String present(String prefix, Object first, Object second) {
    return Stream.of(first, second).filter(Objects::nonNull).map(String::valueOf).collect(joining(",", prefix + "{", "}"));
  }

  /**
   * An expression: a conjunction (AND) of sequences, or one sequence.
   *
   * <p>
   * The AND is case-sensitive.
   *
   * <p>
   * Example: {@code a b AND c AND d}. The expression {@code (a b) AND c AND d} has the same meaning.
   */
  public record Expression(List<Sequence> sequences) {

    public Expression {
      sequences = List.copyOf(sequences);
    }

    @Override
    public String toString() {
      return joined("expression", sequences);
    }
  }

  /**
   * A sequence: one or more factors with whitespace between them.
   *
   * <p>
   * With exact match semantics, a sequence has the same meaning as AND. Fuzzy match semantics are different.
   *
   * <p>
   * Example: {@code New York Giants OR Yankees}. The expression {@code New York (Giants OR Yankees)} has the same
   * meaning.
   */
  public record Sequence(List<Factor> factors) {

    public Sequence {
      factors = List.copyOf(factors);
    }

    @Override
    public String toString() {
      return joined("sequence", factors);
    }
  }

  /**
   * A factor: a disjunction (OR) of terms, or one term.
   *
   * <p>
   * The OR is case-sensitive.
   *
   * <p>
   * Example: {@code a < 10 OR a >= 100}
   */
  public record Factor(List<Term> terms) {

    public Factor {
      terms = List.copyOf(terms);
    }

    @Override
    public String toString() {
      return joined("factor", terms);
    }
  }

  /**
   * A term: a simple expression with an optional negation.
   *
   * <p>
   * The negation is {@code -} or {@code NOT}. The two forms have the same meaning. The {@code NOT} is
   * case-sensitive, and whitespace must follow it.
   */
  public record Term(boolean negated, Simple simple) {

    @Override
    public String toString() {
      return "term{" + (negated ? "-" : "") + simple + "}";
    }
  }

  /**
   * A simple expression: a restriction or an expression in parentheses (a composite).
   *
   * <p>
   * Example of a composite: {@code (a OR b) AND c < 10}
   */
  public record Simple(Restriction restriction, Expression composite) {

    @Override
    public String toString() {
      return present("simple", restriction, composite);
    }
  }

  /**
   * A restriction: a comparable, an optional operator and an optional argument.
   *
   * <p>
   * A restriction with only a comparable is a global restriction. The generator matches the value of a global
   * restriction with all fields for implicit filters.
   *
   * <p>
   * Examples:
   *
   * <ul>
   * <li>equality: {@code package=com.google}
   * <li>inequality: {@code msg != "hello"}
   * <li>greater than: {@code 1 > 0}
   * <li>has: {@code map:key}
   * <li>global: {@code prod}
   * </ul>
   */
  public record Restriction(Comparable comparable, Operator operator, Arg arg) {

    @Override
    public String toString() {
      String written = comparable == null ? "" : String.valueOf(comparable);
      if (operator != null) {
        written += (written.isEmpty() ? "" : ",") + quote(operator.symbol());
      }
      if (arg != null) {
        written += (written.isEmpty() ? "" : ",") + arg;
      }
      return "restriction{" + written + "}";
    }
  }

  /**
   * An argument: a comparable, or an expression in parentheses (a composite).
   *
   * <p>
   * Most backends refuse composite arguments. The message names the field of the argument.
   */
  public record Arg(Comparable comparable, Expression composite) {

    @Override
    public String toString() {
      return present("arg", comparable, composite);
    }
  }

  /**
   * A comparable. The parser does not support functions. Thus a comparable is always a member.
   */
  public record Comparable(Member member) {

    @Override
    public String toString() {
      return "comparable{" + member + "}";
    }
  }

  /**
   * A member: a value and the field references after it, with dots between them. An example is
   * {@code expr.type_map.1.type}.
   *
   * <p>
   * The value names a field. The references go into that field. The schema tells which fields accept this.
   */
  public record Member(Value value, List<Value> fields) {

    public Member {
      fields = List.copyOf(fields);
    }

    /**
     * Gives the input text of the member, for error messages.
     *
     * @return the value and the fields with a dot between them. A quoted value is in quotes
     */
    public String input() {
      return Stream.concat(Stream.of(value), fields.stream()).filter(Objects::nonNull).map(Value::input).collect(joining("."));
    }

    @Override
    public String toString() {
      return "member{" + value + (fields.isEmpty() ? "" : joined(", ", fields)) + "}";
    }
  }

  /**
   * A value: TEXT or STRING.
   *
   * <p>
   * The literal rules of AIP-160 depend on this difference. Strings are in double quotes. Booleans, integers,
   * durations and enum values have no quotes. A string backend refuses a value without quotes, because the value
   * can be a field reference. A boolean backend refuses a value in quotes, because {@code "true"} is a string, not
   * a boolean.
   */
  public record Value(boolean quoted, String value) {

    /**
     * Gives the input text of the value, for error messages.
     *
     * @return the value, in quotes if the client quoted it
     */
    public String input() {
      return quoted ? quote(value) : value;
    }

    @Override
    public String toString() {
      return "value{" + (quoted ? "quoted," : "") + quote(value) + "}";
    }
  }
}
