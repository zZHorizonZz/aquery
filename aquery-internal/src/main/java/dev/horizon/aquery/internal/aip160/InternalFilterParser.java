package dev.horizon.aquery.internal.aip160;

import dev.horizon.aquery.aip160.Filter;
import dev.horizon.aquery.aip160.FilterParser;

/**
 * The parser the internal module provides to the api: the lexer and the recursive descent.
 *
 * @see <a href=
 *      "https://chromium.googlesource.com/infra/luci/luci-go/+/main/common/data/aip160/filter_parser.go">LUCI
 *      aip160: filter_parser.go (ParseFilter)</a>
 */
public final class InternalFilterParser implements FilterParser {

  @Override
  public Filter parse(String filter) {
    return new Parser(filter).filter();
  }
}
