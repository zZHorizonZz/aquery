package dev.horizon.aquery.cel;

import dev.horizon.aquery.aip160.Args;
import dev.horizon.aquery.aip160.InvalidFilterException;
import dev.horizon.aquery.shaded.cel.common.CelAbstractSyntaxTree;
import dev.horizon.aquery.shaded.cel.common.Operator;
import dev.horizon.aquery.shaded.cel.common.ast.CelConstant;
import dev.horizon.aquery.shaded.cel.common.ast.CelExpr;
import dev.horizon.aquery.shaded.cel.common.ast.CelExpr.CelCall;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The syntax tree that cel-java gave for one text, as the translations of aquery read it.
 *
 * <p>
 * The tree holds what the translation of a filter and the translation of an expression read in the same way: where a
 * node is in the text, the path of a field, the constants that are function calls such as {@code duration("1.5s")},
 * how to name a node in an error, and the type of a node if the type checker of cel-java checked the text.
 */
final class CelTree {

  private static final Pattern DURATION_PART = Pattern.compile("([0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(ns|us|µs|ms|s|m|h)");

  private final CelAbstractSyntaxTree tree;
  private final CelSourceText source;
  private final CelTypes types;

  /**
   * Reads a tree.
   *
   * @param tree the tree of cel-java
   * @param types the types of the compilation, or null if the text was parsed but not checked
   */
  CelTree(CelAbstractSyntaxTree tree, CelTypes types) {
    this.tree = tree;
    this.source = new CelSourceText(tree.getSource());
    this.types = types;
  }

  /** Gives the root of the tree. */
  CelExpr root() {
    return tree.getExpr();
  }

  /** Gives the offset of a node in the text, in characters, or -1 if it is not known. */
  int position(CelExpr node) {
    return source.offsetOf(node.id());
  }

  /** Gives the offset in the text of the node that a refusal names. */
  int position(Refusal refusal) {
    return source.offsetOf(refusal.nodeId());
  }

  /** Makes the error of a node that a translation does not support. */
  Refusal refuse(CelExpr node, String message, Object... arguments) {
    return new Refusal(node.id(), arguments.length == 0 ? message : message.formatted(arguments));
  }

  /** Gives the type that the checker found for a node, or {@link Type#ANY} if the text was not checked. */
  Type type(CelExpr node) {
    return types == null ? Type.ANY : tree.getType(node.id()).map(types::aquery).orElse(Type.ANY);
  }

  /** Names a type in an error message. */
  String describe(Type type) {
    return types == null ? type.toString() : types.describe(type);
  }

  /**
   * Tells if a node is the path of a field: a name, a member of a field, or a key of a field that is a string
   * constant, as in {@code labels["site-1"]}.
   */
  boolean isFieldPath(CelExpr node) {
    return switch (node.getKind()) {
      case IDENT -> true;
      case SELECT -> !node.select().testOnly() && isFieldPath(node.select().operand());
      case CALL -> isKey(node);
      default -> false;
    };
  }

  /**
   * Gives the segments of the path of a field.
   *
   * @throws Refusal if the node is not the path of a field
   */
  List<String> fieldPath(CelExpr node) {
    List<String> segments = new ArrayList<>();
    segments(node, segments);
    return segments;
  }

  private void segments(CelExpr node, List<String> segments) {
    switch (node.getKind()) {
      case IDENT -> segments.add(node.ident().name());
      case SELECT -> {
        segments(node.select().operand(), segments);
        segments.add(node.select().field());
      }
      case CALL -> {
        if (!isKey(node)) {
          throw refuse(node, "expected a field but found %s", describe(node));
        }
        segments(node.call().args().get(0), segments);
        segments.add(node.call().args().get(1).constant().stringValue());
      }
      default -> throw refuse(node, "expected a field but found %s", describe(node));
    }
  }

  private boolean isKey(CelExpr node) {
    CelCall call = node.call();
    if (!call.function().equals(Operator.INDEX.getFunction()) || call.args().size() != 2) {
      return false;
    }
    CelExpr key = call.args().get(1);
    return isFieldPath(call.args().get(0)) && key.getKind() == CelExpr.ExprKind.Kind.CONSTANT
        && key.constant().getKind() == CelConstant.Kind.STRING_VALUE;
  }

  /**
   * Reads the duration of {@code duration("1.5s")}: a sign and decimal numbers with the units h, m, s, ms, us or ns.
   *
   * @param call the call of {@code duration}
   * @throws Refusal if the argument is not a string constant that is a duration
   */
  Duration duration(CelExpr call) {
    CelExpr argument = conversionArgument(call);
    String written = argument.constant().stringValue();
    String rest = written;
    boolean negative = rest.startsWith("-");
    if (negative || rest.startsWith("+")) {
      rest = rest.substring(1);
    }
    if (rest.equals("0")) {
      return Duration.ZERO;
    }
    Matcher part = DURATION_PART.matcher(rest);
    BigDecimal nanos = BigDecimal.ZERO;
    int at = 0;
    while (at < rest.length() && part.region(at, rest.length()).lookingAt()) {
      nanos = nanos.add(new BigDecimal(part.group(1)).multiply(BigDecimal.valueOf(unitNanos(part.group(2)))));
      at = part.end();
    }
    if (rest.isEmpty() || at != rest.length()) {
      throw refuse(argument, "'%s' is not a valid duration, expected e.g. \"1.5s\" or \"1h30m\"", written);
    }
    try {
      long exact = nanos.toBigInteger().longValueExact();
      return Duration.ofNanos(negative ? -exact : exact);
    } catch (ArithmeticException tooLong) {
      throw refuse(argument, "'%s' is too long a duration, the nanoseconds must fit in 64 bits", written);
    }
  }

  private static long unitNanos(String unit) {
    return switch (unit) {
      case "h" -> 3_600_000_000_000L;
      case "m" -> 60_000_000_000L;
      case "s" -> 1_000_000_000L;
      case "ms" -> 1_000_000L;
      case "us", "µs" -> 1_000L;
      default -> 1L;
    };
  }

  /**
   * Reads the time of {@code timestamp("2012-04-21T11:30:00Z")}.
   *
   * @param call the call of {@code timestamp}
   * @throws Refusal if the argument is not a string constant that is a time
   */
  OffsetDateTime timestamp(CelExpr call) {
    CelExpr argument = conversionArgument(call);
    try {
      return Args.parseTimestamp(argument.constant().stringValue());
    } catch (InvalidFilterException refused) {
      throw refuse(argument, "%s", refused.getMessage());
    }
  }

  private CelExpr conversionArgument(CelExpr node) {
    CelCall call = node.call();
    CelExpr argument = call.args().isEmpty() ? node : call.args().getFirst();
    if (call.target().isPresent() || call.args().size() != 1 || argument.getKind() != CelExpr.ExprKind.Kind.CONSTANT
        || argument.constant().getKind() != CelConstant.Kind.STRING_VALUE) {
      throw refuse(argument, "expected a string constant in %s(), as in %s(\"...\")", call.function(),
          call.function());
    }
    return argument;
  }

  /** Gives the symbol of an operator, as in {@code ==}, or the name of a function as it is. */
  String symbol(String function) {
    return Operator.lookupBinaryOperator(function)
        .or(() -> Operator.lookupUnaryOperator(function))
        .orElse(function);
  }

  /** Tells if a function is an operator of CEL, such as {@code _==_}, not a function with a name. */
  boolean isOperator(String function) {
    return Operator.findReverse(function).isPresent();
  }

  /** Names a node in an error message. */
  String describe(CelExpr node) {
    return switch (node.getKind()) {
      case CONSTANT -> "a constant";
      case IDENT -> "the identifier '" + node.ident().name() + "'";
      case SELECT -> node.select().testOnly() ? "has()" : "the field '" + node.select().field() + "'";
      case CALL -> {
        String function = node.call().function();
        String symbol = symbol(function);
        yield symbol.equals(function) ? "the function '" + function + "'" : "the operator '" + symbol + "'";
      }
      case LIST -> "a list";
      case MAP -> "a map";
      case STRUCT -> "a message";
      case COMPREHENSION -> "a comprehension";
      default -> "an empty expression";
    };
  }
}
