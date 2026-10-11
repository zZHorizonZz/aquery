package dev.horizon.aquery.cel;

import java.util.List;

/**
 * A function that an expression can call, as in {@code now()}, which is not part of CEL. The caller of
 * {@link CelExpressionCompiler} declares the functions that an expression can use; a call of one is a
 * {@link Expression.FunctionCall} with the same function, so that the caller writes it as it knows.
 *
 * <p>
 * Two functions with the same name, parameters and result are the same function: implement it as a record.
 */
public interface Function {

  /**
   * Gives the name of the function in the text of an expression.
   *
   * @return the name, for example {@code now}
   */
  String name();

  /**
   * Gives the types of the arguments of the function.
   *
   * @return the types, in the order of the arguments
   */
  List<Type> parameters();

  /**
   * Gives the type of the value of the function.
   *
   * @return the type
   */
  Type result();
}
