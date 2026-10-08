package dev.horizon.aquery.ebnf;

import dev.horizon.aquery.aip160.TranslatingFilterParser;

/**
 * The parser of AIP-160 filter text, as the EBNF of <a href="https://google.aip.dev/160">AIP-160</a> specifies.
 *
 * <p>
 * The parser has two steps. The lexer and the recursive descent of {@link Parser} make the syntax tree of the
 * grammar. {@link EbnfTranslator} then changes it into the {@link dev.horizon.aquery.aip160.Filter} tree.
 *
 * <p>
 * Parentheses can have at most {@code MAX_DEPTH} (64) levels. Thus a deep filter cannot overflow the stack.
 *
 * <p>
 * Make one parser and use it for all requests:
 *
 * <pre>{@code
 * FilterParser parser = new EbnfFilterParser();
 * Filter filter = parser.parse("name = \"dan\" AND NOT locked = true");
 * }</pre>
 *
 * @see <a href="https://chromium.googlesource.com/infra/luci/luci-go/+/main/common/data/aip160/filter_parser.go">LUCI aip160: filter_parser.go (ParseFilter)</a>
 */
public final class EbnfFilterParser extends TranslatingFilterParser<Ast.Root> {

  public static final int MAX_DEPTH = 64;

  public EbnfFilterParser() {
    super(filter -> new Parser(filter).filter(), new EbnfTranslator());
  }
}
