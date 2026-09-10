package dev.horizon.aquery.aip160;

/**
 * Parses AIP-160 filter text into a {@link Filter} AST.
 *
 * <p>
 * This is the seam between the api and the implementation behind it. The api module reads the implementation
 * from the module path or the class path at runtime. The internal module provides it.
 *
 * <p>
 * A different implementation — a parser with better error positions, or a parser of a profile of the language
 * — is a provider of this interface, not an edit to the api.
 *
 * @see <a href=
 *      "https://chromium.googlesource.com/infra/luci/luci-go/+/main/common/data/aip160/filter_parser.go">LUCI
 *      aip160: filter_parser.go (ParseFilter)</a>
 */
public interface FilterParser {

  /**
   * Parses filter text. Blank text parses to an empty filter.
   *
   * @param filter the text that the client wrote. Null or blank text means no filter
   * @return the parsed filter
   * @throws InvalidFilterException if the text does not follow the AIP-160 grammar
   */
  Filter parse(String filter);
}
