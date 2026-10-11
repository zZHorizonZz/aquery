package dev.horizon.aquery.cel;

import static dev.horizon.aquery.aip160.Operator.ENDS_WITH;
import static dev.horizon.aquery.aip160.Operator.EQUALS;
import static dev.horizon.aquery.aip160.Operator.GREATER_EQUALS;
import static dev.horizon.aquery.aip160.Operator.GREATER_THAN;
import static dev.horizon.aquery.aip160.Operator.HAS;
import static dev.horizon.aquery.aip160.Operator.LESS_EQUALS;
import static dev.horizon.aquery.aip160.Operator.LESS_THAN;
import static dev.horizon.aquery.aip160.Operator.NOT_EQUALS;
import static dev.horizon.aquery.aip160.Operator.STARTS_WITH;

import dev.horizon.aquery.aip160.Filter;
import dev.horizon.aquery.aip160.FilterTranslator;
import dev.horizon.aquery.aip160.InvalidFilterException;
import dev.horizon.aquery.shaded.cel.common.CelAbstractSyntaxTree;
import dev.horizon.aquery.shaded.cel.common.Operator;
import java.util.HashMap;
import java.util.Map;

/**
 * Changes a CEL syntax tree into the {@link Filter} tree of the api.
 *
 * <p>
 * The translator knows the operation of each CEL operator and function that a filter supports; the operation of a
 * call checks its arguments and gives the condition. The translator refuses all other calls with an
 * {@link InvalidFilterException}, whose position is the offset of the call in the text.
 *
 * <p>
 * The translator has no state; each tree gets its own {@link FilterTranslation}.
 */
final class CelFilterTranslator implements FilterTranslator<CelAbstractSyntaxTree> {

  private final Map<String, FilterOperation> operations = new HashMap<>();

  CelFilterTranslator() {
    operations.put(Operator.LOGICAL_AND.getFunction(), new FilterJunction(true));
    operations.put(Operator.LOGICAL_OR.getFunction(), new FilterJunction(false));
    operations.put(Operator.LOGICAL_NOT.getFunction(), new FilterNegation());
    operations.put(Operator.EQUALS.getFunction(), new FilterComparison(EQUALS, EQUALS));
    operations.put(Operator.NOT_EQUALS.getFunction(), new FilterComparison(NOT_EQUALS, NOT_EQUALS));
    operations.put(Operator.LESS.getFunction(), new FilterComparison(LESS_THAN, GREATER_THAN));
    operations.put(Operator.LESS_EQUALS.getFunction(), new FilterComparison(LESS_EQUALS, GREATER_EQUALS));
    operations.put(Operator.GREATER.getFunction(), new FilterComparison(GREATER_THAN, LESS_THAN));
    operations.put(Operator.GREATER_EQUALS.getFunction(), new FilterComparison(GREATER_EQUALS, LESS_EQUALS));
    operations.put(Operator.IN.getFunction(), new FilterMembership());
    operations.put(Operator.OLD_IN.getFunction(), new FilterMembership());
    operations.put("contains", new FilterStringMatch(HAS));
    operations.put("startsWith", new FilterStringMatch(STARTS_WITH));
    operations.put("endsWith", new FilterStringMatch(ENDS_WITH));
    operations.put(Operator.CONDITIONAL.getFunction(),
        new RefusedOperation("the conditional operator '? :' is not supported in a filter"));
    operations.put("matches", new RefusedOperation(
        "matches() is not supported in a filter, because ISO SQL has no portable regular expressions"));
  }

  @Override
  public Filter translate(CelAbstractSyntaxTree syntax) {
    CelTree tree = new CelTree(syntax, null);
    try {
      return new FilterTranslation(tree, operations).filter();
    } catch (Refusal refusal) {
      throw new InvalidFilterException(tree.position(refusal), "%s", refusal.getMessage());
    }
  }
}
