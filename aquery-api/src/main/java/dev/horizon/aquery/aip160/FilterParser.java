package dev.horizon.aquery.aip160;

/**
 * Parses filter text of one language into a {@link Filter} tree.
 *
 * <p>
 * Each language has its own implementation in its own module. The {@code aquery-grammar-ebnf} module has the AIP-160
 * parser. The {@code aquery-grammar-cel} module has the CEL parser. The server makes the parser of the language that it
 * accepts, and keeps it. Then {@link WhereClause} compiles the filters of all parsers in the same way.
 *
 * <p>
 * Most implementations are a {@link TranslatingFilterParser}: a {@link SyntaxParser} for the grammar of the
 * language and a {@link FilterTranslator} for its syntax tree.
 *
 * @see <a href="https://chromium.googlesource.com/infra/luci/luci-go/+/main/common/data/aip160/filter_parser.go">LUCI aip160: filter_parser.go (ParseFilter)</a>
 */
public interface FilterParser {

  /**
   * Parses filter text. Blank text parses to an empty filter.
   *
   * @param filter the text that the client wrote. Null or blank text means no filter
   * @return the parsed filter
   * @throws InvalidFilterException if the text does not follow the grammar of the language, or if the text is longer than
   * {@code Filter.MAX_LENGTH}
   */
  Filter parse(String filter);
}
