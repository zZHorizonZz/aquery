package dev.horizon.aquery.aip132;

import dev.horizon.aquery.common.Identifiers;
import java.util.List;

/**
 * The path to a field in a resource, as AIP-161 specifies.
 *
 * <p>
 * A path is a list of segments. The traversal operator ({@code .}) is between the segments. A segment is a bare
 * word of letters, digits and underscores. Other segments are strings in backticks. For example, for this
 * message:
 *
 * <pre>{@code
 * message MyThing {
 *   message Bar {
 *     string foobar = 2;
 *   }
 *   string foo = 1;
 *   Bar bar = 2;
 *   map<string, Bar> named_bars = 3;
 * }
 * }</pre>
 *
 * <p>
 * some valid paths are {@code foo}, {@code bar.foobar} and {@code named_bars.`bar-key`.foobar}.
 *
 * <p>
 * Two paths name the same field if they have equal segments. Each path has one canonical form. The canonical
 * form writes a bare word as it is, and it puts all other segments in backticks. {@link #toString()} gives the
 * canonical form. The path makes the canonical form only when a caller asks for it the first time. Thus a map
 * lookup does not make it.
 *
 * @see <a href=
 *      "https://chromium.googlesource.com/infra/luci/luci-go/+/main/common/data/aip132/orderby_parser.go">LUCI
 *      aip132: orderby_parser.go (FieldPath)</a>
 */
public final class FieldPath {

  private final List<String> segments;
  private String canonical;

  public FieldPath(String... segments) {
    this(List.of(segments));
  }

  public FieldPath(List<String> segments) {
    this.segments = List.copyOf(segments);
  }

  /**
   * Parses a path that a client wrote.
   *
   * <p>
   * The parser reads bare words and segments in backticks. In backticks, two backticks are one backtick. The
   * internal module supplies the parser, as for {@link Order#parse}.
   *
   * @param path the text of the path
   * @return the parsed path
   * @throws InvalidOrderByException if the path is empty, ends with a dot, has an open backtick, or has other characters
   */
  public static FieldPath parse(String path) {
    return Order.PARSER.get().parseFieldPath(path);
  }

  /**
   * Gives the segments of the path.
   *
   * @return the segments, first to last. The list cannot change
   */
  public List<String> segments() {
    return segments;
  }

  @Override
  public String toString() {
    String result = canonical;
    if (result == null) {
      StringBuilder written = new StringBuilder();
      for (String segment : segments) {
        if (!written.isEmpty()) {
          written.append('.');
        }
        if (Identifiers.isIdentifier(segment)) {
          written.append(segment);
        } else {
          written.append('`').append(segment.replace("`", "``")).append('`');
        }
      }
      result = written.toString();
      canonical = result;
    }
    return result;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof FieldPath path && segments.equals(path.segments);
  }

  @Override
  public int hashCode() {
    return segments.hashCode();
  }
}
