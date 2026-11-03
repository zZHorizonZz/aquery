package dev.horizon.aquery.cel;

import java.util.Optional;

/** A call that a {@link Macro} rewrites, and the syntax that it can write in its place. */
public interface MacroCall {

  /**
   * Gives the value that a member call is on.
   *
   * @return the value, or empty for a call by name
   */
  Optional<Syntax> target();

  /**
   * Gives an argument.
   *
   * @param index the index of the argument, from 0
   * @return the argument
   */
  Syntax argument(int index);

  /**
   * Gives the row of the expression, whose members are the fields of the scope.
   *
   * @return the row
   */
  Syntax row();

  /**
   * Writes {@code operand.field}.
   *
   * @param operand the value
   * @param field the name of the member
   * @return the syntax
   */
  Syntax select(Syntax operand, String field);

  /**
   * Writes whether a value has a member, as {@code has(operand.field)} means.
   *
   * @param operand the value
   * @param field the name of the member
   * @return the syntax
   */
  Syntax presence(Syntax operand, String field);

  /**
   * Writes a call by name.
   *
   * @param function the name, for example {@code _&&_}
   * @param arguments the arguments
   * @return the syntax
   */
  Syntax call(String function, Syntax... arguments);

  /**
   * Writes an error in place of the call, which the parser reports.
   *
   * @param message the message
   * @return the syntax of the error
   */
  Syntax refuse(String message);
}
