package dev.horizon.aquery.internal.aip160;

import dev.horizon.aquery.aip160.Filter;
import dev.horizon.aquery.aip160.InvalidFilterException;
import java.util.ArrayList;
import java.util.List;

/**
 * A lexer for AIP-160 filter text.
 *
 * <p>
 * The lexer reads the text one character at a time. It reads the same tokens as the LUCI regular expression, but
 * it uses no regular expression. Thus the time of a read is linear in the length of the text, and a long string
 * cannot overflow the stack.
 *
 * <p>
 * Each token has the offset of its first character and of the character after it. The parser uses the offsets in
 * its errors. It also uses them to find tokens with no whitespace between them.
 *
 * <p>
 * Two differences from LUCI make errors clearer. A string without a closing quote is an error, not text. A
 * character that starts no token is an error at its offset.
 *
 * @see <a href=
 *      "https://chromium.googlesource.com/infra/luci/luci-go/+/main/common/data/aip160/filter_parser.go">LUCI
 *      aip160: filter_parser.go (filterLexer)</a>
 */
final class Lexer {

  /** The kinds of tokens. */
  enum Kind {
    COMPARATOR,
    NEGATE,
    AND,
    OR,
    DOT,
    LPAREN,
    RPAREN,
    COMMA,
    STRING,
    TEXT,
    END
  }

  record Token(Kind kind, String value, int start, int end) {
  }

  private static final String TEXT_STOPS = ".,<>=!:()";

  private final String input;
  private int position;

  Lexer(String input) {
    this.input = input == null ? "" : input;
  }

  /**
   * Reads all tokens of the input.
   *
   * @return the tokens in the sequence of the input. The last token is {@link Kind#END}
   * @throws InvalidFilterException if a character starts no token, or if a string has no closing quote
   */
  List<Token> tokens() {
    List<Token> tokens = new ArrayList<>();
    Token token;
    do {
      token = next();
      tokens.add(token);
    } while (token.kind() != Kind.END);
    return tokens;
  }

  /**
   * Reads the next token.
   *
   * @return the next token. At the end of the input, each call gives an {@link Kind#END} token
   * @throws InvalidFilterException if a character starts no token, or if a string has no closing quote
   */
  Token next() {
    while (position < input.length() && isWhitespace(input.charAt(position))) {
      position++;
    }
    int start = position;
    if (start == input.length()) {
      return new Token(Kind.END, "", start, start);
    }

    return switch (input.charAt(start)) {
      case '<', '>' -> token(Kind.COMPARATOR, start, charAt(start + 1) == '=' ? 2 : 1);
      case '!' -> {
        if (charAt(start + 1) != '=') {
          throw new InvalidFilterException(start, "unable to lex token from %s", Filter.quote(input.substring(start)));
        }
        yield token(Kind.COMPARATOR, start, 2);
      }
      case '=', ':' -> token(Kind.COMPARATOR, start, 1);
      case '-' -> token(Kind.NEGATE, start, 1);
      case '.' -> token(Kind.DOT, start, 1);
      case '(' -> token(Kind.LPAREN, start, 1);
      case ')' -> token(Kind.RPAREN, start, 1);
      case ',' -> token(Kind.COMMA, start, 1);
      case '"' -> string(start);
      default -> keywordOrText(start);
    };
  }

  private Token keywordOrText(int start) {
    // A keyword needs whitespace after it. Thus "NOTother" is text, not a negation of "other".
    if (isKeyword(start, "NOT")) {
      return token(Kind.NEGATE, start, 3);
    }
    if (isKeyword(start, "AND")) {
      return token(Kind.AND, start, 3);
    }
    if (isKeyword(start, "OR")) {
      return token(Kind.OR, start, 2);
    }
    int end = start;
    while (end < input.length() && !isWhitespace(input.charAt(end)) && TEXT_STOPS.indexOf(input.charAt(end)) < 0) {
      end++;
    }
    return token(Kind.TEXT, start, end - start);
  }

  private Token string(int start) {
    int at = start + 1;
    while (at < input.length()) {
      char character = input.charAt(at);
      if (character == '\\') {
        // The escape and the character after it. The parser checks the escape.
        at += 2;
      } else if (character == '"') {
        return token(Kind.STRING, start, at + 1 - start);
      } else {
        at++;
      }
    }
    throw new InvalidFilterException(start, "unterminated string starting at position %d", start);
  }

  private boolean isKeyword(int start, String keyword) {
    int end = start + keyword.length();
    return input.startsWith(keyword, start) && end < input.length() && isWhitespace(input.charAt(end));
  }

  private Token token(Kind kind, int start, int length) {
    position = start + length;
    return new Token(kind, input.substring(start, position), start, position);
  }

  private char charAt(int index) {
    return index < input.length() ? input.charAt(index) : '\0';
  }

  /**
   * Tells if the character is whitespace: a space, a tab, a line feed, a carriage return, a form feed or a vertical
   * tab.
   *
   * @param character the character to check
   * @return true if the character is whitespace
   */
  static boolean isWhitespace(char character) {
    return character == ' ' || character == '\t' || character == '\n' || character == '\r' || character == '\f'
        || character == 0x0B;
  }
}
