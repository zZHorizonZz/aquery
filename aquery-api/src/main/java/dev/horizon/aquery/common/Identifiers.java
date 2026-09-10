package dev.horizon.aquery.common;

/**
 * The rule for a bare word: letters, digits and underscores, with no digit at the start.
 *
 * <p>
 * AIP-161 uses this rule for a field path segment without backticks. Standard SQL uses the same rule for an
 * identifier without quotes. The field paths, the order_by parser and the SQL generator all use this class. Thus the
 * rule is in one location.
 *
 * <p>
 * The letters are the ASCII letters only, as in LUCI.
 */
public final class Identifiers {

  private Identifiers() {
  }

  /**
   * Tells if the character can start an identifier: a letter or an underscore.
   *
   * @param character the character to check
   * @return true if the character can start an identifier
   */
  public static boolean isStart(char character) {
    return character == '_' || (character >= 'a' && character <= 'z') || (character >= 'A' && character <= 'Z');
  }

  /**
   * Tells if the character can be in an identifier after the start: a letter, a digit or an underscore.
   *
   * @param character the character to check
   * @return true if the character can be in an identifier after the start
   */
  public static boolean isPart(char character) {
    return isStart(character) || (character >= '0' && character <= '9');
  }

  /**
   * Tells if the text is an identifier.
   *
   * @param text the text to check
   * @return true if the text is an identifier. An empty text is not an identifier
   */
  public static boolean isIdentifier(String text) {
    if (text.isEmpty() || !isStart(text.charAt(0))) {
      return false;
    }
    for (int i = 1; i < text.length(); i++) {
      if (!isPart(text.charAt(i))) {
        return false;
      }
    }
    return true;
  }
}
