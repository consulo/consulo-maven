package org.jetbrains.idea.maven.config;

import consulo.language.file.LanguageFileType;
import consulo.language.plain.PlainTextLanguage;
import consulo.localize.LocalizeValue;
import consulo.maven.icon.MavenIconGroup;
import consulo.ui.image.Image;
import jakarta.annotation.Nonnull;
import org.jetbrains.idea.maven.localize.MavenLocalize;

/**
 * @author VISTALL
 * @since 2025-06-22
 */
public class MavenConfigFileType extends LanguageFileType {
    public static final MavenConfigFileType INSTANCE = new MavenConfigFileType();

    public MavenConfigFileType() {
        super(PlainTextLanguage.INSTANCE, true);
    }

    @Nonnull
    @Override
    public String getId() {
        return "MAVEN_CONFIG";
    }

    @Nonnull
    @Override
    public String getDefaultExtension() {
        return "config";
    }

    @Nonnull
    @Override
    public LocalizeValue getDescription() {
        return MavenLocalize.filetypeMavenConfigDescription();
    }

    @Nonnull
    @Override
    public Image getIcon() {
        return MavenIconGroup.mavenlogo();
    }
}
