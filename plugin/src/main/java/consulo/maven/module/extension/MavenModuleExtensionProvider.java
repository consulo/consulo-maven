package consulo.maven.module.extension;

import consulo.annotation.component.ExtensionImpl;
import consulo.localize.LocalizeValue;
import consulo.maven.icon.MavenIconGroup;
import consulo.module.content.layer.ModuleExtensionProvider;
import consulo.module.content.layer.ModuleRootLayer;
import consulo.module.extension.ModuleExtension;
import consulo.module.extension.MutableModuleExtension;
import consulo.ui.image.Image;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author VISTALL
 * @since 20/01/2023
 */
@ExtensionImpl
public class MavenModuleExtensionProvider implements ModuleExtensionProvider<MavenModuleExtension>
{
	@Nonnull
	@Override
	public String getId()
	{
		return "maven";
	}

	@Nullable
	@Override
	public String getParentId()
	{
		return "java";
	}

	@Nonnull
	@Override
	public LocalizeValue getName()
	{
		return LocalizeValue.localizeTODO("Maven");
	}

	@Nonnull
	@Override
	public Image getIcon()
	{
		return MavenIconGroup.mavenlogo();
	}

	@Nonnull
	@Override
	public ModuleExtension<MavenModuleExtension> createImmutableExtension(@Nonnull ModuleRootLayer moduleRootLayer)
	{
		return new MavenModuleExtension(getId(), moduleRootLayer);
	}

	@Nonnull
	@Override
	public MutableModuleExtension<MavenModuleExtension> createMutableExtension(@Nonnull ModuleRootLayer moduleRootLayer)
	{
		return new MavenMutableModuleExtension(getId(), moduleRootLayer);
	}

	@Override
	public boolean isSystemOnly()
	{
		return true;
	}
}
