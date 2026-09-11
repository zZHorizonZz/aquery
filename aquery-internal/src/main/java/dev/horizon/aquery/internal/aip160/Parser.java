package dev.horizon.aquery.internal.aip160;

import dev.horizon.aquery.aip160.Filter;
import dev.horizon.aquery.aip160.Filter.Arg;
import dev.horizon.aquery.aip160.Filter.Comparable;
import dev.horizon.aquery.aip160.Filter.Expression;
import dev.horizon.aquery.aip160.Filter.Factor;
import dev.horizon.aquery.aip160.Filter.Member;
import dev.horizon.aquery.aip160.Filter.Restriction;
import dev.horizon.aquery.aip160.Filter.Sequence;
import dev.horizon.aquery.aip160.Filter.Simple;
import dev.horizon.aquery.aip160.Filter.Term;
import dev.horizon.aquery.aip160.Filter.Value;
import dev.horizon.aquery.aip160.InvalidFilterException;
import dev.horizon.aquery.aip160.Operator;
import dev.horizon.aquery.internal.aip160.Lexer.Kind;
import dev.horizon.aquery.internal.aip160.Lexer.Token;
import java.util.ArrayList;
import java.util.List;

/**
 * A recursive-descent parser for AIP-160 filter expressions.
 *
 * <p>
 * The parser implements this EBNF, with lexer tokens:
 *
 * <pre>
 * filter: [expression];
 * expression: sequence {WS AND WS sequence};
 * sequence: factor {WS factor};
 * factor: term {WS OR WS term};
 * term: [NEGATE] simple;
 * simple: restriction | composite;
 * restriction: comparable [COMPARATOR arg];
 * comparable: member;
 * member: (TEXT | STRING) {DOT (TEXT | STRING | keyword)};
 * composite: LPAREN expression RPAREN;
 * arg: comparable | composite;
 * </pre>
 *
 * <p>
 * The parser differs from LUCI in these points:
 *
 * <ul>
 * <li>Parentheses can have at most {@code Filter.MAX_DEPTH} levels. Thus a deep filter cannot overflow the stack.
 * <li>A minus sign immediately before a text argument is part of the argument. Thus {@code age > -30} compares
 * with a negative number. A minus sign with whitespace after it is a negation.
 * <li>A word immediately before a parenthesis is a function call. The parser refuses it, because it does not
 * support functions. A word and a parenthesis with whitespace between them are a sequence.
 * <li>A keyword can be a field after a dot, as the EBNF permits.
 * </ul>
 *
 * <p>
 * The escapes in strings are the escapes of Go string literals, as in LUCI: {@code \a \b \f \n \r \t \v \\ \"},
 * {@code \xHH}, {@code \UHHHHHHHH}, a backslash and a lowercase u with four hexadecimal digits, and a backslash
 * with three octal digits. The parser refuses all other escapes and a new line in a string.
 *
 * @see <a href=
 * "https://chromium.googlesource.com/infra/luci/luci-go/+/main/common/data/aip160/filter_parser.go">LUCI
 * aip160: filter_parser.go (parser)</a>
 */
final class Parser {

  private final List<Token> tokens;
  private int index;
  private int depth;

  Parser(String input) {
    this.tokens = new Lexer(input).tokens();
  }

  /**
   * Parses all the input.
   *
   * @return the parsed filter. The filter is empty if the input has no tokens
   * @throws InvalidFilterException if the input does not follow the grammar, or if parentheses nest too deep
   */
  Filter filter() {
    if (peek().kind() == Kind.END) {
      return new Filter(null);
    }
    Expression expression = expression();
    expect(Kind.END);
    return new Filter(expression);
  }

  private Expression expression() {
    Sequence first = sequence();
    if (first == null) {
      return null;
    }
    List<Sequence> sequences = new ArrayList<>();
    sequences.add(first);
    while (accept(Kind.AND) != null) {
      sequences.add(required(sequence(), "expected sequence after AND"));
    }
    return new Expression(sequences);
  }

  private Sequence sequence() {
    List<Factor> factors = new ArrayList<>();
    for (Factor factor = factor(); factor != null; factor = factor()) {
      factors.add(factor);
    }
    return factors.isEmpty() ? null : new Sequence(factors);
  }

  private Factor factor() {
    Term first = term();
    if (first == null) {
      return null;
    }
    List<Term> terms = new ArrayList<>();
    terms.add(first);
    while (accept(Kind.OR) != null) {
      terms.add(required(term(), "expected term after OR"));
    }
    return new Factor(terms);
  }

  private Term term() {
    Token negation = accept(Kind.NEGATE);
    Simple simple = simple();
    if (simple == null) {
      if (negation != null) {
        throw new InvalidFilterException(peek().start(), "expected simple term after negation '%s'", negation.value());
      }
      return null;
    }
    return new Term(negation != null, simple);
  }

  private Simple simple() {
    Restriction restriction = restriction();
    if (restriction != null) {
      return new Simple(restriction, null);
    }
    Expression composite = composite();
    return composite == null ? null : new Simple(null, composite);
  }

  private Restriction restriction() {
    Member member = member();
    if (member == null) {
      return null;
    }
    Token comparator = accept(Kind.COMPARATOR);
    if (comparator == null) {
      return new Restriction(new Comparable(member), null, null);
    }
    Arg arg = arg();
    if (arg == null) {
      throw new InvalidFilterException(peek().start(), "expected arg after %s", comparator.value());
    }
    return new Restriction(new Comparable(member), Operator.ofSymbol(comparator.value()), arg);
  }

  private Member member() {
    Value value = value();
    return value == null ? null : memberAfter(value);
  }

  private Member memberAfter(Value value) {
    List<Value> fields = new ArrayList<>();
    while (accept(Kind.DOT) != null) {
      fields.add(required(field(), "expected value after '.'"));
    }
    Token last = tokens.get(index - 1);
    if (last.kind() != Kind.STRING && peek().kind() == Kind.LPAREN && peek().start() == last.end()) {
      throw new InvalidFilterException(last.start(), "function calls are not supported, but '%s' is followed by '('",
          last.value());
    }
    return new Member(value, fields);
  }

  private Value value() {
    Token string = accept(Kind.STRING);
    if (string != null) {
      return new Value(true, unquote(string));
    }
    Token text = accept(Kind.TEXT);
    return text == null ? null : new Value(false, text.value());
  }

  private Value field() {
    Value value = value();
    if (value != null) {
      return value;
    }
    Token keyword = peek();
    boolean isKeyword = keyword.kind() == Kind.AND || keyword.kind() == Kind.OR
        || (keyword.kind() == Kind.NEGATE && keyword.value().equals("NOT"));
    if (!isKeyword) {
      return null;
    }
    index++;
    return new Value(false, keyword.value());
  }

  private Expression composite() {
    Token open = accept(Kind.LPAREN);
    if (open == null) {
      return null;
    }
    if (++depth > Filter.MAX_DEPTH) {
      throw new InvalidFilterException(open.start(), "parentheses nest more than %d levels deep", Filter.MAX_DEPTH);
    }
    Expression expression = required(expression(), "expected expression after '('");
    expect(Kind.RPAREN);
    depth--;
    return expression;
  }

  private Arg arg() {
    Token minus = peek();
    if (minus.kind() == Kind.NEGATE && minus.value().equals("-")) {
      Token text = tokens.get(index + 1);
      if (text.kind() == Kind.TEXT && text.start() == minus.end()) {
        // A minus sign immediately before text is a negative literal, as in 'age > -30'.
        index += 2;
        return new Arg(new Comparable(memberAfter(new Value(false, "-" + text.value()))), null);
      }
    }
    Member member = member();
    if (member != null) {
      return new Arg(new Comparable(member), null);
    }
    Expression composite = composite();
    return composite == null ? null : new Arg(null, composite);
  }

  private Token peek() {
    return tokens.get(index);
  }

  private Token accept(Kind kind) {
    return peek().kind() == kind ? tokens.get(index++) : null;
  }

  private void expect(Kind kind) {
    Token token = peek();
    if (token.kind() != kind) {
      throw new InvalidFilterException(token.start(), "expected %s but got %s(%s)", kind, token.kind(), token.value());
    }
    index++;
  }

  private <T> T required(T parsed, String message) {
    if (parsed == null) {
      throw new InvalidFilterException(peek().start(), message);
    }
    return parsed;
  }

  /**
   * Removes the quotes of a string token and applies its escapes.
   *
   * @param token the string token, with its quotes
   * @return the value of the string
   * @throws InvalidFilterException if the string has an escape that Go does not have, or a new line
   */
  static String unquote(Token token) {
    String quoted = token.value();
    StringBuilder value = new StringBuilder(quoted.length());
    int end = quoted.length() - 1;
    for (int i = 1; i < end; i++) {
      char character = quoted.charAt(i);
      if (character == '\n') {
        throw new InvalidFilterException(token.start() + i, "a string cannot contain a new line; write \\n");
      }
      if (character != '\\') {
        value.append(character);
        continue;
      }
      int escape = i;
      char kind = quoted.charAt(++i);
      boolean octal = kind >= '0' && kind <= '7';
      int digits = switch (kind) {
        case 'x' -> 2;
        case 'u' -> 4;
        case 'U' -> 8;
        default -> octal ? 3 : 0;
      };
      int codePoint;
      if (digits == 0) {
        codePoint = switch (kind) {
          case 'a' -> 0x07;
          case 'b' -> '\b';
          case 'f' -> '\f';
          case 'n' -> '\n';
          case 'r' -> '\r';
          case 't' -> '\t';
          case 'v' -> 0x0B;
          case '\\', '"' -> kind;
          default -> -1;
        };
      } else {
        // An octal escape starts at its first digit. The other escapes start after their letter.
        int from = octal ? i : i + 1;
        codePoint = number(quoted, from, digits, octal ? 8 : 16, end);
        i = from + digits - 1;
      }
      boolean isByte = kind == 'x' || octal;
      boolean valid = codePoint >= 0
          && (isByte ? codePoint <= 0xFF : codePoint <= Character.MAX_CODE_POINT && (codePoint < 0xD800 || codePoint > 0xDFFF));
      if (!valid) {
        throw new InvalidFilterException(token.start() + escape, "invalid escape '%s' in string %s",
            quoted.substring(escape, Math.min(escape + 2, end)), quoted);
      }
      value.appendCodePoint(codePoint);
    }
    return value.toString();
  }

  /**
   * Reads the digits of an escape.
   *
   * @param quoted the string token, with its quotes
   * @param from the offset of the first digit
   * @param digits the number of digits
   * @param radix 8 for octal digits, 16 for hexadecimal digits
   * @param end the offset of the closing quote
   * @return the number, or -1 if the string has too few digits or a character that is not a digit
   */
  private static int number(String quoted, int from, int digits, int radix, int end) {
    if (from + digits > end) {
      return -1;
    }
    int result = 0;
    for (int i = from; i < from + digits; i++) {
      int digit = Character.digit(quoted.charAt(i), radix);
      if (digit < 0) {
        return -1;
      }
      result = result * radix + digit;
    }
    return result;
  }
}
