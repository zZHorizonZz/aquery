package dev.horizon.aquery.cel.expression;

import dev.horizon.aquery.cel.Type;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * A form of a call that the type checker accepts: the types of the arguments and the type of the value.
 *
 * <p>
 * An {@link Operation} names its overloads. The checker chooses the overload that the arguments of a call fit, and
 * {@link Call#overload()} tells which one it chose.
 */
public sealed interface Overload permits Overload.Global, Overload.Member {

  /**
   * Gives the types of the arguments, without the receiver of a member call.
   *
   * @return the types, in the order of the arguments
   */
  List<Type> parameters();

  /**
   * Gives the type of the value of the call.
   *
   * @return the type
   */
  Type result();

  /**
   * Gives the types of the operands of the call: the receiver of a member call, then the arguments.
   *
   * @return the types, in order
   */
  List<Type> operands();

  /**
   * A call by name, as in {@code coalesce(a, b)}.
   *
   * @param parameters the types of the arguments
   * @param result the type of the value
   */
  record Global(List<Type> parameters, Type result) implements Overload {

    public Global {
      parameters = List.copyOf(parameters);
      Objects.requireNonNull(result, "result");
    }

    @Override
    public List<Type> operands() {
      return parameters;
    }
  }

  /**
   * A call on a value, as in {@code name.lower()}.
   *
   * @param receiver the type of the value that the call is on
   * @param parameters the types of the arguments
   * @param result the type of the value
   */
  record Member(Type receiver, List<Type> parameters, Type result) implements Overload {

    public Member {
      Objects.requireNonNull(receiver, "receiver");
      parameters = List.copyOf(parameters);
      Objects.requireNonNull(result, "result");
    }

    @Override
    public List<Type> operands() {
      return Stream.concat(Stream.of(receiver), parameters.stream()).toList();
    }
  }
}
