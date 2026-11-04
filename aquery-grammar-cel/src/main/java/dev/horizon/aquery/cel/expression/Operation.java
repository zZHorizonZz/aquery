package dev.horizon.aquery.cel.expression;

import java.util.List;

/**
 * A function or an operator of CEL, and the node that a call of it is.
 *
 * <p>
 * A {@link CelGrammar} chains the operations of each function. A call goes to the first operation of its function,
 * which translates it, passes it to the {@link Next} operations, or changes what they give. An operation that a
 * caller adds comes first, so it decorates the operations of the grammar:
 *
 * <pre>{@code
 * public Expression translate(Call call, Next next) {
 *   if (call.argument(0) instanceof Expression.FieldReference field && field.field().name().equals("secret")) {
 *     throw call.refuse(0, "the field secret cannot be compared");
 *   }
 *   return next.translate(call);
 * }
 * }</pre>
 *
 * <p>
 * An operation of an operator or a function that CEL declares, such as {@code _==_} or {@code timestamp}, needs no
 * overloads of its own. An operation of a function of the caller names the overloads that the type checker accepts.
 */
public interface Operation {

  /**
   * Gives the name of the function in CEL.
   *
   * @return the name, for example {@code _==_}, {@code _?_:_} or {@code changed}
   */
  String function();

  /**
   * Gives the overloads that this operation declares besides those of CEL.
   *
   * @return the overloads, or none for a function that CEL declares
   */
  List<Overload> overloads();

  /**
   * Translates a call.
   *
   * @param call the call
   * @param next the operations of the function after this one
   * @return the node of the call
   * @throws InvalidExpressionException if the call has a form that this operation refuses, as {@link Call#refuse}
   * gives it
   */
  Expression translate(Call call, Next next);
}
