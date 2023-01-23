package org.jetbrains.idea.maven.importing;

import consulo.annotation.component.ExtensionImpl;
import consulo.application.Application;
import consulo.component.extension.preview.ExtensionPreview;
import consulo.component.extension.preview.ExtensionPreviewRecorder;
import jakarta.inject.Inject;

import javax.annotation.Nonnull;
import java.util.function.Consumer;

/**
 * @author VISTALL
 * @since 23/01/2023
 */
@ExtensionImpl
public class MavenImporterExtensionPreview implements ExtensionPreviewRecorder<MavenImporter>
{
	private final Application myApplication;

	@Inject
	public MavenImporterExtensionPreview(Application application)
	{
		myApplication = application;
	}

	@Override
	public void analyze(@Nonnull Consumer<ExtensionPreview<MavenImporter>> consumer)
	{
		myApplication.getExtensionPoint(MavenImporter.class).forEachExtensionSafe(mavenImporter ->
		{
			String id = mavenImporter.getId();
			if(id == null)
			{
				return;
			}

			consumer.accept(new ExtensionPreview<>(MavenImporter.class, id, mavenImporter));
		});
	}
}
