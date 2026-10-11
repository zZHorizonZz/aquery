package dev.horizon.aquery.cel;

import static dev.horizon.aquery.aip160.Operator.EQUALS;
import static dev.horizon.aquery.aip160.Operator.HAS;

import dev.horizon.aquery.aip132.FieldPath;
import dev.horizon.aquery.aip160.Filter;
import dev.horizon.aquery.aip160.Filter.BoolLiteral;
import dev.horizon.aquery.aip160.Filter.Condition;
import dev.horizon.aquery.aip160.Filter.DoubleLiteral;
import dev.horizon.aquery.aip160.Filter.DurationLiteral;
import dev.horizon.aquery.aip160.Filter.IntLiteral;
import dev.horizon.aquery.aip160.Filter.Restriction;
import dev.horizon.aquery.aip160.Filter.StringLiteral;
import dev.horizon.aquery.aip160.Filter.Text;
import dev.horizon.aquery.aip160.Filter.TimestampLiteral;
import dev.horizon.aquery.aip160.Filter.Value;
import dev.horizon.aquery.shaded.cel.common.Operator;
import dev.horizon.aquery.shaded.cel.common.ast.CelConstant;
import dev.horizon.aquery.shaded.cel.common.ast.CelExpr;
import dev.horizon.aquery.shaded.cel.common.ast.CelExpr.CelCall;
import java.util.Map;

/**
 * The translation of one CEL filter into the {@link Filter} tree: its conditions, the fields they name, and the
 * constants they compare with.
 *
 * <p>
 * A field alone is a boolean field that must be true, as in {@code !locked} or {@code labels["on"]}, and
 * {@code has(labels.site)} tells if the field has the member or the key. Every other condition is a call, which the
 * operation of its function translates.
 */
final class FilterTranslation {

  private static final Text PRESENCE = new Text("*");

  private final CelTree tree;
  private final Map<String, FilterOperation> operations;

  FilterTranslation(CelTree tree, Map<String, FilterOperation> operations) {
    this.tree = tree;
    this.operations = operations;
  }

  /** Gives the filter of the tree. */
  Filter filter() {
    return new Filter(condition(tree.root()));
  }

  /** Gives the tree that the translation reads. */
  CelTree tree() {
    return tree;
  }

  /**
   * Gives the condition of a node.
   *
   * @throws Refusal if the node is not a condition that a filter supports
   */
  Condition condition(CelExpr node) {
    if (node.getKind() == CelExpr.ExprKind.Kind.SELECT && node.select().testOnly()) {
      return new Restriction(fieldPath(node), HAS, PRESENCE);
    }
    if (tree.isFieldPath(node)) {
      return new Restriction(fieldPath(node), EQUALS, new BoolLiteral(true));
    }
    if (node.getKind() != CelExpr.ExprKind.Kind.CALL) {
      throw tree.refuse(node, "expected a condition but found %s", tree.describe(node));
    }
    FilterOperation operation = operations.get(node.call().function());
    if (operation == null) {
      throw tree.refuse(node, "%s is not supported in a filter", tree.describe(node));
    }
    return operation.translate(node, this);
  }

  /** Tells if a node is a field. */
  boolean isField(CelExpr node) {
    return tree.isFieldPath(node);
  }

  /** Gives the path of a field, also of the field that {@code has()} tests. */
  FieldPath fieldPath(CelExpr node) {
    return new FieldPath(tree.fieldPath(node));
  }

  /**
   * Gives the value of a node that a condition compares with: a constant, or a name, which is an enum value.
   *
   * @throws Refusal if the node is not such a value
   */
  Value value(CelExpr node) {
    return switch (node.getKind()) {
      case CONSTANT -> constant(node);
      case IDENT, SELECT -> {
        if (!tree.isFieldPath(node)) {
          throw tree.refuse(node, "expected a constant but found %s", tree.describe(node));
        }
        // An identifier is an enum value. A dotted name gives a clear error from the backend.
        yield new Text(String.join(".", tree.fieldPath(node)));
      }
      case CALL -> callValue(node);
      case LIST -> throw tree.refuse(node, "a list is only allowed after 'in'");
      default -> throw tree.refuse(node, "expected a constant but found %s", tree.describe(node));
    };
  }

  private Value constant(CelExpr node) {
    CelConstant constant = node.constant();
    return switch (constant.getKind()) {
      case STRING_VALUE -> new StringLiteral(constant.stringValue(), false);
      case INT64_VALUE -> new IntLiteral(constant.int64Value());
      case UINT64_VALUE -> {
        long value = constant.uint64Value().longValue();
        if (value < 0) {
          throw tree.refuse(node, "the unsigned integer %s does not fit in 64 bits", constant.uint64Value());
        }
        yield new IntLiteral(value);
      }
      case DOUBLE_VALUE -> new DoubleLiteral(constant.doubleValue());
      case BOOLEAN_VALUE -> new BoolLiteral(constant.booleanValue());
      case NULL_VALUE -> throw tree.refuse(node, "null is not supported in a filter; use has() to test if a field is set");
      case BYTES_VALUE -> throw tree.refuse(node, "bytes are not supported in a filter");
      default -> throw tree.refuse(node, "the constant is not supported in a filter");
    };
  }

  private Value callValue(CelExpr node) {
    CelCall call = node.call();
    if (call.function().equals(Operator.NEGATE.getFunction())) {
      Value value = value(call.args().getFirst());
      return switch (value) {
        case IntLiteral integer -> {
          if (integer.value() == Long.MIN_VALUE) {
            throw tree.refuse(node, "the integer -(%d) does not fit in 64 bits", integer.value());
          }
          yield new IntLiteral(-integer.value());
        }
        case DoubleLiteral number -> new DoubleLiteral(-number.value());
        default -> throw tree.refuse(node, "expected a number after '-' but found %s", value.input());
      };
    }
    if (tree.isFieldPath(node)) {
      return new Text(String.join(".", tree.fieldPath(node)));
    }
    boolean conversion = call.target().isEmpty() && call.args().size() == 1;
    if (conversion && call.function().equals("duration")) {
      return new DurationLiteral(tree.duration(node));
    }
    if (conversion && call.function().equals("timestamp")) {
      return new TimestampLiteral(tree.timestamp(node));
    }
    throw tree.refuse(node, "expected a constant but found %s", tree.describe(node));
  }
}
