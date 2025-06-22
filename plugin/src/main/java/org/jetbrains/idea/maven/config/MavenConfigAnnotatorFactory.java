package org.jetbrains.idea.maven.config;

import consulo.annotation.component.ExtensionImpl;
import consulo.application.dumb.DumbAware;
import consulo.language.Language;
import consulo.language.editor.annotation.Annotator;
import consulo.language.editor.annotation.AnnotatorFactory;
import consulo.language.plain.PlainTextLanguage;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author VISTALL
 * @since 2025-06-22
 */
@ExtensionImpl
public class MavenConfigAnnotatorFactory implements AnnotatorFactory, DumbAware {
    @Nullable
    @Override
    public Annotator createAnnotator() {
        return new MavenConfigAnnotator();
    }

    @Nonnull
    @Override
    public Language getLanguage() {
        return PlainTextLanguage.INSTANCE;
    }
}
