// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.externalSystemIntegration.output.quickfixes;

import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.vfs.jrt.JrtFileSystem;
import com.intellij.util.ArrayUtil;
import consulo.content.bundle.Sdk;
import consulo.disposer.Disposable;
import consulo.module.Module;
import consulo.module.ModuleManager;
import consulo.module.content.ModuleRootManager;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import org.jetbrains.idea.maven.utils.MavenLog;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Predicate;

public final class CacheForCompilerErrorMessages {
    private static final byte[] KEY = "compiler.err.unsupported.release.version".getBytes(StandardCharsets.UTF_8);
    private static final byte[] DELIMITER = new byte[]{1, 0}; // SOH, NUL byte

    private static final List<Predicate<String>> DEFAULT_CHECK = Arrays.asList(
        s -> s.contains("source release") && s.contains("requires target release"),
        s -> s.contains("invalid target release"),
        s -> s.contains("release version") && s.contains("not supported"), // en
        s -> s.contains("\u30EA\u30EA\u30FC\u30B9\u30FB\u30D0\u30FC\u30B8\u30E7\u30F3")
            && s.contains("\u306F\u30B5\u30DD\u30FC\u30C8\u3055\u308C\u3066\u3044\u307E\u305B\u3093"), // ja
        s -> s.contains("\u4E0D\u652F\u6301\u53D1\u884C\u7248\u672C") // zh_CN
    );

    private static final Map<String, List<Predicate<String>>> cache = new WeakHashMap<>();

    private CacheForCompilerErrorMessages() {
    }

    public static void connectToJdkListener(@Nonnull Project project, @Nonnull Disposable disposable) {
        project.getMessageBus().connect(disposable).subscribe(ProjectJdkTable.JDK_TABLE_TOPIC, new ProjectJdkTable.Listener() {
            @Override
            public void jdkAdded(@Nonnull Sdk jdk) {
                synchronized (cache) {
                    cache.remove(jdk.getName());
                }
            }

            @Override
            public void jdkRemoved(@Nonnull Sdk jdk) {
                synchronized (cache) {
                    cache.remove(jdk.getName());
                }
            }

            @Override
            public void jdkNameChanged(@Nonnull Sdk jdk, @Nonnull String previousName) {
                synchronized (cache) {
                    List<Predicate<String>> list = cache.get(previousName);
                    if (list != null) {
                        cache.put(jdk.getName(), list);
                    }
                }
            }
        });
    }

    @Nonnull
    public static List<Predicate<String>> getPredicatesToCheck(@Nonnull Project project, @Nonnull String moduleName) {
        Module module = ModuleManager.getInstance(project).findModuleByName(moduleName);
        if (module == null) return DEFAULT_CHECK;

        Sdk sdk = ModuleRootManager.getInstance(module).getSdk();
        if (sdk == null) return DEFAULT_CHECK;

        synchronized (cache) {
            return cache.computeIfAbsent(sdk.getName(), k -> readFrom(sdk));
        }
    }

    @Nonnull
    private static List<Predicate<String>> readFrom(@Nonnull Sdk sdk) {
        JavaSdkVersion version = JavaSdk.getInstance().getVersion(sdk);
        if (version == null || !version.isAtLeast(JavaSdkVersion.JDK_1_9)) {
            return DEFAULT_CHECK;
        }

        try {
            VirtualFile homeDirectory = sdk.getHomeDirectory();
            if (homeDirectory == null) return DEFAULT_CHECK;

            VirtualFile jrtLocalFile = JrtFileSystem.getInstance().getRootByLocal(homeDirectory);
            if (jrtLocalFile == null) return DEFAULT_CHECK;

            VirtualFile resourcesDir = jrtLocalFile.findFileByRelativePath("jdk.compiler/com/sun/tools/javac/resources");
            if (resourcesDir == null) return DEFAULT_CHECK;

            VirtualFile[] children = resourcesDir.getChildren();
            if (children == null) return DEFAULT_CHECK;

            List<Predicate<String>> list = new ArrayList<>();
            for (VirtualFile child : children) {
                if (child.getName().startsWith("compiler") && child.getName().endsWith(".class")) {
                    Predicate<String> predicate = readFromBinaryFile(child);
                    if (predicate != null) {
                        list.add(predicate);
                    }
                }
            }

            return list.isEmpty() ? DEFAULT_CHECK : list;
        } catch (Throwable e) {
            MavenLog.LOG.warn(e);
            return DEFAULT_CHECK;
        }
    }

    @jakarta.annotation.Nullable
    private static Predicate<String> readFromBinaryFile(@jakarta.annotation.Nullable VirtualFile file) {
        if (file == null) return null;
        try {
            byte[] allBytes = file.contentsToByteArray();
            int indexKey = ArrayUtil.indexOf(allBytes, KEY, 0);
            if (indexKey == -1) return null;

            int startFrom = indexKey + KEY.length + 3;
            int endIndex = findNextSOH(allBytes, startFrom);
            if (endIndex == -1 || startFrom == endIndex) return null;

            String message = new String(allBytes, startFrom, endIndex - startFrom, StandardCharsets.UTF_8);
            return toMessagePredicate(message);
        } catch (Throwable e) {
            MavenLog.LOG.warn(e);
            return null;
        }
    }

    @Nonnull
    private static Predicate<String> toMessagePredicate(@Nonnull String message) {
        int idx = message.indexOf("{0}");
        String first = idx >= 0 ? message.substring(0, idx) : message;
        String second = idx >= 0 ? message.substring(idx + 3) : "";
        return s -> s.contains(first) && s.contains(second);
    }

    private static int findNextSOH(@Nonnull byte[] bytes, int startFrom) {
        if (startFrom == -1) return -1;
        for (int i = startFrom; i < bytes.length - 1; i++) {
            if (bytes[i] == DELIMITER[0] && bytes[i + 1] == DELIMITER[1]) {
                return i;
            }
        }
        return -1;
    }
}
