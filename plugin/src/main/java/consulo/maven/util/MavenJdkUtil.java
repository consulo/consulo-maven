package consulo.maven.util;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkTable;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.util.containers.ContainerUtil;

/**
 * @author VISTALL
 * @since 2018-08-18
 */
public class MavenJdkUtil
{
	@Nullable
	public static Sdk findSdkOfLevel(@Nonnull LanguageLevel languageLevel)
	{
		List<Sdk> list = SdkTable.getInstance().getSdksOfType(JavaSdk.getInstance());
		ContainerUtil.weightSort(list, sdk ->
		{
			JavaSdkVersion version = JavaSdk.getInstance().getVersion(sdk);
			int ordinal = version == null ? 0 : version.ordinal();
			return sdk.isPredefined() ? ordinal * 100 : ordinal;
		});

		Sdk temp = null;
		for(Sdk sdk : list)
		{
			JavaSdkVersion version = JavaSdk.getInstance().getVersion(sdk);
			if(version == null)
			{
				continue;
			}

			if(version.getMaxLanguageLevel().isAtLeast(languageLevel))
			{
				temp = sdk;
			}
		}

		return temp;
	}
}
