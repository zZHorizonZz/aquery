package dev.horizon.aquery.aip160;

/**
 * A {@link FilterParser} in two steps: a {@link SyntaxParser} and a {@link FilterTranslator}.
 *
 * <p>
 * This class applies the rules that are the same for all languages. Null or blank text gives an empty filter. Text
 * longer than {@code Filter.MAX_LENGTH} characters is refused before the syntax parser reads it. All other text
 * goes to the syntax parser, and its tree goes to the translator.
 *
 * <p>
 * The parser of a language extends this class and gives its two steps to the constructor. An instance has no
 * state that changes. Thus one instance can parse the filters of all requests.
 *
 * @param <T> the type of the syntax tree
 */
public class TranslatingFilterParser<T> implements FilterParser {

  private final SyntaxParser<T> syntaxParser;
  private final FilterTranslator<T> translator;

  public TranslatingFilterParser(SyntaxParser<T> syntaxParser, FilterTranslator<T> translator) {
    this.syntaxParser = syntaxParser;
    this.translator = translator;
  }

  @Override
  public final Filter parse(String filter) {
    if (filter != null && filter.length() > Filter.MAX_LENGTH) {
      throw new InvalidFilterException("the filter is too long: %d characters, at most %d", filter.length(),
          Filter.MAX_LENGTH);
    }
    if (filter == null || filter.isBlank()) {
      return new Filter(null);
    }
    return translator.translate(syntaxParser.parse(filter));
  }
}
