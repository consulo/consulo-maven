package consulo.maven.bundle;

import consulo.annotation.component.ExtensionImpl;
import consulo.application.util.SystemInfo;
import consulo.content.OrderRootType;
import consulo.content.base.BinariesOrderRootType;
import consulo.content.bundle.Sdk;
import consulo.content.bundle.SdkModificator;
import consulo.content.bundle.SdkType;
import consulo.logging.Logger;
import consulo.platform.Platform;
import consulo.ui.image.Image;
import consulo.util.lang.StringUtil;
import consulo.util.lang.SystemProperties;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.idea.maven.MavenIcons;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author VISTALL
 * @since 2020-01-17
 */
@ExtensionImpl
public class MavenBundleType extends SdkType {
    @Nonnull
    public static MavenBundleType getInstance() {
        return EP_NAME.findExtensionOrFail(MavenBundleType.class);
    }

    private static final Logger LOG = Logger.getInstance(MavenBundleType.class);

    public MavenBundleType() {
        super("MVN_BUNDLE");
    }

    @Nonnull
    @Override
    public Set<String> getEnviromentVariables(@Nonnull Platform platform) {
        return Set.of(MavenUtil.ENV_M2_HOME);
    }

    @Nonnull
    @Override
    public Collection<String> suggestHomePaths() {
        Set<String> paths = new LinkedHashSet<>();
        String userHome = SystemProperties.getUserHome();
        if (!StringUtil.isEmptyOrSpaces(userHome)) {
            final File underUserHome = new File(userHome, MavenUtil.M2_DIR);
            if (MavenUtil.isValidMavenHome(underUserHome)) {
                paths.add(underUserHome.getPath());
            }
        }

        if (SystemInfo.isMac) {
            File home = fromBrew();
            if (home != null) {
                paths.add(home.getPath());
            }

            if ((home = fromMacSystemJavaTools()) != null) {
                paths.add(home.getPath());
            }
        }
        else if (SystemInfo.isLinux) {
            File home = new File("/usr/share/maven");
            if (MavenUtil.isValidMavenHome(home)) {
                paths.add(home.getPath());
            }

            home = new File("/usr/share/maven2");
            if (MavenUtil.isValidMavenHome(home)) {
                paths.add(home.getPath());
            }
        }

        return paths;
    }

    @Nullable
    private static File fromMacSystemJavaTools() {
        final File symlinkDir = new File("/usr/share/maven");
        if (MavenUtil.isValidMavenHome(symlinkDir)) {
            return symlinkDir;
        }

        // well, try to search
        final File dir = new File("/usr/share/java");
        final String[] list = dir.list();
        if (list == null || list.length == 0) {
            return null;
        }

        String home = null;
        final String prefix = "maven-";
        final int versionIndex = prefix.length();
        for (String path : list) {
            if (path.startsWith(prefix) && (home == null || StringUtil.compareVersionNumbers(
                path.substring(versionIndex),
                home.substring(versionIndex)
            ) > 0)) {
                home = path;
            }
        }

        if (home != null) {
            File file = new File(dir, home);
            if (MavenUtil.isValidMavenHome(file)) {
                return file;
            }
        }

        return null;
    }

    @Nullable
    private static File fromBrew() {
        final File brewDir = new File("/usr/local/Cellar/maven");
        final String[] list = brewDir.list();
        if (list == null || list.length == 0) {
            return null;
        }

        if (list.length > 1) {
            Arrays.sort(list, (o1, o2) -> StringUtil.compareVersionNumbers(o2, o1));
        }

        final File file = new File(brewDir, list[0] + "/libexec");
        return MavenUtil.isValidMavenHome(file) ? file : null;
    }

    @Override
    public boolean canCreatePredefinedSdks() {
        return true;
    }

    @Override
    public boolean isValidSdkHome(String sdkHome) {
        return MavenUtil.isValidMavenHome(new File(sdkHome));
    }

    @Override
    public void setupSdkPaths(Sdk sdk) {
        SdkModificator sdkModificator = sdk.getSdkModificator();

        File libDir = new File(sdk.getHomePath(), "lib");

        if (libDir.exists()) {
            for (File jarFile : libDir.listFiles()) {
                if (jarFile.getName().endsWith(".jar")) {
                    VirtualFile file = LocalFileSystem.getInstance().findFileByIoFile(jarFile);
                    if (file != null) {
                        sdkModificator.addRoot(file, BinariesOrderRootType.getInstance());
                    }
                }
            }
        }

        sdkModificator.commitChanges();
    }

    @Override
    public boolean isRootTypeApplicable(OrderRootType type) {
        return type == BinariesOrderRootType.getInstance();
    }

    @Nullable
    @Override
    public String getVersionString(String sdkHome) {
        String mavenVersion = MavenUtil.getMavenVersion(sdkHome);
        if (mavenVersion != null) {
            return mavenVersion;
        }
        return "0.0.0";
    }

    @Override
    @Nonnull
    public String suggestSdkName(String currentSdkName, String sdkHome) {
        return getPresentableName() + " " + getVersionString(sdkHome);
    }

    @Nonnull
    @Override
    public String getPresentableName() {
        return "Maven";
    }

    @Nullable
    @Override
    public Image getIcon() {
        return MavenIcons.MavenLogo;
    }
}
