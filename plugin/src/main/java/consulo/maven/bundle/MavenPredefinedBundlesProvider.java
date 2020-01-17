package consulo.maven.bundle;

import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import consulo.bundle.PredefinedBundlesProvider;
import consulo.container.plugin.PluginManager;

import javax.annotation.Nonnull;
import java.io.File;

/**
 * @author VISTALL
 * @since 2020-01-17
 */
public class MavenPredefinedBundlesProvider extends PredefinedBundlesProvider
{
	@Override
	public void createBundles(@Nonnull Context context)
	{
		File pluginPath = PluginManager.getPluginPath(MavenBundleType.class);
		MavenBundleType type = MavenBundleType.getInstance();

		for(File file : pluginPath.listFiles())
		{
			if(type.isValidSdkHome(file.getPath()))
			{
				Sdk sdk = context.createSdk(type, file.getPath());

				SdkModificator sdkModificator = sdk.getSdkModificator();
				sdkModificator.setVersionString(type.getVersionString(sdk));
				sdkModificator.setHomePath(file.getPath());
				sdkModificator.commitChanges();

				type.setupSdkPaths(sdk);
			}
		}
	}
}
