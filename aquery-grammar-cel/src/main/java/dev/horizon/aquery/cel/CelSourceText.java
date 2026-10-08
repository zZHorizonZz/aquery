package dev.horizon.aquery.cel;

import dev.cel.common.CelSource;
import dev.cel.common.CelSourceLocation;

/**
 * Finds offsets in the text of a CEL source.
 *
 * <p>
 * cel-java gives positions in code points. An {@link dev.horizon.aquery.InvalidQueryException} gives offsets in
 * characters, as the other parsers do. The two are different for characters outside the Basic Multilingual Plane.
 */
final class CelSourceText {

  private final CelSource source;
  private final String text;

  CelSourceText(CelSource source) {
    this.source = source;
    this.text = source.getContent().toString();
  }

  /**
   * Gives the offset of an expression in the text.
   *
   * @param exprId the id of the expression in the syntax tree
   * @return the offset in characters, or -1 if the source has no position for the expression
   */
  int offsetOf(long exprId) {
    Integer codePoints = source.getPositionsMap().get(exprId);
    return codePoints == null ? -1 : charOffset(codePoints);
  }

  /**
   * Gives the offset of a location in the text.
   *
   * @param location the line and the column that cel-java gives
   * @return the offset in characters, or -1 if the location is not in the text
   */
  int offsetOf(CelSourceLocation location) {
    return source.getLocationOffset(location).map(this::charOffset).orElse(-1);
  }

  private int charOffset(int codePoints) {
    if (codePoints < 0 || codePoints > text.codePointCount(0, text.length())) {
      return -1;
    }
    return text.offsetByCodePoints(0, codePoints);
  }
}
