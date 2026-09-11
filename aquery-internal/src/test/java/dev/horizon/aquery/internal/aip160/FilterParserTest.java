package dev.horizon.aquery.internal.aip160;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.horizon.aquery.InvalidQueryException;
import dev.horizon.aquery.aip160.Filter;
import dev.horizon.aquery.internal.aip160.Lexer.Kind;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class FilterParserTest {

  @ParameterizedTest(name = "[{0}] lexes as {1}({2})")
  @CsvSource(delimiter = '|', textBlock = """
      <= 10 | COMPARATOR | <=
      -file | NEGATE | -
      NOT file | NEGATE | NOT
      AND b | AND | AND
      OR a | OR | OR
      .field | DOT | .
      (arg) | LPAREN | (
      ) | RPAREN | )
      , arg2) | COMMA | ,
      text | TEXT | text
      "string" | STRING | "string"
      """)
  void tokenKinds(String input, Kind kind, String value) {
    Lexer.Token token = new Lexer(input).next();

    assertThat(token.kind()).as(input).isEqualTo(kind);
    assertThat(token.value()).as(input).isEqualTo(value);
  }

  @Test
  @DisplayName("lexes the tokens of a whole filter, whitespace and all")
  void whitespaceLexing() {
    String filter = "text \"string with whitespace\" (43 AND 44) OR 45"
        + " NOT function(arg1, arg2):hello -field1.field2: hello field < 36";
    Object[][] tokens = {
        { Kind.TEXT, "text" },
        { Kind.STRING, "\"string with whitespace\"" },
        { Kind.LPAREN, "(" },
        { Kind.TEXT, "43" },
        { Kind.AND, "AND" },
        { Kind.TEXT, "44" },
        { Kind.RPAREN, ")" },
        { Kind.OR, "OR" },
        { Kind.TEXT, "45" },
        { Kind.NEGATE, "NOT" },
        { Kind.TEXT, "function" },
        { Kind.LPAREN, "(" },
        { Kind.TEXT, "arg1" },
        { Kind.COMMA, "," },
        { Kind.TEXT, "arg2" },
        { Kind.RPAREN, ")" },
        { Kind.COMPARATOR, ":" },
        { Kind.TEXT, "hello" },
        { Kind.NEGATE, "-" },
        { Kind.TEXT, "field1" },
        { Kind.DOT, "." },
        { Kind.TEXT, "field2" },
        { Kind.COMPARATOR, ":" },
        { Kind.TEXT, "hello" },
        { Kind.TEXT, "field" },
        { Kind.COMPARATOR, "<" },
        { Kind.TEXT, "36" },
        { Kind.END, "" },
        { Kind.END, "" },
    };

    Lexer lexer = new Lexer(filter);
    for (Object[] expected : tokens) {
      Lexer.Token actual = lexer.next();
      assertThat(actual.kind()).as(actual.toString()).isEqualTo(expected[0]);
      assertThat(actual.value()).isEqualTo(expected[1]);
    }
  }

  @Test
  @DisplayName("each token knows where it starts and ends")
  void tokenOffsets() {
    Lexer lexer = new Lexer("  ab <= \"c\"");

    assertThat(lexer.next()).isEqualTo(new Lexer.Token(Kind.TEXT, "ab", 2, 4));
    assertThat(lexer.next()).isEqualTo(new Lexer.Token(Kind.COMPARATOR, "<=", 5, 7));
    assertThat(lexer.next()).isEqualTo(new Lexer.Token(Kind.STRING, "\"c\"", 8, 11));
  }

  static Stream<Arguments> parses() {
    return Stream.of(
        Arguments.of("", "filter{}"),
        Arguments.of(" ", "filter{}"),
        Arguments.of(
            "simple",
            "filter{expression{sequence{factor{term{simple{restriction{comparable{member{value{\"simple\"}}}}}}}}}}"),
        Arguments.of(
            " wsBefore",
            "filter{expression{sequence{factor{term{simple{restriction{comparable{member{value{\"wsBefore\"}}}}}}}}}}"),
        Arguments.of(
            "wsAfter ",
            "filter{expression{sequence{factor{term{simple{restriction{comparable{member{value{\"wsAfter\"}}}}}}}}}}"),
        Arguments.of(
            " wsAround ",
            "filter{expression{sequence{factor{term{simple{restriction{comparable{member{value{\"wsAround\"}}}}}}}}}}"),
        Arguments.of(
            "\"string\"",
            "filter{expression{sequence{factor{term{simple{restriction{comparable{member{value{quoted,\"string\"}}}}}}}}}}"),
        Arguments.of(
            " \"string\" ",
            "filter{expression{sequence{factor{term{simple{restriction{comparable{member{value{quoted,\"string\"}}}}}}}}}}"),
        Arguments.of(
            "\"ws string\"",
            "filter{expression{sequence{factor{term{simple{restriction{comparable{member{value{quoted,\"ws string\"}}}}}}}}}}"),
        Arguments.of(
            "-negated",
            "filter{expression{sequence{factor{term{-simple{restriction{comparable{member{value{\"negated\"}}}}}}}}}}"),
        Arguments.of(
            " - negated ",
            "filter{expression{sequence{factor{term{-simple{restriction{comparable{member{value{\"negated\"}}}}}}}}}}"),
        Arguments.of(
            "dash-separated-name",
            "filter{expression{sequence{factor{term{simple{restriction{comparable{member{value{\"dash-separated-name\"}}}}}}}}}}"),
        Arguments.of(
            "term -negated-term",
            "filter{expression{sequence{factor{term{simple{restriction{comparable{member{value{\"term\"}}}}}}},factor{term{-simple{restriction{comparable{member{value{\"negated-term\"}}}}}}}}}}"),
        Arguments.of(
            "NOT negated",
            "filter{expression{sequence{factor{term{-simple{restriction{comparable{member{value{\"negated\"}}}}}}}}}}"),
        Arguments.of(
            " NOT negated ",
            "filter{expression{sequence{factor{term{-simple{restriction{comparable{member{value{\"negated\"}}}}}}}}}}"),
        Arguments.of(
            " NOTnegated ",
            "filter{expression{sequence{factor{term{simple{restriction{comparable{member{value{\"NOTnegated\"}}}}}}}}}}"),
        Arguments.of(
            "implicit and",
            "filter{expression{sequence{factor{term{simple{restriction{comparable{member{value{\"implicit\"}}}}}}},factor{term{simple{restriction{comparable{member{value{\"and\"}}}}}}}}}}"),
        Arguments.of(
            " implicit and ",
            "filter{expression{sequence{factor{term{simple{restriction{comparable{member{value{\"implicit\"}}}}}}},factor{term{simple{restriction{comparable{member{value{\"and\"}}}}}}}}}}"),
        Arguments.of(
            "explicit AND and",
            "filter{expression{sequence{factor{term{simple{restriction{comparable{member{value{\"explicit\"}}}}}}}},sequence{factor{term{simple{restriction{comparable{member{value{\"and\"}}}}}}}}}}"),
        Arguments.of(
            " explicit AND and ",
            "filter{expression{sequence{factor{term{simple{restriction{comparable{member{value{\"explicit\"}}}}}}}},sequence{factor{term{simple{restriction{comparable{member{value{\"and\"}}}}}}}}}}"),
        Arguments.of(
            " explicit ANDnotand ",
            "filter{expression{sequence{factor{term{simple{restriction{comparable{member{value{\"explicit\"}}}}}}},factor{term{simple{restriction{comparable{member{value{\"ANDnotand\"}}}}}}}}}}"),
        Arguments.of(
            "test OR or",
            "filter{expression{sequence{factor{term{simple{restriction{comparable{member{value{\"test\"}}}}}},term{simple{restriction{comparable{member{value{\"or\"}}}}}}}}}}"),
        Arguments.of(
            "test ORnotor",
            "filter{expression{sequence{factor{term{simple{restriction{comparable{member{value{\"test\"}}}}}}},factor{term{simple{restriction{comparable{member{value{\"ORnotor\"}}}}}}}}}}"),
        Arguments.of(
            " test OR or ",
            "filter{expression{sequence{factor{term{simple{restriction{comparable{member{value{\"test\"}}}}}},term{simple{restriction{comparable{member{value{\"or\"}}}}}}}}}}"),
        Arguments.of(
            " testORor ",
            "filter{expression{sequence{factor{term{simple{restriction{comparable{member{value{\"testORor\"}}}}}}}}}}"),
        Arguments.of(
            "implicit and AND explicit",
            "filter{expression{sequence{factor{term{simple{restriction{comparable{member{value{\"implicit\"}}}}}}},factor{term{simple{restriction{comparable{member{value{\"and\"}}}}}}}},sequence{factor{term{simple{restriction{comparable{member{value{\"explicit\"}}}}}}}}}}"),
        Arguments.of(
            "implicit with OR term AND explicit OR term",
            "filter{expression{sequence{factor{term{simple{restriction{comparable{member{value{\"implicit\"}}}}}}},factor{term{simple{restriction{comparable{member{value{\"with\"}}}}}},term{simple{restriction{comparable{member{value{\"term\"}}}}}}}},sequence{factor{term{simple{restriction{comparable{member{value{\"explicit\"}}}}}},term{simple{restriction{comparable{member{value{\"term\"}}}}}}}}}}"),
        Arguments.of(
            "(composite)",
            "filter{expression{sequence{factor{term{simple{expression{sequence{factor{term{simple{restriction{comparable{member{value{\"composite\"}}}}}}}}}}}}}}}"),
        Arguments.of(
            " (composite) ",
            "filter{expression{sequence{factor{term{simple{expression{sequence{factor{term{simple{restriction{comparable{member{value{\"composite\"}}}}}}}}}}}}}}}"),
        Arguments.of(
            "( composite )",
            "filter{expression{sequence{factor{term{simple{expression{sequence{factor{term{simple{restriction{comparable{member{value{\"composite\"}}}}}}}}}}}}}}}"),
        Arguments.of(
            " ( composite ) ",
            "filter{expression{sequence{factor{term{simple{expression{sequence{factor{term{simple{restriction{comparable{member{value{\"composite\"}}}}}}}}}}}}}}}"),
        Arguments.of(
            " ( composite multi) ",
            "filter{expression{sequence{factor{term{simple{expression{sequence{factor{term{simple{restriction{comparable{member{value{\"composite\"}}}}}}},factor{term{simple{restriction{comparable{member{value{\"multi\"}}}}}}}}}}}}}}}"),
        Arguments.of(
            "value<21",
            "filter{expression{sequence{factor{term{simple{restriction{comparable{member{value{\"value\"}}},\"<\",arg{comparable{member{value{\"21\"}}}}}}}}}}}"),
        Arguments.of(
            "value < 21",
            "filter{expression{sequence{factor{term{simple{restriction{comparable{member{value{\"value\"}}},\"<\",arg{comparable{member{value{\"21\"}}}}}}}}}}}"),
        Arguments.of(
            " value < 21 ",
            "filter{expression{sequence{factor{term{simple{restriction{comparable{member{value{\"value\"}}},\"<\",arg{comparable{member{value{\"21\"}}}}}}}}}}}"),
        Arguments.of(
            "value<=21",
            "filter{expression{sequence{factor{term{simple{restriction{comparable{member{value{\"value\"}}},\"<=\",arg{comparable{member{value{\"21\"}}}}}}}}}}}"),
        Arguments.of(
            "value>21",
            "filter{expression{sequence{factor{term{simple{restriction{comparable{member{value{\"value\"}}},\">\",arg{comparable{member{value{\"21\"}}}}}}}}}}}"),
        Arguments.of(
            "value>=21",
            "filter{expression{sequence{factor{term{simple{restriction{comparable{member{value{\"value\"}}},\">=\",arg{comparable{member{value{\"21\"}}}}}}}}}}}"),
        Arguments.of(
            "value=21",
            "filter{expression{sequence{factor{term{simple{restriction{comparable{member{value{\"value\"}}},\"=\",arg{comparable{member{value{\"21\"}}}}}}}}}}}"),
        Arguments.of(
            "value!=21",
            "filter{expression{sequence{factor{term{simple{restriction{comparable{member{value{\"value\"}}},\"!=\",arg{comparable{member{value{\"21\"}}}}}}}}}}}"),
        Arguments.of(
            "value:21",
            "filter{expression{sequence{factor{term{simple{restriction{comparable{member{value{\"value\"}}},\":\",arg{comparable{member{value{\"21\"}}}}}}}}}}}"),
        Arguments.of(
            "value=(composite)",
            "filter{expression{sequence{factor{term{simple{restriction{comparable{member{value{\"value\"}}},\"=\",arg{expression{sequence{factor{term{simple{restriction{comparable{member{value{\"composite\"}}}}}}}}}}}}}}}}}"),
        Arguments.of(
            "member.field",
            "filter{expression{sequence{factor{term{simple{restriction{comparable{member{value{\"member\"}, {value{\"field\"}}}}}}}}}}}"),
        Arguments.of(
            " member.field > 4 ",
            "filter{expression{sequence{factor{term{simple{restriction{comparable{member{value{\"member\"}, {value{\"field\"}}}},\">\",arg{comparable{member{value{\"4\"}}}}}}}}}}}"),
        Arguments.of(
            " member.\"field\" > 4 ",
            "filter{expression{sequence{factor{term{simple{restriction{comparable{member{value{\"member\"}, {value{quoted,\"field\"}}}},\">\",arg{comparable{member{value{\"4\"}}}}}}}}}}}"),
        Arguments.of(
            "composite (expression)",
            "filter{expression{sequence{factor{term{simple{restriction{comparable{member{value{\"composite\"}}}}}}},factor{term{simple{expression{sequence{factor{term{simple{restriction{comparable{member{value{\"expression\"}}}}}}}}}}}}}}}"),
        Arguments.of(
            "value > -30",
            "filter{expression{sequence{factor{term{simple{restriction{comparable{member{value{\"value\"}}},\">\",arg{comparable{member{value{\"-30\"}}}}}}}}}}}"),
        Arguments.of(
            "value > -1.5s",
            "filter{expression{sequence{factor{term{simple{restriction{comparable{member{value{\"value\"}}},\">\",arg{comparable{member{value{\"-1\"}, {value{\"5s\"}}}}}}}}}}}}"),
        Arguments.of(
            "a.AND = \"x\"",
            "filter{expression{sequence{factor{term{simple{restriction{comparable{member{value{\"a\"}, {value{\"AND\"}}}},\"=\",arg{comparable{member{value{quoted,\"x\"}}}}}}}}}}}"),
        Arguments.of("explicit AND ", (String) null),
        Arguments.of("test OR ", (String) null));
  }

  @ParameterizedTest(name = "[{0}]")
  @MethodSource("parses")
  void parses(String input, String ast) {
    if (ast == null) {
      assertThatThrownBy(() -> Filter.parse(input)).as(input).isInstanceOf(InvalidQueryException.class);
    } else {
      assertThat(Filter.parse(input).toString()).as(input).isEqualTo(ast);
    }
  }

  @ParameterizedTest(name = "[{0}]")
  @ValueSource(strings = { "explicit AND ", "test OR ", "(unclosed", "value =", "value > - 30", "\"unterminated", "!x" })
  @DisplayName("refuses what it cannot read, naming the filter field")
  void refuses(String input) {
    assertThatThrownBy(() -> Filter.parse(input))
        .isInstanceOf(InvalidQueryException.class)
        .satisfies(thrown -> assertThat(((InvalidQueryException) thrown).field()).isEqualTo(InvalidQueryException.FILTER));
  }

  @ParameterizedTest(name = "[{0}] fails at {1}")
  @CsvSource(delimiter = '|', textBlock = """
      a = "b" !x | 8
      (a OR b | 7
      a = "unterminated | 4
      a = "\\q" | 5
      value = | 7
      """)
  @DisplayName("says where in the text the reading stopped")
  void errorPositions(String input, int position) {
    assertThatThrownBy(() -> Filter.parse(input))
        .isInstanceOf(InvalidQueryException.class)
        .satisfies(thrown -> assertThat(((InvalidQueryException) thrown).position()).isEqualTo(position));
  }

  @ParameterizedTest(name = "[{0}] reads as [{1}]")
  @CsvSource(delimiter = '|', quoteCharacter = '\'', textBlock = """
      "plain" | plain
      "a\\"b" | a"b
      "back\\\\slash" | back\\slash
      "caf\\u00e9" | café
      "\\x41\\101" | AA
      "\\U0001F600" | 😀
      """)
  @DisplayName("reads the escapes of Go string literals, as LUCI does")
  void escapes(String input, String expected) {
    String value = Filter.parse(input).expression().sequences().getFirst().factors().getFirst().terms().getFirst().simple()
        .restriction().comparable().member().value().value();

    assertThat(value).isEqualTo(expected);
  }

  @ParameterizedTest(name = "[{0}]")
  @ValueSource(strings = { "\"\\q\"", "\"\\'\"", "\"\\xZZ\"", "\"\\uD800\"", "\"\\400\"", "\"new\nline\"" })
  @DisplayName("refuses escapes Go does not have")
  void refusesEscapes(String input) {
    assertThatThrownBy(() -> Filter.parse(input)).isInstanceOf(InvalidQueryException.class);
  }

  @Test
  @DisplayName("reads a string as long as the limit without overflowing the stack")
  void longStrings() {
    String filter = "a = \"" + "x".repeat(Filter.MAX_LENGTH - 6) + "\"";

    assertThatNoException().isThrownBy(() -> Filter.parse(filter));
  }

  @Test
  @DisplayName("refuses parentheses nested deeper than the limit, rather than overflowing the stack")
  void depthLimit() {
    String allowed = "(".repeat(Filter.MAX_DEPTH) + "a" + ")".repeat(Filter.MAX_DEPTH);
    String tooDeep = "(".repeat(Filter.MAX_DEPTH + 1) + "a" + ")".repeat(Filter.MAX_DEPTH + 1);
    String deepest = "(".repeat(Filter.MAX_LENGTH / 2) + "a" + ")".repeat(Filter.MAX_LENGTH / 2 - 1);

    assertThatNoException().isThrownBy(() -> Filter.parse(allowed));
    assertThatThrownBy(() -> Filter.parse(tooDeep)).isInstanceOf(InvalidQueryException.class).hasMessageContaining("nest");
    assertThatThrownBy(() -> Filter.parse(deepest)).isInstanceOf(InvalidQueryException.class).hasMessageContaining("nest");
  }

  @ParameterizedTest(name = "[{0}]")
  @ValueSource(strings = { "regex(name, \"x\")", "foo(bar)", "a = f(x)", "m.key(x)" })
  @DisplayName("refuses function calls rather than reading them as a sequence")
  void refusesFunctionCalls(String input) {
    assertThatThrownBy(() -> Filter.parse(input))
        .isInstanceOf(InvalidQueryException.class)
        .hasMessageContaining("function calls are not supported");
  }
}
