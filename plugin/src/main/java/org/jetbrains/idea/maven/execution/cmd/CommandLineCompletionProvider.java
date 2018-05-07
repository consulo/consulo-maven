package org.jetbrains.idea.maven.execution.cmd;

import java.util.Collection;

import javax.annotation.Nonnull;

import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.TailTypeDecorator;
import com.intellij.util.TextFieldCompletionProvider;

/**
 * @author Sergey Evdokimov
 */
public abstract class CommandLineCompletionProvider extends TextFieldCompletionProvider {

  private final Options myOptions;

  public CommandLineCompletionProvider(Options options) {
    super(true);

    myOptions = options;
  }

  @Nonnull
  @Override
  protected String getPrefix(@Nonnull String currentTextPrefix) {
    ParametersListLexer lexer = new ParametersListLexer(currentTextPrefix);
    while (lexer.nextToken()) {
      if (lexer.getTokenEnd() == currentTextPrefix.length()) {
        return lexer.getCurrentToken();
      }
    }

    return "";
  }

  protected LookupElement createLookupElement(@Nonnull Option option, @Nonnull String text) {
    LookupElementBuilder res = LookupElementBuilder.create(text);

    if (option.getDescription() != null) {
      return TailTypeDecorator.withTail(res.withTypeText(option.getDescription(), true), TailType.INSERT_SPACE);
    }

    return res;
  }

  protected abstract void addArgumentVariants(@Nonnull CompletionResultSet result);

  @Override
  protected void addCompletionVariants(@Nonnull String text, int offset, @Nonnull String prefix, @Nonnull CompletionResultSet result) {
    ParametersListLexer lexer = new ParametersListLexer(text);

    int argCount = 0;

    while (lexer.nextToken()) {
      if (offset < lexer.getTokenStart()) {
        break;
      }

      if (offset <= lexer.getTokenEnd()) {
        if (argCount == 0) {
          if (prefix.startsWith("--")) {
            for (Option option : (Collection<Option>)myOptions.getOptions()) {
              if (option.getLongOpt() != null) {
                result.addElement(createLookupElement(option, "--" + option.getLongOpt()));
              }
            }
          }
          else if (prefix.startsWith("-")) {
            for (Option option : (Collection<Option>)myOptions.getOptions()) {
              if (option.getOpt() != null) {
                result.addElement(createLookupElement(option, "-" + option.getOpt()));
              }
            }
          }
          else {
            addArgumentVariants(result);
          }
        }

        return;
      }

      if (argCount > 0) {
        argCount--;
      }
      else {
        String token = lexer.getCurrentToken();

        if (token.startsWith("-")) {
          Option option = myOptions.getOption(token);
          if (option != null) {
            int optionArgCount = option.getArgs();

            if (optionArgCount == Option.UNLIMITED_VALUES) {
              argCount = Integer.MAX_VALUE;
            }
            else if (optionArgCount != Option.UNINITIALIZED) {
              argCount = optionArgCount;
            }
          }
        }
      }
    }

    if (argCount > 0) {
      return;
    }

    addArgumentVariants(result);
  }

}
