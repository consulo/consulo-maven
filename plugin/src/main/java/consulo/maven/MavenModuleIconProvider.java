package consulo.maven;

import consulo.annotation.component.ExtensionImpl;
import consulo.maven.icon.MavenIconGroup;
import consulo.maven.module.extension.MavenModuleExtension;
import consulo.module.Module;
import consulo.module.content.ModuleIconProvider;
import consulo.ui.image.Image;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author VISTALL
 * @since 26/12/2025
 */
@ExtensionImpl
public class MavenModuleIconProvider implements ModuleIconProvider {
    @Nullable
    @Override
    public Image getIcon(@Nonnull Module module) {
        MavenModuleExtension extension = module.getExtension(MavenModuleExtension.class);
        if (extension != null) {
            return MavenIconGroup.modulesclosed();
        }
        return null;
    }
}
