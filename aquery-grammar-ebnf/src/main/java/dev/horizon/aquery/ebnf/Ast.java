package dev.horizon.aquery.ebnf;

import static dev.horizon.aquery.aip160.Filter.quote;
import static java.util.stream.Collectors.joining;

import dev.horizon.aquery.aip160.Operator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * The syntax tree of AIP-160 filter text.
 *
 * <p>
 * The tree follows the EBNF of <a href="https://google.aip.dev/160">AIP-160</a>. Each type is one level of the
 * grammar. The shape of the tree gives the operator precedence of the language:
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
 * The {@link Parser} makes this tree. {@link EbnfTranslator} then changes it into the {@link dev.horizon.aquery.aip160.Filter}
 * tree of the api. {@code toString()} writes the tree in a stable form for the tests.
 *
 * @see <a href="https://chromium.googlesource.com/infra/luci/luci-go/+/main/common/data/aip160/filter_parser.go">LUCI aip160: filter_parser.go (Filter AST)</a>
 */
final class Ast {

  private Ast() {
  }

  private static String joined(String prefix, List<?> parts) {
    return parts.stream().map(String::valueOf).collect(joining(",", prefix + "{", "}"));
  }

  private static String present(String prefix, Object first, Object second) {
    return Stream.of(first, second).filter(Objects::nonNull).map(String::valueOf).collect(joining(",", prefix + "{", "}"));
  }

  /** The root of the tree. The expression is null if the text is blank. */
  record Root(Expression expression) {

    @Override
    public String toString() {
      return "filter{" + (expression == null ? "" : expression) + "}";
    }
  }

  /** An expression: a conjunction (AND) of sequences, or one sequence. */
  record Expression(List<Sequence> sequences) {

    Expression {
      sequences = List.copyOf(sequences);
    }

    @Override
    public String toString() {
      return joined("expression", sequences);
    }
  }

  /** A sequence: one or more factors with whitespace between them. */
  record Sequence(List<Factor> factors) {

    Sequence {
      factors = List.copyOf(factors);
    }

    @Override
    public String toString() {
      return joined("sequence", factors);
    }
  }

  /** A factor: a disjunction (OR) of terms, or one term. */
  record Factor(List<Term> terms) {

    Factor {
      terms = List.copyOf(terms);
    }

    @Override
    public String toString() {
      return joined("factor", terms);
    }
  }

  /** A term: a simple expression with an optional negation. */
  record Term(boolean negated, Simple simple) {

    @Override
    public String toString() {
      return "term{" + (negated ? "-" : "") + simple + "}";
    }
  }

  /** A simple expression: a restriction or an expression in parentheses (a composite). */
  record Simple(Restriction restriction, Expression composite) {

    @Override
    public String toString() {
      return present("simple", restriction, composite);
    }
  }

  /**
   * A restriction: a comparable, an optional operator and an optional argument. A restriction with only a
   * comparable is a global restriction.
   */
  record Restriction(Comparable comparable, Operator operator, Arg arg) {

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

  /** An argument: a comparable, or an expression in parentheses (a composite). */
  record Arg(Comparable comparable, Expression composite) {

    @Override
    public String toString() {
      return present("arg", comparable, composite);
    }
  }

  /** A comparable. The parser does not support functions. Thus a comparable is always a member. */
  record Comparable(Member member) {

    @Override
    public String toString() {
      return "comparable{" + member + "}";
    }
  }

  /**
   * A member: a value and the field references after it, with dots between them. An example is
   * {@code expr.type_map.1.type}.
   */
  record Member(Value value, List<Value> fields) {

    Member {
      fields = List.copyOf(fields);
    }

    /**
     * Gives the input text of the member, for error messages.
     *
     * @return the value and the fields with a dot between them. A quoted value is in quotes
     */
    String input() {
      return Stream.concat(Stream.of(value), fields.stream()).filter(Objects::nonNull).map(Value::input).collect(joining("."));
    }

    @Override
    public String toString() {
      return "member{" + value + (fields.isEmpty() ? "" : joined(", ", fields)) + "}";
    }
  }

  /** A value: TEXT or STRING. Strings are in double quotes. */
  record Value(boolean quoted, String value) {

    /**
     * Gives the input text of the value, for error messages.
     *
     * @return the value, in quotes if the client quoted it
     */
    String input() {
      return quoted ? quote(value) : value;
    }

    @Override
    public String toString() {
      return "value{" + (quoted ? "quoted," : "") + quote(value) + "}";
    }
  }
}
