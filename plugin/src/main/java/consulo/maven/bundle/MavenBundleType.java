package consulo.maven.bundle;

import consulo.annotation.component.ExtensionImpl;
import consulo.content.OrderRootType;
import consulo.content.base.BinariesOrderRootType;
import consulo.content.bundle.Sdk;
import consulo.content.bundle.SdkModificator;
import consulo.content.bundle.SdkType;
import consulo.logging.Logger;
import consulo.ui.image.Image;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.idea.maven.MavenIcons;
import org.jetbrains.idea.maven.utils.MavenUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;

/**
 * @author VISTALL
 * @since 2020-01-17
 */
@ExtensionImpl
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
