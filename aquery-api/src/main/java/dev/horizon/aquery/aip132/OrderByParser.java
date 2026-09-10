package dev.horizon.aquery.aip132;

import java.util.List;

/**
 * Parses AIP-132 order_by text and AIP-161 field paths.
 *
 * <p>
 * This interface connects the api to the implementation. The api module reads the implementation from the module
 * path or the class path at runtime. The internal module supplies it.
 *
 * @see Order#parse
 * @see FieldPath#parse
 */
public interface OrderByParser {

  /**
   * Parses an order_by list. Text with only spaces gives an empty list.
   *
   * @param orderBy the order_by text from the client. It can be null
   * @return the terms of the order, in the sequence of the text
   * @throws InvalidOrderByException if the text does not follow the order_by grammar, or if a field occurs two times
   */
  List<OrderBy> parse(String orderBy);

  /**
   * Parses one field path.
   *
   * @param path the text of the path
   * @return the parsed path
   * @throws InvalidOrderByException if the text is not a field path
   */
  FieldPath parseFieldPath(String path);
}
