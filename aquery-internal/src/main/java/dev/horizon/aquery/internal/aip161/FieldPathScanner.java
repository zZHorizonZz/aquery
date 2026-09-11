package dev.horizon.aquery.internal.aip161;

import dev.horizon.aquery.aip132.FieldPath;
import dev.horizon.aquery.common.Identifiers;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads AIP-161 field paths in small parts: spaces, segments, words and separators.
 *
 * <p>
 * The order_by parser and the read mask parser both use this scanner. Thus the field path grammar is in one
 * location. The scanner reads the grammar that LUCI parses:
 *
 * <pre>
 * field_path = segment {"." segment}
 * segment = string | quoted_string
 * string = (letter | "_") {letter | "_" | digit}
 * quoted_string = "`" { utf8-no-backtick | "`" "`" } "`"
 * spaces = " " { " " }
 * </pre>
 *
 * <p>
 * The scanner does not support the {@code *} wildcard of AIP-161 in a segment.
 *
 * @see <a href="https://chromium.googlesource.com/infra/luci/luci-go/+/main/common/data/aip132/orderby_parser.go">LUCI aip132:
 *      orderby_parser.go (orderByLexer)</a>
 */
public final class FieldPathScanner {

  private final String text;
  private int position;

  public FieldPathScanner(String text) {
    this.text = text;
  }

  /**
   * Gives the position of the scanner.
   *
   * @return the offset of the next character in the text
   */
  public int position() {
    return position;
  }

  /**
   * Tells if the scanner read all the text.
   *
   * @return true if no character is left
   */
  public boolean atEnd() {
    return position >= text.length();
  }

  /**
   * Moves past the spaces. Only the space character is a space, as in the grammar.
   *
   * @return true if there was a space
   */
  public boolean skipSpaces() {
    int start = position;
    while (!atEnd() && text.charAt(position) == ' ') {
      position++;
    }
    return position > start;
  }

  /**
   * Moves past the character if it is the next character.
   *
   * @param character the expected character
   * @return true if the next character was the expected character
   */
  public boolean accept(char character) {
    if (!atEnd() && text.charAt(position) == character) {
      position++;
      return true;
    }
    return false;
  }

  /**
   * Reads a field path: segments with a dot between them.
   *
   * @return the field path
   * @throws FieldPathSyntaxException if a segment is missing, if a segment is a wildcard, or if a backtick is open
   */
  public FieldPath fieldPath() {
    List<String> segments = new ArrayList<>();
    segments.add(segment());
    while (accept('.')) {
      segments.add(segment());
    }
    return new FieldPath(segments);
  }

  /**
   * Reads a word of letters, digits and underscores. A word does not start with a digit.
   *
   * @return the word, or null if no word starts at this position
   */
  public String word() {
    if (atEnd() || !Identifiers.isStart(text.charAt(position))) {
      return null;
    }
    int start = position;
    do {
      position++;
    } while (!atEnd() && Identifiers.isPart(text.charAt(position)));
    return text.substring(start, position);
  }

  private String segment() {
    if (accept('`')) {
      return quotedSegment(position - 1);
    }
    if (!atEnd() && text.charAt(position) == '*') {
      throw new FieldPathSyntaxException(position,
          "syntax error: the wildcard '*' is not supported in a field path, at position %d of '%s'".formatted(position, text));
    }
    String word = word();
    if (word == null) {
      throw new FieldPathSyntaxException(position,
          "syntax error: expected a field name at position %d of '%s'".formatted(position, text));
    }
    return word;
  }

  private String quotedSegment(int start) {
    StringBuilder segment = new StringBuilder();
    while (!atEnd()) {
      char character = text.charAt(position++);
      // In quotes, two backticks are one backtick. One backtick ends the segment.
      if (character == '`' && !accept('`')) {
        return segment.toString();
      }
      segment.append(character);
    }
    throw new FieldPathSyntaxException(start,
        "syntax error: unterminated backtick at position %d of '%s'".formatted(start, text));
  }
}
