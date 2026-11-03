package dev.horizon.aquery.cel.expression;

import dev.horizon.aquery.cel.Has;
import dev.horizon.aquery.cel.Macro;
import dev.horizon.aquery.cel.Type;
import dev.horizon.aquery.cel.expression.Expression.Operator;
import dev.horizon.aquery.cel.expression.operation.Arithmetic;
import dev.horizon.aquery.cel.expression.operation.Comparison;
import dev.horizon.aquery.cel.expression.operation.Conditional;
import dev.horizon.aquery.cel.expression.operation.Division;
import dev.horizon.aquery.cel.expression.operation.Duration;
import dev.horizon.aquery.cel.expression.operation.Indexing;
import dev.horizon.aquery.cel.expression.operation.Logic;
import dev.horizon.aquery.cel.expression.operation.Membership;
import dev.horizon.aquery.cel.expression.operation.Negation;
import dev.horizon.aquery.cel.expression.operation.Negative;
import dev.horizon.aquery.cel.expression.operation.NullEquality;
import dev.horizon.aquery.cel.expression.operation.NullInequality;
import dev.horizon.aquery.cel.expression.operation.Refused;
import dev.horizon.aquery.cel.expression.operation.StringMatch;
import dev.horizon.aquery.cel.expression.operation.Timestamp;
import dev.horizon.aquery.cel.internal.CelTree;
import java.util.ArrayList;
import java.util.List;

/**
 * The CEL that an expression can be written in: the macros that rewrite its syntax, the operations that translate its
 * calls, and the decorator that rewrites the nodes that they give.
 *
 * <p>
 * The {@linkplain #standard() standard} grammar has {@code && || !}, the comparisons, {@code == null},
 * {@code + - * / %}, {@code -}, {@code in}, indexes and keys, {@code ? :}, {@code startsWith}, {@code endsWith},
 * {@code contains}, {@code timestamp()}, {@code duration()}, {@code has()}, {@code exists()} and {@code all()}. A
 * caller adds its own functions, operations, macros and decorators, and takes away the operations that it does not
 * want:
 *
 * <pre>{@code
 * CelGrammar grammar = CelGrammar.standard()
 *     .with(new Now())
 *     .without(Conditional.class);
 * CompiledExpression expression = grammar.compile("expire_time > now()", CelEnvironment.of(orders),
 *     Type.Scalar.BOOL);
 * }</pre>
 *
 * <p>
 * A grammar cannot change. It is safe for use by several threads at the same time.
 */
public final class CelGrammar {

  private final List<Operation> operations;
  private final List<Macro> macros;
  private final Decorator decorator;

  private CelGrammar(List<Operation> operations, List<Macro> macros, Decorator decorator) {
    this.operations = List.copyOf(operations);
    this.macros = List.copyOf(macros);
    this.decorator = decorator;
  }

  /**
   * Gives the grammar of every operation and macro that aquery has.
   *
   * @return the grammar
   */
  public static CelGrammar standard() {
    return new CelGrammar(List.of(
        new Logic("_&&_", Operator.AND),
        new Logic("_||_", Operator.OR),
        new Negation(),
        new NullEquality(),
        new NullInequality(),
        new Comparison("_==_", Operator.EQUALS),
        new Comparison("_!=_", Operator.NOT_EQUALS),
        new Comparison("_<_", Operator.LESS),
        new Comparison("_<=_", Operator.LESS_EQUALS),
        new Comparison("_>_", Operator.GREATER),
        new Comparison("_>=_", Operator.GREATER_EQUALS),
        new Arithmetic("_+_", Operator.ADD, "+", Arithmetic.SUMS),
        new Arithmetic("_-_", Operator.SUBTRACT, "-", Arithmetic.NUMBERS),
        new Arithmetic("_*_", Operator.MULTIPLY, "*", Arithmetic.NUMBERS),
        new Division("_/_", Operator.DIVIDE, "/"),
        new Division("_%_", Operator.MODULO, "%"),
        new Negative(),
        new Membership("@in"),
        new Membership("_in_"),
        new Indexing(),
        new Conditional(),
        new StringMatch("startsWith", Expression.StringMatch.Kind.STARTS_WITH),
        new StringMatch("endsWith", Expression.StringMatch.Kind.ENDS_WITH),
        new StringMatch("contains", Expression.StringMatch.Kind.CONTAINS),
        new Timestamp(),
        new Duration(),
        new Refused("matches", "matches() is not supported, because the databases have no common regular"
            + " expressions")),
        List.of(new Has()), Decorator.none());
  }

  /**
   * Gives this grammar with one more operation, which comes before the operations of its function in this grammar.
   *
   * @param operation the operation
   * @return the grammar
   * @throws IllegalArgumentException if an operation of this grammar has the same function and one of the same
   * overloads
   */
  public CelGrammar with(Operation operation) {
    for (Operation other : operations) {
      if (other.function().equals(operation.function())
          && other.overloads().stream().anyMatch(operation.overloads()::contains)) {
        throw new IllegalArgumentException("the grammar has an overload of " + operation.function() + " that "
            + operation + " declares again");
      }
    }
    List<Operation> all = new ArrayList<>();
    all.add(operation);
    all.addAll(operations);
    return new CelGrammar(all, macros, decorator);
  }

  /**
   * Gives this grammar with one more function, whose calls are {@link Expression.FunctionCall} nodes.
   *
   * @param function the function
   * @return the grammar
   * @throws IllegalArgumentException if the grammar has a function with the same name and overload
   */
  public CelGrammar with(Function function) {
    return with(new FunctionOperation(function));
  }

  /**
   * Gives this grammar with one more macro.
   *
   * @param macro the macro
   * @return the grammar
   */
  public CelGrammar with(Macro macro) {
    List<Macro> all = new ArrayList<>(macros);
    all.add(macro);
    return new CelGrammar(operations, all, decorator);
  }

  /**
   * Gives this grammar with one more decorator, which rewrites the nodes after the decorators of this grammar.
   *
   * @param decorator the decorator
   * @return the grammar
   */
  public CelGrammar with(Decorator decorator) {
    return new CelGrammar(operations, macros, this.decorator.then(decorator));
  }

  /**
   * Gives this grammar without the operations of one class. A call that only they translate is refused.
   *
   * @param kind the class, for example {@code Conditional.class}
   * @return the grammar
   */
  public CelGrammar without(Class<? extends Operation> kind) {
    return new CelGrammar(operations.stream().filter(operation -> !kind.isInstance(operation)).toList(), macros,
        decorator);
  }

  /**
   * Compiles an expression.
   *
   * @param text the text of the expression, for example {@code state == 'OPEN' && total_cents > params.min}
   * @param environment the names that the expression can read
   * @param result the type that the value of the expression must have, for example {@link Type.Scalar#BOOL} for a
   * condition
   * @return the expression
   * @throws InvalidExpressionException if the expression does not compile
   */
  public CompiledExpression compile(String text, CelEnvironment environment, Type result) {
    Checker checker = new Checker(this, environment);
    CelTree tree = checker.check(text);
    Translation translation = new Translation(tree, this, checker, new Namespace(environment));
    return new CompiledExpression(text, translation.expression(tree.root()), environment.parameters()).require(result);
  }

  List<Operation> operations() {
    return operations;
  }

  List<Macro> macros() {
    return macros;
  }

  Next chain(String function, Next last) {
    return new Chain(operations.stream().filter(operation -> operation.function().equals(function)).toList(), last);
  }

  Expression decorate(Expression node) {
    return decorator.decorate(node);
  }
}
