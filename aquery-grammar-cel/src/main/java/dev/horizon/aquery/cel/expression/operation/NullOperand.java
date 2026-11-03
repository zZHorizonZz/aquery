package dev.horizon.aquery.cel.expression.operation;

import dev.horizon.aquery.cel.Type;
import dev.horizon.aquery.cel.expression.Call;
import dev.horizon.aquery.cel.expression.Expression;
import dev.horizon.aquery.cel.expression.Expression.Presence;
import dev.horizon.aquery.cel.expression.Overload;
import java.util.ArrayList;
import java.util.List;

record NullOperand(Call call) {

  static final List<Overload> OVERLOADS = overloads();

  private static List<Overload> overloads() {
    List<Overload> all = new ArrayList<>();
    for (Type.Scalar type : List.of(Type.Scalar.BOOL, Type.Scalar.INT, Type.Scalar.DOUBLE, Type.Scalar.STRING,
        Type.Scalar.BYTES)) {
      all.add(new Overload.Global(List.of(type, Type.NULL), Type.Scalar.BOOL));
      all.add(new Overload.Global(List.of(Type.NULL, type), Type.Scalar.BOOL));
    }
    return List.copyOf(all);
  }

  boolean comparesNull() {
    return call.isNull(0) || call.isNull(1);
  }

  Expression presence() {
    int index = call.isNull(0) ? 1 : 0;
    if (call.isNull(index)) {
      throw call.refuse("null compared with null is always true or always false; compare a field with null");
    }
    return call.argument(index).inside(operand -> {
      if (!operand.optional()) {
        throw call.refuse(index, "%s always has a value, so it is never null; compare with null only a field that"
            + " can be unset or a member that is optional", operand.describe());
      }
      return new Presence(operand, call.position());
    });
  }
}
