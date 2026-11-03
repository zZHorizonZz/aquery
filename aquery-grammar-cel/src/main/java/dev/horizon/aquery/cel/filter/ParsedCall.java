package dev.horizon.aquery.cel.filter;

import dev.horizon.aquery.aip160.InvalidFilterException;
import java.util.List;
import java.util.Optional;

record ParsedCall(Site site, Translation translation) implements Call {

  @Override
  public String function() {
    return site.node().call().function();
  }

  @Override
  public String symbol() {
    return site.tree().symbol(function());
  }

  @Override
  public Optional<Operand> target() {
    return site.node().call().target().map(target -> new ParsedOperand(site.at(target), translation));
  }

  @Override
  public Operand argument(int index) {
    return new ParsedOperand(site.at(site.node().call().args().get(index)), translation);
  }

  @Override
  public List<Operand> arguments() {
    return site.node().call().args().stream()
        .<Operand> map(argument -> new ParsedOperand(site.at(argument), translation)).toList();
  }

  @Override
  public InvalidFilterException refuse(String message, Object... arguments) {
    return site.refuse(message, arguments);
  }
}
