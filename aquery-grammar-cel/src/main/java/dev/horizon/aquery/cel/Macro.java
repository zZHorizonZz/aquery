package dev.horizon.aquery.cel;

import java.util.Optional;

/**
 * A call that the parser rewrites into other syntax before the type checker reads it, as {@link Has} rewrites
 * {@code has(field)}.
 *
 * <p>
 * The {@code with(Macro)} of a grammar adds a macro to it. A macro gives syntax that the operations of the grammar
 * then translate.
 */
public interface Macro {

  /**
   * Gives the name of the call that the macro rewrites.
   *
   * @return the name, for example {@code has}
   */
  String function();

  /**
   * Gives the number of arguments of the call, without the receiver of a member call.
   *
   * @return the number
   */
  int arguments();

  /**
   * Tells if the call is on a value, as {@code items.exists(...)} is.
   *
   * @return true for a member call
   */
  boolean member();

  /**
   * Rewrites a call.
   *
   * @param call the call
   * @return the syntax in place of the call, or empty to keep the call as it is
   */
  Optional<Syntax> expand(MacroCall call);
}
