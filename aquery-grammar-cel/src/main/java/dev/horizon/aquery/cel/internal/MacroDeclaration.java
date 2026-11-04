package dev.horizon.aquery.cel.internal;

import dev.horizon.aquery.cel.Macro;
import dev.horizon.aquery.shaded.cel.parser.CelMacro;
import dev.horizon.aquery.shaded.cel.parser.CelMacroExpander;

public record MacroDeclaration(Macro macro) {

  public CelMacro cel() {
    CelMacroExpander expander = (factory, target, arguments) -> macro
        .expand(new FactoryMacroCall(factory, target, arguments)).map(CelSyntax::node);
    return macro.member() ? CelMacro.newReceiverMacro(macro.function(), macro.arguments(), expander)
        : CelMacro.newGlobalMacro(macro.function(), macro.arguments(), expander);
  }
}
