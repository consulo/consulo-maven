package org.jetbrains.idea.maven.dom.annotator;

import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.editor.annotation.Annotator;
import consulo.language.editor.annotation.AnnotatorFactory;
import consulo.xml.lang.xml.XMLLanguage;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author VISTALL
 * @since 2023-01-21
 */
@ExtensionImpl
public class MavenDomGutterAnnotatorFactory implements AnnotatorFactory {
    @Nullable
    @Override
    public Annotator createAnnotator() {
        return new MavenDomGutterAnnotator();
    }

    @Nonnull
    @Override
    public Language getLanguage() {
        return XMLLanguage.INSTANCE;
    }
}
