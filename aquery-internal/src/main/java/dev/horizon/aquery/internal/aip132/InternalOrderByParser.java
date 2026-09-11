package dev.horizon.aquery.internal.aip132;

import dev.horizon.aquery.aip132.FieldPath;
import dev.horizon.aquery.aip132.InvalidOrderByException;
import dev.horizon.aquery.aip132.OrderBy;
import dev.horizon.aquery.aip132.OrderByParser;
import dev.horizon.aquery.internal.aip161.FieldPathScanner;
import dev.horizon.aquery.internal.aip161.FieldPathSyntaxException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The order_by parser that the internal module supplies to the api. It reads the text one token at a time with
 * {@link FieldPathScanner}.
 *
 * @see <a href="https://chromium.googlesource.com/infra/luci/luci-go/+/main/common/data/aip132/orderby_parser.go">LUCI aip132:
 *      orderby_parser.go (ParseOrderBy)</a>
 */
public final class InternalOrderByParser implements OrderByParser {

  @Override
  public List<OrderBy> parse(String orderBy) {
    if (orderBy == null || orderBy.chars().allMatch(character -> character == ' ')) {
      return List.of();
    }

    FieldPathScanner scanner = new FieldPathScanner(orderBy);
    List<OrderBy> result = new ArrayList<>();
    Set<FieldPath> seen = new HashSet<>();
    try {
      do {
        scanner.skipSpaces();
        int start = scanner.position();
        OrderBy term = new OrderBy(scanner.fieldPath(), descending(scanner));
        if (!seen.add(term.fieldPath())) {
          throw new InvalidOrderByException(start, "field appears multiple times: '%s'", term.fieldPath());
        }
        result.add(term);
        scanner.skipSpaces();
      } while (scanner.accept(','));
    } catch (FieldPathSyntaxException syntax) {
      throw new InvalidOrderByException(syntax.position(), syntax.getMessage());
    }

    if (!scanner.atEnd()) {
      throw new InvalidOrderByException(scanner.position(), "syntax error: expected ',' or the end at position %d of '%s'",
          scanner.position(), orderBy);
    }
    return List.copyOf(result);
  }

  @Override
  public FieldPath parseFieldPath(String path) {
    FieldPathScanner scanner = new FieldPathScanner(path);
    FieldPath result;
    try {
      result = scanner.fieldPath();
    } catch (FieldPathSyntaxException syntax) {
      throw new InvalidOrderByException(syntax.position(), syntax.getMessage());
    }
    if (!scanner.atEnd()) {
      throw new InvalidOrderByException(scanner.position(), "syntax error: unexpected '%c' at position %d of field path '%s'",
          path.charAt(scanner.position()), scanner.position(), path);
    }
    return result;
  }

  private static boolean descending(FieldPathScanner scanner) {
    if (!scanner.skipSpaces()) {
      return false;
    }
    int start = scanner.position();
    String word = scanner.word();
    if (word == null) {
      return false;
    }
    return switch (word) {
      case "desc" -> true;
      case "asc" -> false;
      default -> throw new InvalidOrderByException(start, "'%s' is not a direction; write asc or desc", word);
    };
  }
}
