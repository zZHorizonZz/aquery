package dev.horizon.aquery.cel.filter;

import dev.horizon.aquery.aip160.Filter.Condition;
import dev.horizon.aquery.aip160.InvalidFilterException;
import java.util.List;
import java.util.Optional;

/** A call in the text of a filter, as a {@link Operation} sees it. */
public interface Call {

  /**
   * Gives the name of the function.
   *
   * @return the name, for example {@code _==_}
   */
  String function();

  /**
   * Gives the function as the text writes it.
   *
   * @return the symbol of an operator, for example {@code ==}, or the name of a function
   */
  String symbol();

  /**
   * Gives the value that a member call is on, as the {@code name} of {@code name.startsWith("a")}.
   *
   * @return the value, or empty for a call by name
   */
  Optional<Operand> target();

  /**
   * Gives an argument.
   *
   * @param index the index of the argument, from 0
   * @return the argument
   */
  Operand argument(int index);

  /**
   * Gives the arguments.
   *
   * @return the arguments, in order
   */
  List<Operand> arguments();

  /**
   * Makes the error of a call that the operation refuses, at the call.
   *
   * @param message the message, a format of {@link String#format}
   * @param arguments the arguments of the format
   * @return the error, to throw
   */
  InvalidFilterException refuse(String message, Object... arguments);

  /**
   * Passes a condition that the operation builds inside the condition it returns through the decorators of the
   * grammar. The grammar decorates the returned condition itself, and the conditions of {@link Operand#condition()}
   * are decorated already, so an operation calls this only for the other conditions it builds, such as each
   * equality of {@code field in [a, b]}.
   *
   * @param condition the condition
   * @return the condition that the decorators give
   */
  Condition decorate(Condition condition);
}
