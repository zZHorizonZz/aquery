package dev.horizon.aquery.aip160;

/**
 * Changes the syntax tree of one language into the {@link Filter} tree.
 *
 * <p>
 * The translator refuses the forms of the language that no backend can answer, for example a function call that
 * SQL does not have. It does not read the schema. {@link WhereClause} compares the filter with the schema later.
 *
 * @param <T> the type of the syntax tree
 */
@FunctionalInterface
public interface FilterTranslator<T> {

  /**
   * Changes a syntax tree into a filter.
   *
   * @param tree the syntax tree from the {@link SyntaxParser} of the language
   * @return the filter
   * @throws InvalidFilterException if the tree has a form that no backend can answer
   */
  Filter translate(T tree);
}
