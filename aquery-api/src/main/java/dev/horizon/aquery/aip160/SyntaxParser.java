package dev.horizon.aquery.aip160;

/**
 * Parses filter text into the syntax tree of one language.
 *
 * <p>
 * The syntax tree follows the grammar of the language. A {@link FilterTranslator} then changes it into the
 * {@link Filter} tree. {@link TranslatingFilterParser} connects the two steps.
 *
 * @param <T> the type of the syntax tree
 */
@FunctionalInterface
public interface SyntaxParser<T> {

  /**
   * Parses filter text into a syntax tree.
   *
   * <p>
   * {@link TranslatingFilterParser} gives only text that is not blank and not longer than {@code Filter.MAX_LENGTH}.
   *
   * @param filter the text that the client wrote
   * @return the syntax tree
   * @throws InvalidFilterException if the text does not follow the grammar of the language
   */
  T parse(String filter);
}
