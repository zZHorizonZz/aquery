package dev.horizon.aquery.cel;

import dev.horizon.aquery.cel.Expression.BinaryOperation;
import dev.horizon.aquery.cel.Expression.Operator;
import dev.horizon.aquery.shaded.cel.common.ast.CelExpr;

/**
 * {@code + - * / %} on numbers, {@code +} on strings, a timestamp plus or minus a duration, and the duration between
 * two timestamps. A divisor must be a constant that is not zero, since the databases disagree on a division by zero:
 * some fail and some give NULL.
 */
final class ExpressionArithmetic implements ExpressionOperation {

  private final Operator operator;
  private final String symbol;

  /**
   * Makes the operation.
   *
   * @param operator the operator
   * @param symbol the symbol of the operator, for errors
   */
  ExpressionArithmetic(Operator operator, String symbol) {
    this.operator = operator;
    this.symbol = symbol;
  }

  @Override
  public Expression translate(CelExpr call, ExpressionTranslation translation) {
    CelTree tree = translation.tree();
    Type type = translation.type(call);
    Type left = translation.type(call.call().args().get(0));
    boolean supported = switch (type) {
      case Type.Scalar.INT, Type.Scalar.DOUBLE, Type.Scalar.TIMESTAMP, Type.Scalar.DURATION -> true;
      case Type.Scalar.STRING -> operator == Operator.ADD;
      default -> false;
    };
    if (!supported || left == Type.Scalar.BYTES) {
      throw tree.refuse(call, "'%s' on a %s is not supported in an expression", symbol, tree.describe(left));
    }
    if (operator == Operator.DIVIDE || operator == Operator.MODULO) {
      CelExpr divisor = call.call().args().get(1);
      boolean constant = divisor.getKind() == CelExpr.ExprKind.Kind.CONSTANT
          && switch (divisor.constant().getKind()) {
            case INT64_VALUE -> divisor.constant().int64Value() != 0;
            case DOUBLE_VALUE -> divisor.constant().doubleValue() != 0.0;
            default -> false;
          };
      if (!constant) {
        throw tree.refuse(divisor, "the divisor of '%s' must be a constant that is not zero", symbol);
      }
    }
    Expression leftOperand = translation.expression(call.call().args().get(0));
    Expression rightOperand = translation.expression(call.call().args().get(1));
    return new BinaryOperation(operator, leftOperand, rightOperand, type, translation.position(call));
  }
}
