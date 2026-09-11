package dev.horizon.aquery.internal.aip157;

import dev.horizon.aquery.aip132.FieldPath;
import dev.horizon.aquery.aip157.InvalidReadMaskException;
import dev.horizon.aquery.aip157.ReadMask;
import dev.horizon.aquery.aip157.ReadMaskParser;
import dev.horizon.aquery.internal.aip161.FieldPathScanner;
import dev.horizon.aquery.internal.aip161.FieldPathSyntaxException;
import java.util.ArrayList;
import java.util.List;

/**
 * The read mask parser that the internal module supplies to the api. It reads the paths with
 * {@link FieldPathScanner}.
 *
 * <p>
 * Spaces before and after a comma have no meaning. An entry {@code *} means all fields.
 */
public final class InternalReadMaskParser implements ReadMaskParser {

  @Override
  public ReadMask parse(String readMask) {
    if (readMask == null || readMask.isBlank()) {
      return new ReadMask();
    }

    FieldPathScanner scanner = new FieldPathScanner(readMask);
    List<FieldPath> paths = new ArrayList<>();
    boolean allFields = false;
    try {
      do {
        scanner.skipSpaces();
        if (scanner.accept('*')) {
          allFields = true;
        } else {
          paths.add(scanner.fieldPath());
        }
        scanner.skipSpaces();
      } while (scanner.accept(','));
    } catch (FieldPathSyntaxException syntax) {
      throw new InvalidReadMaskException(syntax.position(), syntax.getMessage());
    }

    if (!scanner.atEnd()) {
      throw new InvalidReadMaskException(scanner.position(), "syntax error: expected ',' or the end at position %d of '%s'",
          scanner.position(), readMask);
    }
    return new ReadMask(allFields, paths);
  }
}
