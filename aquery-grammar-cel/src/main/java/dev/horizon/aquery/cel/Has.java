package dev.horizon.aquery.cel;

import java.util.Optional;

/**
 * The macro {@code has()}: {@code has(expire_time)} tells if a field of the row has a value, and
 * {@code has(address.city)} if a value has a member. CEL's own {@code has()} takes only a member, so a field of the
 * row becomes a member of the row.
 */
public final class Has implements Macro {

  @Override
  public String function() {
    return "has";
  }

  @Override
  public int arguments() {
    return 1;
  }

  @Override
  public boolean member() {
    return false;
  }

  @Override
  public Optional<Syntax> expand(MacroCall call) {
    Syntax argument = call.argument(0);
    return Optional.of(argument.name().map(name -> call.presence(call.row(), name))
        .or(() -> argument.selection().map(member -> call.presence(member.operand(), member.field())))
        .orElseGet(() -> call.refuse("has() takes a field, as in has(expire_time) or has(address.city)")));
  }
}
