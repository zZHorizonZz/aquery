package dev.horizon.aquery.cel.filter.operation;

import dev.horizon.aquery.aip132.FieldPath;
import dev.horizon.aquery.aip160.Filter.Condition;
import dev.horizon.aquery.aip160.Filter.Or;
import dev.horizon.aquery.aip160.Filter.Restriction;
import dev.horizon.aquery.aip160.Operator;
import dev.horizon.aquery.cel.filter.Call;
import dev.horizon.aquery.cel.filter.Next;
import dev.horizon.aquery.cel.filter.Operand;
import dev.horizon.aquery.cel.filter.Operation;
import java.util.List;

/**
 * {@code field in [a, b]}: the field equals one of the constants. {@code "x" in field}: the field contains the value
 * as an element or as a key.
 */
public final class Membership implements Operation {

  private final String function;

  /**
   * Translates the calls of one function.
   *
   * @param function the function, for example {@code _in_}
   */
  public Membership(String function) {
    this.function = function;
  }

  @Override
  public String function() {
    return function;
  }

  @Override
  public Condition translate(Call call, Next next) {
    Operand element = call.argument(0);
    Operand collection = call.argument(1);
    return collection.list().map(values -> oneOf(call, element, values, collection)).orElseGet(() -> {
      if (!collection.isField()) {
        throw collection.refuse("expected a field or a list after 'in' but found %s", collection.describe());
      }
      return new Restriction(collection.path(), Operator.IN, element.value());
    });
  }

  private Condition oneOf(Call call, Operand element, List<Operand> values, Operand list) {
    if (!element.isField()) {
      throw element.refuse("expected a field before 'in' a list but found %s", element.describe());
    }
    if (values.isEmpty()) {
      throw list.refuse("the list after 'in' is empty");
    }
    FieldPath field = element.path();
    List<Condition> equalities = values.stream()
        .<Condition> map(value -> new Restriction(field, Operator.EQUALS, value.value())).toList();
    return equalities.size() == 1 ? equalities.getFirst() : new Or(equalities.stream().map(call::decorate).toList());
  }
}
