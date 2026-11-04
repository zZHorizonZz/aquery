package dev.horizon.aquery.cel;

import java.util.Optional;

/**
 * A part of the text of an expression before the type checker reads it, which a {@link Macro} reads and writes.
 *
 * <p>
 * A {@link MacroCall} gives the syntax that a macro can read and write. Other implementations are not supported.
 */
public interface Syntax {

  /**
   * Gives the name, if this is a name, as {@code expire_time}.
   *
   * @return the name, or empty for any other syntax
   */
  Optional<String> name();

  /**
   * Gives the value and the member, if this is a member of a value, as {@code address.city}.
   *
   * @return the selection, or empty for any other syntax
   */
  Optional<Selection> selection();

  /**
   * A member of a value.
   *
   * @param operand the value
   * @param field the name of the member
   */
  record Selection(Syntax operand, String field) {
  }
}
