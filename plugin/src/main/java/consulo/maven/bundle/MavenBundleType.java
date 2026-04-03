package consulo.maven.bundle;

import consulo.annotation.component.ExtensionImpl;
import consulo.application.Application;
import consulo.content.OrderRootType;
import consulo.content.base.BinariesOrderRootType;
import consulo.content.bundle.Sdk;
import consulo.content.bundle.SdkModificator;
import consulo.content.bundle.SdkType;
import consulo.maven.icon.MavenIconGroup;
import consulo.platform.Platform;
import consulo.platform.PlatformOperatingSystem;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.idea.maven.localize.MavenLocalize;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.io.File;
import java.nio.file.Path;
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
        return Application.get().getExtensionPoint(SdkType.class).findExtensionOrFail(MavenBundleType.class);
    }

    public MavenBundleType() {
        super("MVN_BUNDLE", MavenLocalize.mavenName(), MavenIconGroup.mavenlogo());
    }

    @Nonnull
    @Override
    public Set<String> getEnvironmentVariables(@Nonnull Platform platform) {
        return Set.of(MavenUtil.ENV_M2_HOME);
    }

    @Nonnull
    @Override
    public Collection<String> suggestHomePaths() {
        Platform platform = Platform.current();

        Set<String> paths = new LinkedHashSet<>();
        Path userHome = platform.user().homePath();
        Path underUserHome = userHome.resolve(MavenUtil.M2_DIR);
        if (MavenUtil.isValidMavenHome(underUserHome.toFile())) {
            paths.add(underUserHome.toAbsolutePath().toString());
        }

        PlatformOperatingSystem os = platform.os();
        if (os.isMac()) {
            File home = fromBrew();
            if (home != null) {
                paths.add(home.getPath());
            }

            if ((home = fromMacSystemJavaTools()) != null) {
                paths.add(home.getPath());
            }
        }
        else if (os.isLinux()) {
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
        return mavenVersion != null ? mavenVersion : "0.0.0";
    }
}
