package dev.horizon.aquery.cel.expression;

/**
 * The operations of a grammar below an {@link Operation}, which translate a call that the operation passes on. The
 * last of them refuses the call as not supported.
 */
@FunctionalInterface
public interface Next {

  /**
   * Translates a call with the operations below.
   *
   * @param call the call
   * @return the node of the call
   * @throws InvalidExpressionException if no operation below translates the call
   */
  Expression translate(Call call);
}
