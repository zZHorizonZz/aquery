package dev.horizon.aquery.cel;

import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelIssue;
import dev.cel.common.CelOptions;
import dev.cel.common.CelValidationException;
import dev.cel.common.CelValidationResult;
import dev.cel.parser.CelParser;
import dev.cel.parser.CelParserFactory;
import dev.cel.parser.CelStandardMacro;
import dev.horizon.aquery.aip160.Filter;
import dev.horizon.aquery.aip160.InvalidFilterException;
import dev.horizon.aquery.aip160.SyntaxParser;

/**
 * Parses CEL text into the syntax tree of cel-java.
 *
 * <p>
 * The parser knows only the {@code has()} macro. Thus {@code exists()} and the other macros are usual function
 * calls, and {@link CelTranslator} refuses them. The text can have at most {@code Filter.MAX_LENGTH} code points.
 */
final class CelSyntaxParser implements SyntaxParser<CelAbstractSyntaxTree> {

  private final CelParser parser = CelParserFactory.standardCelParserBuilder()
      .setOptions(CelOptions.current().maxExpressionCodePointSize(Filter.MAX_LENGTH).build())
      .setStandardMacros(CelStandardMacro.HAS)
      .build();

  @Override
  public CelAbstractSyntaxTree parse(String filter) {
    CelValidationResult result = parser.parse(filter);
    if (result.hasError()) {
      CelIssue issue = result.getErrors().getFirst();
      throw new InvalidFilterException(new CelSourceText(result.getSource()).offsetOf(issue.getSourceLocation()),
          "invalid CEL expression: %s", issue.getMessage());
    }
    try {
      return result.getAst();
    } catch (CelValidationException unexpected) {
      throw new IllegalStateException("cel-java gave no tree for an expression without errors", unexpected);
    }
  }
}
