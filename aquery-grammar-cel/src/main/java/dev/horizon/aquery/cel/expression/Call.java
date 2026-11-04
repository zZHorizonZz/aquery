package dev.horizon.aquery.cel.expression;

import dev.horizon.aquery.cel.Type;

import java.util.List;
import java.util.Optional;

/**
 * A call in a text that the type checker checked, as an {@link Operation} sees it. The arguments are nodes, each
 * translated when it is read.
 */
public interface Call {

  /**
   * Gives the name of the function.
   *
   * @return the name, for example {@code _+_}
   */
  String function();

  /**
   * Gives the value that a member call is on, as the {@code name} of {@code name.lower()}.
   *
   * @return the value, or empty for a call by name
   */
  Optional<Expression> target();

  /**
   * Gives an argument.
   *
   * @param index the index of the argument, from 0
   * @return the argument
   */
  Expression argument(int index);

  /**
   * Gives the arguments.
   *
   * @return the arguments, in order
   */
  List<Expression> arguments();

  /**
   * Tells if an argument is the constant {@code null}, which is not a node.
   *
   * @param index the index of the argument, from 0
   * @return true for {@code null}
   */
  boolean isNull(int index);

  /**
   * Gives the overload of the operation that the type checker chose for the call.
   *
   * @return the overload, or empty if the checker chose an overload of CEL
   */
  Optional<Overload> overload();

  /**
   * Gives the type of the value of the call.
   *
   * @return the type
   */
  Type type();

  /**
   * Gives the offset of the call in the text.
   *
   * @return the offset in characters, or -1 if it is not known
   */
  int position();

  /**
   * Makes the error of a call that the operation refuses, at the call.
   *
   * @param message the message, a format of {@link String#format}
   * @param arguments the arguments of the format
   * @return the error, to throw
   */
  InvalidExpressionException refuse(String message, Object... arguments);

  /**
   * Makes the error of a call that the operation refuses, at one of its arguments.
   *
   * @param index the index of the argument, from 0
   * @param message the message, a format of {@link String#format}
   * @param arguments the arguments of the format
   * @return the error, to throw
   */
  InvalidExpressionException refuse(int index, String message, Object... arguments);
}
