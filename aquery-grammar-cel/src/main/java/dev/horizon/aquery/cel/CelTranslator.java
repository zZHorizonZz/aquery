package dev.horizon.aquery.cel;

import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.ast.CelConstant;
import dev.cel.common.ast.CelExpr;
import dev.cel.common.ast.CelExpr.CelCall;
import dev.horizon.aquery.aip132.FieldPath;
import dev.horizon.aquery.aip160.Args;
import dev.horizon.aquery.aip160.Filter;
import dev.horizon.aquery.aip160.Filter.And;
import dev.horizon.aquery.aip160.Filter.BoolLiteral;
import dev.horizon.aquery.aip160.Filter.Condition;
import dev.horizon.aquery.aip160.Filter.DoubleLiteral;
import dev.horizon.aquery.aip160.Filter.DurationLiteral;
import dev.horizon.aquery.aip160.Filter.IntLiteral;
import dev.horizon.aquery.aip160.Filter.Not;
import dev.horizon.aquery.aip160.Filter.Or;
import dev.horizon.aquery.aip160.Filter.Restriction;
import dev.horizon.aquery.aip160.Filter.StringLiteral;
import dev.horizon.aquery.aip160.Filter.Text;
import dev.horizon.aquery.aip160.Filter.TimestampLiteral;
import dev.horizon.aquery.aip160.Filter.Value;
import dev.horizon.aquery.aip160.FilterTranslator;
import dev.horizon.aquery.aip160.InvalidFilterException;
import dev.horizon.aquery.aip160.Operator;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Changes a CEL syntax tree into the {@link Filter} tree of the api.
 *
 * <p>
 * {@link CelFilterParser} gives the supported part of CEL. The translator refuses all other expressions with an
 * {@link InvalidFilterException}. The position of the error is the offset of the expression in the text.
 *
 * <p>
 * The translator has no state. A refused expression throws a {@link Refusal} with the id of the expression.
 * {@link #translate} then finds the offset of the expression in the source of the tree.
 */
final class CelTranslator implements FilterTranslator<CelAbstractSyntaxTree> {

  private static final Pattern DURATION_PART = Pattern.compile("([0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(ns|us|µs|ms|s|m|h)");

  private static final Text PRESENCE = new Text("*");

  @Override
  public Filter translate(CelAbstractSyntaxTree tree) {
    try {
      return new Filter(condition(tree.getExpr()));
    } catch (Refusal refusal) {
      throw new InvalidFilterException(new CelSourceText(tree.getSource()).offsetOf(refusal.exprId()), "%s",
          refusal.getMessage());
    }
  }

  private Condition condition(CelExpr expr) {
    return switch (expr.getKind()) {
      case CALL -> callCondition(expr);
      case SELECT -> {
        if (expr.select().testOnly()) {
          yield new Restriction(fieldPath(expr), Operator.HAS, PRESENCE);
        }
        yield new Restriction(fieldPath(expr), Operator.EQUALS, new BoolLiteral(true));
      }
      case IDENT -> new Restriction(fieldPath(expr), Operator.EQUALS, new BoolLiteral(true));
      default -> throw error(expr, "expected a condition but found %s", describe(expr));
    };
  }

  private Condition callCondition(CelExpr expr) {
    CelCall call = expr.call();
    Optional<dev.cel.common.Operator> operator = dev.cel.common.Operator.findReverse(call.function());
    if (operator.isPresent()) {
      return switch (operator.get()) {
        case LOGICAL_AND -> new And(operands(call, dev.cel.common.Operator.LOGICAL_AND));
        case LOGICAL_OR -> new Or(operands(call, dev.cel.common.Operator.LOGICAL_OR));
        case LOGICAL_NOT -> new Not(condition(call.args().getFirst()));
        case EQUALS -> comparison(expr, Operator.EQUALS, Operator.EQUALS);
        case NOT_EQUALS -> comparison(expr, Operator.NOT_EQUALS, Operator.NOT_EQUALS);
        case LESS -> comparison(expr, Operator.LESS_THAN, Operator.GREATER_THAN);
        case LESS_EQUALS -> comparison(expr, Operator.LESS_EQUALS, Operator.GREATER_EQUALS);
        case GREATER -> comparison(expr, Operator.GREATER_THAN, Operator.LESS_THAN);
        case GREATER_EQUALS -> comparison(expr, Operator.GREATER_EQUALS, Operator.LESS_EQUALS);
        case IN, OLD_IN -> in(expr);
        case INDEX -> new Restriction(fieldPath(expr), Operator.EQUALS, new BoolLiteral(true));
        case CONDITIONAL -> throw error(expr, "the conditional operator '? :' is not supported in a filter");
        default -> throw error(expr, "%s is not supported in a filter", describe(expr));
      };
    }
    return switch (call.function()) {
      case "contains" -> memberFunction(expr, Operator.HAS);
      case "startsWith" -> memberFunction(expr, Operator.STARTS_WITH);
      case "endsWith" -> memberFunction(expr, Operator.ENDS_WITH);
      case "matches" ->
        throw error(expr, "matches() is not supported in a filter, because ISO SQL has no portable regular expressions");
      default -> throw error(expr, "%s is not supported in a filter", describe(expr));
    };
  }

  private List<Condition> operands(CelCall call, dev.cel.common.Operator operator) {
    List<Condition> operands = new ArrayList<>();
    for (CelExpr arg : call.args()) {
      Condition operand = condition(arg);
      // cel-java balances a chain of && as a tree. One flat list gives shorter SQL with the same meaning.
      boolean same = arg.getKind() == CelExpr.ExprKind.Kind.CALL && arg.call().function().equals(operator.getFunction());
      if (same && operand instanceof And and) {
        operands.addAll(and.operands());
      } else if (same && operand instanceof Or or) {
        operands.addAll(or.operands());
      } else {
        operands.add(operand);
      }
    }
    return operands;
  }

  private Condition comparison(CelExpr expr, Operator operator, Operator flipped) {
    CelExpr left = expr.call().args().get(0);
    CelExpr right = expr.call().args().get(1);
    if (isField(left)) {
      return new Restriction(fieldPath(left), operator, value(right));
    }
    if (isField(right)) {
      return new Restriction(fieldPath(right), flipped, value(left));
    }
    throw error(expr, "expected a field on one side of '%s' but found %s and %s", display(expr.call().function()),
        describe(left), describe(right));
  }

  private Condition in(CelExpr expr) {
    CelExpr element = expr.call().args().get(0);
    CelExpr collection = expr.call().args().get(1);
    if (collection.getKind() == CelExpr.ExprKind.Kind.LIST) {
      if (!isField(element)) {
        throw error(element, "expected a field before 'in' a list but found %s", describe(element));
      }
      List<CelExpr> elements = collection.list().elements();
      if (elements.isEmpty()) {
        throw error(collection, "the list after 'in' is empty");
      }
      FieldPath fieldPath = fieldPath(element);
      List<Condition> equalities = new ArrayList<>(elements.size());
      for (CelExpr value : elements) {
        equalities.add(new Restriction(fieldPath, Operator.EQUALS, value(value)));
      }
      return equalities.size() == 1 ? equalities.getFirst() : new Or(equalities);
    }
    if (isField(collection)) {
      return new Restriction(fieldPath(collection), Operator.IN, value(element));
    }
    throw error(collection, "expected a field or a list after 'in' but found %s", describe(collection));
  }

  private Condition memberFunction(CelExpr expr, Operator operator) {
    CelCall call = expr.call();
    if (call.target().isEmpty() || call.args().size() != 1) {
      throw error(expr, "expected %s() on a field with one argument, as in name.%s(\"x\")", call.function(), call.function());
    }
    CelExpr target = call.target().get();
    if (!isField(target)) {
      throw error(target, "expected a field before .%s() but found %s", call.function(), describe(target));
    }
    return new Restriction(fieldPath(target), operator, value(call.args().getFirst()));
  }

  private boolean isField(CelExpr expr) {
    return switch (expr.getKind()) {
      case IDENT -> true;
      case SELECT -> !expr.select().testOnly() && isField(expr.select().operand());
      case CALL -> isKey(expr);
      default -> false;
    };
  }

  private boolean isKey(CelExpr expr) {
    CelCall call = expr.call();
    if (!call.function().equals(dev.cel.common.Operator.INDEX.getFunction()) || call.args().size() != 2) {
      return false;
    }
    CelExpr key = call.args().get(1);
    return isField(call.args().get(0)) && key.getKind() == CelExpr.ExprKind.Kind.CONSTANT
        && key.constant().getKind() == CelConstant.Kind.STRING_VALUE;
  }

  private FieldPath fieldPath(CelExpr expr) {
    List<String> segments = new ArrayList<>();
    segments(expr, segments);
    return new FieldPath(segments);
  }

  private void segments(CelExpr expr, List<String> segments) {
    switch (expr.getKind()) {
      case IDENT -> segments.add(expr.ident().name());
      case SELECT -> {
        segments(expr.select().operand(), segments);
        segments.add(expr.select().field());
      }
      case CALL -> {
        if (!isKey(expr)) {
          throw error(expr, "expected a field but found %s", describe(expr));
        }
        segments(expr.call().args().get(0), segments);
        segments.add(expr.call().args().get(1).constant().stringValue());
      }
      default -> throw error(expr, "expected a field but found %s", describe(expr));
    }
  }

  private Value value(CelExpr expr) {
    return switch (expr.getKind()) {
      case CONSTANT -> constant(expr);
      case IDENT, SELECT -> {
        if (!isField(expr)) {
          throw error(expr, "expected a constant but found %s", describe(expr));
        }
        // An identifier is an enum value. A dotted name gives a clear error from the backend.
        yield new Text(String.join(".", fieldPath(expr).segments()));
      }
      case CALL -> callValue(expr);
      case LIST -> throw error(expr, "a list is only allowed after 'in'");
      default -> throw error(expr, "expected a constant but found %s", describe(expr));
    };
  }

  private Value constant(CelExpr expr) {
    CelConstant constant = expr.constant();
    return switch (constant.getKind()) {
      case STRING_VALUE -> new StringLiteral(constant.stringValue(), false);
      case INT64_VALUE -> new IntLiteral(constant.int64Value());
      case UINT64_VALUE -> {
        long value = constant.uint64Value().longValue();
        if (value < 0) {
          throw error(expr, "the unsigned integer %s does not fit in 64 bits", constant.uint64Value());
        }
        yield new IntLiteral(value);
      }
      case DOUBLE_VALUE -> new DoubleLiteral(constant.doubleValue());
      case BOOLEAN_VALUE -> new BoolLiteral(constant.booleanValue());
      case NULL_VALUE -> throw error(expr, "null is not supported in a filter; use has() to test if a field is set");
      case BYTES_VALUE -> throw error(expr, "bytes are not supported in a filter");
      default -> throw error(expr, "the constant is not supported in a filter");
    };
  }

  private Value callValue(CelExpr expr) {
    CelCall call = expr.call();
    if (call.function().equals(dev.cel.common.Operator.NEGATE.getFunction())) {
      CelExpr operand = call.args().getFirst();
      Value value = value(operand);
      return switch (value) {
        case IntLiteral integer -> {
          if (integer.value() == Long.MIN_VALUE) {
            throw error(expr, "the integer -(%d) does not fit in 64 bits", integer.value());
          }
          yield new IntLiteral(-integer.value());
        }
        case DoubleLiteral number -> new DoubleLiteral(-number.value());
        default -> throw error(expr, "expected a number after '-' but found %s", value.input());
      };
    }
    if (isKey(expr)) {
      return new Text(String.join(".", fieldPath(expr).segments()));
    }
    boolean conversion = call.target().isEmpty() && call.args().size() == 1;
    if (conversion && (call.function().equals("duration") || call.function().equals("timestamp"))) {
      CelExpr argument = call.args().getFirst();
      if (argument.getKind() != CelExpr.ExprKind.Kind.CONSTANT
          || argument.constant().getKind() != CelConstant.Kind.STRING_VALUE) {
        throw error(argument, "expected a string constant in %s()", call.function());
      }
      String written = argument.constant().stringValue();
      try {
        return call.function().equals("duration") ? new DurationLiteral(duration(written))
            : new TimestampLiteral(Args.parseTimestamp(written));
      } catch (InvalidFilterException refused) {
        throw error(argument, "%s", refused.getMessage());
      }
    }
    throw error(expr, "expected a constant but found %s", describe(expr));
  }

  private Duration duration(String written) {
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
      // A duration is a sign and decimal numbers with the units h, m, s, ms, us or ns, as in -1h30m.
      throw new InvalidFilterException("'%s' is not a valid duration, expected e.g. \"1.5s\" or \"1h30m\"", written);
    }
    try {
      long exact = nanos.toBigInteger().longValueExact();
      return Duration.ofNanos(negative ? -exact : exact);
    } catch (ArithmeticException tooLong) {
      throw new InvalidFilterException("'%s' is too long a duration, the nanoseconds must fit in 64 bits", written);
    }
  }

  private long unitNanos(String unit) {
    return switch (unit) {
      case "h" -> 3_600_000_000_000L;
      case "m" -> 60_000_000_000L;
      case "s" -> 1_000_000_000L;
      case "ms" -> 1_000_000L;
      case "us", "µs" -> 1_000L;
      default -> 1L;
    };
  }

  private String display(String function) {
    return dev.cel.common.Operator.lookupBinaryOperator(function)
        .or(() -> dev.cel.common.Operator.lookupUnaryOperator(function))
        .orElse(function);
  }

  private String describe(CelExpr expr) {
    return switch (expr.getKind()) {
      case CONSTANT -> "a constant";
      case IDENT -> "the identifier '" + expr.ident().name() + "'";
      case SELECT -> expr.select().testOnly() ? "has()" : "the field '" + expr.select().field() + "'";
      case CALL -> {
        String function = expr.call().function();
        String symbol = display(function);
        yield symbol.equals(function) ? "the function '" + function + "'" : "the operator '" + symbol + "'";
      }
      case LIST -> "a list";
      case MAP -> "a map";
      case STRUCT -> "a message";
      case COMPREHENSION -> "a comprehension";
      default -> "an empty expression";
    };
  }

  private Refusal error(CelExpr expr, String message, Object... arguments) {
    return new Refusal(expr.id(), arguments.length == 0 ? message : message.formatted(arguments));
  }
}
