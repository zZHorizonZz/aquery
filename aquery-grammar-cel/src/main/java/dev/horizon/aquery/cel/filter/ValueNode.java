package dev.horizon.aquery.cel.filter;

import dev.horizon.aquery.aip160.Args;
import dev.horizon.aquery.aip160.Filter.BoolLiteral;
import dev.horizon.aquery.aip160.Filter.DoubleLiteral;
import dev.horizon.aquery.aip160.Filter.DurationLiteral;
import dev.horizon.aquery.aip160.Filter.IntLiteral;
import dev.horizon.aquery.aip160.Filter.StringLiteral;
import dev.horizon.aquery.aip160.Filter.Text;
import dev.horizon.aquery.aip160.Filter.TimestampLiteral;
import dev.horizon.aquery.aip160.Filter.Value;
import dev.horizon.aquery.aip160.InvalidFilterException;
import dev.horizon.aquery.cel.internal.DurationText;
import dev.horizon.aquery.shaded.cel.common.ast.CelConstant;
import dev.horizon.aquery.shaded.cel.common.ast.CelExpr;
import dev.horizon.aquery.shaded.cel.common.ast.CelExpr.CelCall;
import java.time.Duration;
import java.time.OffsetDateTime;

record ValueNode(Site site) {

  Value value() {
    CelExpr node = site.node();
    return switch (node.getKind()) {
      case CONSTANT -> constant(node.constant());
      case IDENT, SELECT -> text();
      case CALL -> call(node.call());
      case LIST -> throw site.refuse("a list is only allowed after 'in'");
      default -> throw site.refuse("expected a constant but found %s", site.describe());
    };
  }

  private Value constant(CelConstant constant) {
    return switch (constant.getKind()) {
      case STRING_VALUE -> new StringLiteral(constant.stringValue(), false);
      case INT64_VALUE -> new IntLiteral(constant.int64Value());
      case UINT64_VALUE -> unsigned(constant.uint64Value().longValue());
      case DOUBLE_VALUE -> new DoubleLiteral(constant.doubleValue());
      case BOOLEAN_VALUE -> new BoolLiteral(constant.booleanValue());
      case NULL_VALUE -> throw site.refuse("null is not supported in a filter; use has() to test if a field is set");
      case BYTES_VALUE -> throw site.refuse("bytes are not supported in a filter");
      default -> throw site.refuse("the constant is not supported in a filter");
    };
  }

  private Value unsigned(long value) {
    if (value < 0) {
      throw site.refuse("the unsigned integer %s does not fit in 64 bits", Long.toUnsignedString(value));
    }
    return new IntLiteral(value);
  }

  private Value text() {
    FieldPathNode path = new FieldPathNode(site.node());
    if (!path.valid()) {
      throw site.refuse("expected a constant but found %s", site.describe());
    }
    return new Text(String.join(".", path.segments()));
  }

  private Value call(CelCall call) {
    if (call.function().equals("-_")) {
      return negative(new ValueNode(site.at(call.args().getFirst())).value());
    }
    if (new FieldPathNode(site.node()).valid()) {
      return text();
    }
    boolean conversion = call.target().isEmpty() && call.args().size() == 1;
    if (conversion && call.function().equals("duration")) {
      return new DurationLiteral(duration(call));
    }
    if (conversion && call.function().equals("timestamp")) {
      return new TimestampLiteral(timestamp(call));
    }
    throw site.refuse("expected a constant but found %s", site.describe());
  }

  private Value negative(Value value) {
    return switch (value) {
      case IntLiteral(long number) when number != Long.MIN_VALUE -> new IntLiteral(-number);
      case IntLiteral(long number) -> throw site.refuse("the integer -(%d) does not fit in 64 bits", number);
      case DoubleLiteral(double number) -> new DoubleLiteral(-number);
      default -> throw site.refuse("expected a number after '-' but found %s", value.input());
    };
  }

  private Duration duration(CelCall call) {
    try {
      return new DurationText(argument(call)).duration();
    } catch (IllegalArgumentException invalid) {
      throw site.at(call.args().getFirst()).refuse("%s", invalid.getMessage());
    }
  }

  private OffsetDateTime timestamp(CelCall call) {
    try {
      return Args.parseTimestamp(argument(call));
    } catch (InvalidFilterException invalid) {
      throw site.at(call.args().getFirst()).refuse("%s", invalid.getMessage());
    }
  }

  private String argument(CelCall call) {
    CelExpr argument = call.args().getFirst();
    if (argument.getKind() != CelExpr.ExprKind.Kind.CONSTANT
        || argument.constant().getKind() != CelConstant.Kind.STRING_VALUE) {
      throw site.at(argument).refuse("expected a string constant in %s(), as in %s(\"...\")", call.function(),
          call.function());
    }
    return argument.constant().stringValue();
  }
}
