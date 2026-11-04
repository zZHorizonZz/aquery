package dev.horizon.aquery.cel.expression.operation;

import dev.horizon.aquery.cel.Type;
import dev.horizon.aquery.cel.expression.Call;
import dev.horizon.aquery.cel.expression.Expression;
import dev.horizon.aquery.cel.expression.Expression.ListLiteral;
import dev.horizon.aquery.cel.expression.Expression.Parameter;
import dev.horizon.aquery.cel.expression.Next;
import dev.horizon.aquery.cel.expression.Operation;
import dev.horizon.aquery.cel.expression.Overload;
import java.util.List;

/**
 * {@code value in collection}: a value is an element of a list, a list parameter or a list field, or a key of a map
 * field. A key in a map field must be a constant or a parameter.
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
  public List<Overload> overloads() {
    return List.of();
  }

  @Override
  public Expression translate(Call call, Next next) {
    Expression element = call.argument(0);
    return call.argument(1).inside(collection -> switch (collection) {
      case ListLiteral _,Parameter _ -> new Expression.Membership(element, collection, call.position());
      case Expression map when map.json() && map.type() instanceof Type.MapOf && !element.constant() ->
        throw call.refuse(0, "a key 'in' a map must be a constant or a parameter");
      case Expression json when json.json() -> new Expression.Membership(element, json, call.position());
      default -> throw call.refuse(1, "expected a list, a list parameter, or a list or map field after 'in'");
    });
  }
}
