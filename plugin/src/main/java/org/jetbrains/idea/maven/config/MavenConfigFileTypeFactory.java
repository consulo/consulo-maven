package org.jetbrains.idea.maven.config;

import consulo.annotation.component.ExtensionImpl;
import consulo.virtualFileSystem.fileType.FileNameMatcherFactory;
import consulo.virtualFileSystem.fileType.FileTypeConsumer;
import consulo.virtualFileSystem.fileType.FileTypeFactory;
import jakarta.annotation.Nonnull;
import jakarta.inject.Inject;

/**
 * @author VISTALL
 * @since 2025-06-22
 */
@ExtensionImpl
public class MavenConfigFileTypeFactory extends FileTypeFactory {
    private final FileNameMatcherFactory myFileNameMatcherFactory;

    @Inject
    public MavenConfigFileTypeFactory(FileNameMatcherFactory fileNameMatcherFactory) {
        myFileNameMatcherFactory = fileNameMatcherFactory;
    }

    @Override
    public void createFileTypes(@Nonnull FileTypeConsumer fileTypeConsumer) {
        fileTypeConsumer.consume(MavenConfigFileType.INSTANCE, myFileNameMatcherFactory.createExactFileNameMatcher("maven.config"));
        fileTypeConsumer.consume(MavenConfigFileType.INSTANCE, myFileNameMatcherFactory.createExactFileNameMatcher("jvm.config"));
    }
}
