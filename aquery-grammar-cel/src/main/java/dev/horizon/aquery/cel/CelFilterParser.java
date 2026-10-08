package dev.horizon.aquery.cel;

import dev.cel.common.CelAbstractSyntaxTree;
import dev.horizon.aquery.aip160.TranslatingFilterParser;

/**
 * The parser of CEL filter text.
 *
 * <p>
 * The parser has two steps. {@link CelSyntaxParser} parses the text with cel-java. {@link CelTranslator} then
 * changes the CEL syntax tree into the {@link dev.horizon.aquery.aip160.Filter} tree of the api.
 *
 * <p>
 * The parser does not evaluate the expression, and it does not check the types. The schema of the table and the
 * backends do that when {@link dev.horizon.aquery.aip160.WhereClause} compiles the filter.
 *
 * <p>
 * The parser supports the part of CEL that a WHERE clause can answer:
 *
 * <ul>
 * <li>{@code &&}, {@code ||}, {@code !} and parentheses.
 * <li>The comparisons {@code == != < <= > >=} between a field and a constant. The constant can be on the left
 * side, as in {@code 18 <= age}.
 * <li>A field alone is a boolean field that must be true, as in {@code !locked}.
 * <li>{@code name.contains("x")}, {@code name.startsWith("x")} and {@code name.endsWith("x")}.
 * <li>{@code status in [ACTIVE, PENDING]}: one of the constants in the list. {@code "x" in tags}: the field
 * contains the value as an element or as a key.
 * <li>{@code has(labels.site)}: the field has the member or the key.
 * <li>{@code labels["site-1"]}: a key that is not an identifier.
 * <li>The constants: strings, integers, floats, booleans, {@code duration("1.5s")} and
 * {@code timestamp("2012-04-21T11:30:00Z")}. An identifier on the right side, as in {@code status == ACTIVE}, is an
 * enum value.
 * </ul>
 *
 * <p>
 * The parser refuses all other CEL: arithmetic, the conditional operator, {@code matches()}, {@code size()},
 * macros such as {@code exists()}, maps, messages, bytes and {@code null}. The error tells the client what is not
 * supported.
 *
 * <p>
 * A CEL string has no wildcards. Thus {@code name == "a*"} compares with {@code =}. Use {@code startsWith()}.
 *
 * <p>
 * Make one parser and use it for all requests:
 *
 * <pre>{@code
 * FilterParser parser = new CelFilterParser();
 * Filter filter = parser.parse("name == \"dan\" && !locked");
 * }</pre>
 *
 * @see <a href="https://github.com/google/cel-spec/blob/master/doc/langdef.md">CEL language definition</a>
 */
public final class CelFilterParser extends TranslatingFilterParser<CelAbstractSyntaxTree> {

  public CelFilterParser() {
    super(new CelSyntaxParser(), new CelTranslator());
  }
}
