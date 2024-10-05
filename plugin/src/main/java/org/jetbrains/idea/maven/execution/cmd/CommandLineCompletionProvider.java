package org.jetbrains.idea.maven.execution.cmd;

import consulo.language.editor.completion.CompletionResultSet;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.completion.lookup.LookupElementBuilder;
import consulo.language.editor.completion.lookup.TailType;
import consulo.language.editor.completion.lookup.TailTypeDecorator;
import consulo.language.editor.ui.awt.TextFieldCompletionProvider;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

import javax.annotation.Nonnull;

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
    public String getPrefix(@Nonnull String currentTextPrefix) {
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
    public void addCompletionVariants(@Nonnull String text, int offset, @Nonnull String prefix, @Nonnull CompletionResultSet result) {
        ParametersListLexer lexer = new ParametersListLexer(text);

        int argCount = 0;

        while (lexer.nextToken()) {
            if (offset < lexer.getTokenStart()) {
                break;
            }

            if (offset <= lexer.getTokenEnd()) {
                if (argCount == 0) {
                    if (prefix.startsWith("--")) {
                        for (Option option : myOptions.getOptions()) {
                            if (option.getLongOpt() != null) {
                                result.addElement(createLookupElement(option, "--" + option.getLongOpt()));
                            }
                        }
                    }
                    else if (prefix.startsWith("-")) {
                        for (Option option : myOptions.getOptions()) {
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
