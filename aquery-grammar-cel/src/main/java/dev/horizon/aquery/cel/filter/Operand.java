package dev.horizon.aquery.cel.filter;

import dev.horizon.aquery.aip132.FieldPath;
import dev.horizon.aquery.aip160.Filter.Condition;
import dev.horizon.aquery.aip160.Filter.Value;
import dev.horizon.aquery.aip160.InvalidFilterException;
import java.util.List;
import java.util.Optional;

/**
 * An argument of a {@link Call}, or the value that the call is on. An operation reads it as what its place in
 * the call is: a field, a constant, a condition or a list.
 */
public interface Operand {

  /**
   * Tells if the operand is the path of a field: a name, a member of a field, or a key of a field that is a string
   * constant, as in {@code labels["site-1"]}.
   *
   * @return true for a field
   */
  boolean isField();

  /**
   * Reads the operand as the path of a field.
   *
   * @return the path
   * @throws InvalidFilterException if the operand is not a field
   */
  FieldPath path();

  /**
   * Reads the operand as a constant. A name is the text of an enum value, as the {@code ACTIVE} of
   * {@code status == ACTIVE}.
   *
   * @return the value
   * @throws InvalidFilterException if the operand is not a constant
   */
  Value value();

  /**
   * Reads the operand as a condition.
   *
   * @return the condition
   * @throws InvalidFilterException if the operand is not a condition that the grammar supports
   */
  Condition condition();

  /**
   * Reads the operand as a list, as the {@code [ACTIVE, PENDING]} of {@code status in [ACTIVE, PENDING]}.
   *
   * @return the elements, or empty if the operand is not a list
   */
  Optional<List<Operand>> list();

  /**
   * Tells if the operand is a call of a function.
   *
   * @param function the name of the function, for example {@code _&&_}
   * @return true if it is
   */
  boolean calls(String function);

  /**
   * Names the operand in an error message.
   *
   * @return the name, for example {@code the field 'name'} or {@code a constant}
   */
  String describe();

  /**
   * Makes the error of an operand that the operation refuses, at the operand.
   *
   * @param message the message, a format of {@link String#format}
   * @param arguments the arguments of the format
   * @return the error, to throw
   */
  InvalidFilterException refuse(String message, Object... arguments);
}
