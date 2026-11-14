package consulo.maven.util;

import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.projectRoots.JavaSdkVersion;
import consulo.content.bundle.Sdk;
import consulo.content.bundle.SdkTable;
import consulo.java.language.bundle.JavaSdkTypeUtil;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * @author VISTALL
 * @since 2018-08-18
 */
public class MavenJdkUtil {
    @Nonnull
    public static LanguageLevel getDefaultRunLevel(String currentMavenVersion) {
        if (StringUtil.compareVersionNumbers(currentMavenVersion, "4.0") >= 0) {
            return LanguageLevel.JDK_17;
        }

        boolean forceUseJava8 = StringUtil.compareVersionNumbers(currentMavenVersion, "3.9.0") >= 0;
        if (forceUseJava8) {
            return LanguageLevel.JDK_1_8;
        }

        if (StringUtil.compareVersionNumbers(currentMavenVersion, "3.3.1") >= 0) {
            return LanguageLevel.JDK_1_7;
        }

        return LanguageLevel.JDK_1_5;
    }

    @Nullable
    public static Sdk findSdkOfLevel(@Nonnull LanguageLevel languageLevel, @Nullable String runtimeJdkName) {
        return findSdkOfLevel(languageLevel, runtimeJdkName, null);
    }

    /**
     * @param platformTargetId the machine of the bundle - {@code null} for the local one
     */
    @Nullable
    public static Sdk findSdkOfLevel(@Nonnull LanguageLevel languageLevel, @Nullable String runtimeJdkName, @Nullable String platformTargetId) {
        SdkTable sdkTable = SdkTable.getInstance();
        if (runtimeJdkName != null) {
            Sdk sdk = sdkTable.findSdk(runtimeJdkName);
            if (sdk != null && Objects.equals(sdk.getPlatformTargetId(), platformTargetId)) {
                JavaSdkVersion version = JavaSdkTypeUtil.getVersion(sdk);
                if (version != null && version.getMaxLanguageLevel().isAtLeast(languageLevel)) {
                    return sdk;
                }
            }
        }

        List<Sdk> list = ContainerUtil.filter(JavaSdkTypeUtil.getAllJavaSdks(), sdk -> Objects.equals(sdk.getPlatformTargetId(), platformTargetId));
        ContainerUtil.weightSort(list, sdk ->
        {
            JavaSdkVersion version = JavaSdkTypeUtil.getVersion(sdk);
            int ordinal = version == null ? 0 : version.ordinal();
            return sdk.isPredefined() ? ordinal * 100 : ordinal;
        });

        for (Sdk sdk : list) {
            JavaSdkVersion version = JavaSdkTypeUtil.getVersion(sdk);
            if (version == null) {
                continue;
            }

            if (version.getMaxLanguageLevel().isAtLeast(languageLevel)) {
                return sdk;
            }
        }

        return null;
    }
}
