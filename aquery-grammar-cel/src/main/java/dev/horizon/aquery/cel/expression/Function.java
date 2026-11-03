package dev.horizon.aquery.cel.expression;

/**
 * A function that an expression can call, which is not part of CEL, as {@code now()} or {@code name.lower()}. A
 * call of it is a {@link Expression.FunctionCall} that holds the same function, so that the caller writes it as it
 * knows.
 *
 * <p>
 * {@link CelGrammar#with(Function)} adds a function to a grammar. Functions with one name and other overloads are
 * the overloads of one function in the text. Two functions with the same name and overload are the same function:
 * implement it as a record.
 */
public interface Function {

  /**
   * Gives the name of the function in the text of an expression.
   *
   * @return the name, for example {@code now}
   */
  String name();

  /**
   * Gives the types that the function takes and gives.
   *
   * @return the overload. A {@link Overload.Member} function is called on a value, and its call has that value as
   * its first argument.
   */
  Overload overload();
}
