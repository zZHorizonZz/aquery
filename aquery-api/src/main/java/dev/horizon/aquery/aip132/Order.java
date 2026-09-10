package dev.horizon.aquery.aip132;

import dev.horizon.aquery.common.ServiceProvider;
import java.util.List;

/**
 * Parses AIP-132 order_by clauses.
 *
 * <p>
 * The order_by argument of a List request is a list of clauses with commas between them. Each clause is a field
 * path. A direction can follow the field path. The field paths and the direction words are case-sensitive. The
 * grammar is:
 *
 * <pre>
 * order_by_list = order_by_clause {[spaces] "," order_by_clause} [spaces]
 * order_by_clause = field_path order
 * field_path = [spaces] segment {"." segment}
 * order = [spaces ("desc" | "asc")]
 * segment = string | quoted_string;
 * string = (letter | "_") {letter | "_" | digit}
 * quoted_string = "`" { utf8-no-backtick | "`" "`" } "`"
 * spaces = " " { " " }
 * </pre>
 *
 * <p>
 * This is the LUCI grammar with one addition: {@code asc} can name the default direction. A segment in backticks
 * can contain commas and spaces.
 *
 * <p>
 * The parser checks the syntax. It also makes sure that each field occurs only one time. It does not check that
 * the fields are sortable. The table does that check when it writes the clause, because only the table has that
 * data.
 *
 * <p>
 * The parser comes from the module path or the class path at runtime. The internal module supplies it.
 *
 * @see <a href="https://chromium.googlesource.com/infra/luci/luci-go/+/main/common/data/aip132/orderby_parser.go">LUCI aip132:
 *      orderby_parser.go (ParseOrderBy)</a>
 */
public final class Order {

  static final ServiceProvider<OrderByParser> PARSER = new ServiceProvider<>(OrderByParser.class);

  private Order() {
  }

  /**
   * Parses an order_by list into its parts. Text with only spaces gives an empty list: no order.
   *
   * @param orderBy the order_by text from the client. It can be null
   * @return the terms of the order, in the sequence of the text. The list cannot change
   * @throws InvalidOrderByException if the syntax is wrong, if a direction is not {@code asc} or {@code desc}, or if a field
   *         occurs two times
   */
  public static List<OrderBy> parse(String orderBy) {
    return PARSER.get().parse(orderBy);
  }
}
