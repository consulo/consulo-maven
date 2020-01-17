package consulo.maven.bundle;

import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import consulo.logging.Logger;
import consulo.roots.types.BinariesOrderRootType;
import consulo.ui.image.Image;
import icons.MavenIcons;
import org.jetbrains.idea.maven.utils.MavenUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.jar.Attributes;
import java.util.jar.JarFile;

/**
 * @author VISTALL
 * @since 2020-01-17
 */
public class MavenBundleType extends SdkType
{
	@Nonnull
	public static MavenBundleType getInstance()
	{
		return EP_NAME.findExtensionOrFail(MavenBundleType.class);
	}

	private static final Logger LOG = Logger.getInstance(MavenBundleType.class);

	public MavenBundleType()
	{
		super("MVN_BUNDLE");
	}

	@Override
	public boolean isValidSdkHome(String sdkHome)
	{
		return MavenUtil.isValidMavenHome(new File(sdkHome));
	}

	@Override
	public void setupSdkPaths(Sdk sdk)
	{
		SdkModificator sdkModificator = sdk.getSdkModificator();

		File libDir = new File(sdk.getHomePath(), "lib");

		if(libDir.exists())
		{
			for(File jarFile : libDir.listFiles())
			{
				if(jarFile.getName().endsWith(".jar"))
				{
					VirtualFile file = LocalFileSystem.getInstance().findFileByIoFile(jarFile);
					if(file != null)
					{
						sdkModificator.addRoot(file, BinariesOrderRootType.getInstance());
					}
				}
			}
		}

		sdkModificator.commitChanges();
	}

	@Override
	public boolean isRootTypeApplicable(OrderRootType type)
	{
		return type == BinariesOrderRootType.getInstance();
	}

	@Nullable
	@Override
	public String getVersionString(String sdkHome)
	{
		String mavenVersion = MavenUtil.getMavenVersion(sdkHome);
		if(mavenVersion != null)
		{
			return mavenVersion;
		}
		return "0.0.0";
	}

	@Override
	@Nonnull
	public String suggestSdkName(String currentSdkName, String sdkHome)
	{
		return getPresentableName() + " " + getVersionString(sdkHome);
	}

	@Nonnull
	@Override
	public String getPresentableName()
	{
		return "Maven";
	}

	@Nullable
	@Override
	public Image getIcon()
	{
		return MavenIcons.MavenLogo;
	}
}
