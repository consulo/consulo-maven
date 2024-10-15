/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.maven.utils;

import consulo.annotation.access.RequiredReadAction;
import consulo.application.Application;
import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.ProgressManager;
import consulo.application.progress.Task;
import consulo.application.util.Semaphore;
import consulo.application.util.concurrent.AppExecutorUtil;
import consulo.codeEditor.Editor;
import consulo.component.ProcessCanceledException;
import consulo.container.boot.ContainerPathManager;
import consulo.content.bundle.Sdk;
import consulo.content.bundle.SdkTable;
import consulo.fileEditor.FileEditorManager;
import consulo.fileTemplate.FileTemplate;
import consulo.fileTemplate.FileTemplateManager;
import consulo.ide.impl.idea.openapi.util.io.JarUtil;
import consulo.ide.impl.idea.openapi.vfs.VfsUtil;
import consulo.ide.impl.idea.util.DisposeAwareRunnable;
import consulo.java.language.module.extension.JavaModuleExtension;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.completion.lookup.LookupElementBuilder;
import consulo.language.editor.template.Template;
import consulo.language.editor.template.TemplateManager;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.util.ModuleUtilCore;
import consulo.maven.MavenNotificationGroup;
import consulo.maven.bundle.MavenBundleType;
import consulo.maven.rt.server.common.model.MavenConstants;
import consulo.maven.rt.server.common.model.MavenId;
import consulo.maven.rt.server.common.model.MavenPlugin;
import consulo.maven.rt.server.common.server.MavenServerUtil;
import consulo.module.Module;
import consulo.navigation.OpenFileDescriptor;
import consulo.navigation.OpenFileDescriptorFactory;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.process.cmd.ParametersList;
import consulo.project.DumbService;
import consulo.project.Project;
import consulo.project.startup.StartupManager;
import consulo.project.ui.notification.Notification;
import consulo.project.ui.notification.NotificationType;
import consulo.project.ui.notification.Notifications;
import consulo.ui.ModalityState;
import consulo.ui.UIAccess;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.image.ImageKey;
import consulo.util.collection.ContainerUtil;
import consulo.util.io.FileUtil;
import consulo.util.lang.Comparing;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import consulo.util.lang.SystemProperties;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.archive.ArchiveVfsUtil;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.server.MavenServerManager;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.zip.CRC32;

public class MavenUtil {
    public static final String SETTINGS_XML = "settings.xml";
    public static final String DOT_M2_DIR = ".m2";
    public static final String PROP_USER_HOME = "user.home";
    public static final String ENV_M2_HOME = "M2_HOME";
    public static final String M2_DIR = "m2";
    public static final String BIN_DIR = "bin";
    public static final String CONF_DIR = "conf";
    public static final String M2_CONF_FILE = "m2.conf";
    public static final String REPOSITORY_DIR = "repository";
    public static final String LIB_DIR = "lib";

    @SuppressWarnings("unchecked")
    private static final Pair<Pattern, String>[] SUPER_POM_PATHS = new Pair[]{
        Pair.create(
            Pattern.compile("maven-\\d+\\.\\d+\\.\\d+-uber\\.jar"),
            "org/apache/maven/project/" + MavenConstants.SUPER_POM_XML
        ),
        Pair.create(
            Pattern.compile("maven-model-builder-\\d+\\.\\d+\\.\\d+\\.jar"),
            "org/apache/maven/model/" + MavenConstants.SUPER_POM_XML
        )
    };

    private static volatile Map<String, String> ourPropertiesFromMvnOpts;

    public static Map<String, String> getPropertiesFromMavenOpts() {
        Map<String, String> res = ourPropertiesFromMvnOpts;
        if (res == null) {
            String mavenOpts = System.getenv("MAVEN_OPTS");
            if (mavenOpts != null) {
                ParametersList mavenOptsList = new ParametersList();
                mavenOptsList.addParametersString(mavenOpts);
                res = mavenOptsList.getProperties();
            }
            else {
                res = Collections.emptyMap();
            }

            ourPropertiesFromMvnOpts = res;
        }

        return res;
    }


    public static void invokeLater(Project p, Runnable r) {
        invokeLater(p, Application.get().getDefaultModalityState(), r);
    }

    public static void invokeLater(final Project p, final ModalityState state, final Runnable r) {
        if (isNoBackgroundMode()) {
            r.run();
        }
        else {
            Application.get().invokeLater(DisposeAwareRunnable.create(r, p), state);
        }
    }

    public static void invokeAndWait(Project p, Runnable r) {
        invokeAndWait(p, Application.get().getDefaultModalityState(), r);
    }

    public static void invokeAndWait(final Project p, final ModalityState state, final Runnable r) {
        if (isNoBackgroundMode()) {
            r.run();
        }
        else if (Application.get().isDispatchThread()) {
            r.run();
        }
        else {
            Application.get().invokeAndWait(DisposeAwareRunnable.create(r, p), state);
        }
    }

    public static void smartInvokeAndWait(final Project p, final ModalityState state, final Runnable r) {
        if (isNoBackgroundMode() || Application.get().isDispatchThread()) {
            r.run();
        }
        else {
            final Semaphore semaphore = new Semaphore();
            semaphore.down();
            DumbService.getInstance(p).smartInvokeLater(
                () -> {
                    try {
                        r.run();
                    }
                    finally {
                        semaphore.up();
                    }
                },
                state
            );
            semaphore.waitFor();
        }
    }

    public static void invokeAndWaitWriteAction(Project p, final Runnable r) {
        invokeAndWait(p, () -> Application.get().runWriteAction(r));
    }

    public static void runDumbAware(final Project project, final Runnable r) {
        if (DumbService.isDumbAware(r)) {
            r.run();
        }
        else {
            DumbService.getInstance(project).runWhenSmart(DisposeAwareRunnable.create(r, project));
        }
    }

    public static void runWhenInitialized(final Project project, final Runnable r) {
        if (project.isDisposed()) {
            return;
        }

        if (isNoBackgroundMode()) {
            r.run();
            return;
        }

        if (!project.isInitialized()) {
            StartupManager.getInstance(project).registerPostStartupActivity(DisposeAwareRunnable.create(r, project));
            return;
        }

        runDumbAware(project, r);
    }

    public static boolean isNoBackgroundMode() {
        return Application.get().isUnitTestMode() || Application.get().isHeadlessEnvironment();
    }

    @RequiredUIAccess
    public static boolean isInModalContext() {
        return !isNoBackgroundMode() && UIAccess.current().isInModalContext();
    }

    public static void showError(Project project, String title, Throwable e) {
        MavenLog.LOG.warn(title, e);
        Notifications.Bus.notify(new Notification(MavenNotificationGroup.ROOT, title, e.getMessage(), NotificationType.ERROR), project);
    }

    public static File getPluginSystemDir(String folder) {
        // PathManager.getSystemPath() may return relative path
        return new File(ContainerPathManager.get().getSystemPath(), "Maven" + "/" + folder).getAbsoluteFile();
    }

    @Nullable
    public static VirtualFile findProfilesXmlFile(@Nullable VirtualFile pomFile) {
        if (pomFile == null) {
            return null;
        }
        VirtualFile parent = pomFile.getParent();
        if (parent == null) {
            return null;
        }
        return parent.findChild(MavenConstants.PROFILES_XML);
    }

    @Nullable
    public static File getProfilesXmlIoFile(VirtualFile pomFile) {
        if (pomFile == null) {
            return null;
        }
        VirtualFile parent = pomFile.getParent();
        if (parent == null) {
            return null;
        }
        return new File(parent.getPath(), MavenConstants.PROFILES_XML);
    }

    public static <T, U> List<T> collectFirsts(List<Pair<T, U>> pairs) {
        List<T> result = new ArrayList<>(pairs.size());
        for (Pair<T, ?> each : pairs) {
            result.add(each.first);
        }
        return result;
    }

    public static <T, U> List<U> collectSeconds(List<Pair<T, U>> pairs) {
        List<U> result = new ArrayList<>(pairs.size());
        for (Pair<T, U> each : pairs) {
            result.add(each.second);
        }
        return result;
    }

    public static List<String> collectPaths(List<VirtualFile> files) {
        return ContainerUtil.map(files, VirtualFile::getPath);
    }

    public static List<VirtualFile> collectFiles(Collection<MavenProject> projects) {
        return ContainerUtil.map(projects, MavenProject::getFile);
    }

    public static <T> boolean equalAsSets(final Collection<T> collection1, final Collection<T> collection2) {
        return toSet(collection1).equals(toSet(collection2));
    }

    private static <T> Collection<T> toSet(final Collection<T> collection) {
        return (collection instanceof Set ? collection : new HashSet<>(collection));
    }

    public static <T, U> List<Pair<T, U>> mapToList(Map<T, U> map) {
        return ContainerUtil.map2List(map.entrySet(), tuEntry -> Pair.create(tuEntry.getKey(), tuEntry.getValue()));
    }

    public static String formatHtmlImage(ImageKey imageKey) {
        return "<icon src=\"" + imageKey.getGroupId() + "@" + imageKey.getImageId() + "\"/> ";
    }

    @RequiredReadAction
    public static void runOrApplyMavenProjectFileTemplate(
        Project project,
        VirtualFile file,
        @Nonnull MavenId projectId,
        boolean interactive
    ) throws IOException {
        runOrApplyMavenProjectFileTemplate(project, file, projectId, null, null, interactive);
    }

    @RequiredReadAction
    public static void runOrApplyMavenProjectFileTemplate(
        Project project,
        VirtualFile file,
        @Nonnull MavenId projectId,
        MavenId parentId,
        VirtualFile parentFile,
        boolean interactive
    ) throws IOException {
        Properties properties = new Properties();
        Properties conditions = new Properties();
        properties.setProperty("GROUP_ID", projectId.getGroupId());
        properties.setProperty("ARTIFACT_ID", projectId.getArtifactId());
        properties.setProperty("VERSION", projectId.getVersion());
        if (parentId != null) {
            conditions.setProperty("HAS_PARENT", "true");
            properties.setProperty("PARENT_GROUP_ID", parentId.getGroupId());
            properties.setProperty("PARENT_ARTIFACT_ID", parentId.getArtifactId());
            properties.setProperty("PARENT_VERSION", parentId.getVersion());

            if (parentFile != null) {
                VirtualFile modulePath = file.getParent();
                VirtualFile parentModulePath = parentFile.getParent();

                if (!Comparing.equal(modulePath.getParent(), parentModulePath)) {
                    String relativePath = VfsUtil.getPath(file, parentModulePath, '/');
                    if (relativePath != null) {
                        if (relativePath.endsWith("/")) {
                            relativePath = relativePath.substring(0, relativePath.length() - 1);
                        }

                        conditions.setProperty("HAS_RELATIVE_PATH", "true");
                        properties.setProperty("PARENT_RELATIVE_PATH", relativePath);
                    }
                }
            }
        }
        runOrApplyFileTemplate(
            project,
            file,
            MavenFileTemplateGroupFactory.MAVEN_PROJECT_XML_TEMPLATE,
            properties,
            conditions,
            interactive
        );
    }

    @RequiredReadAction
    public static void runFileTemplate(Project project, VirtualFile file, String templateName) throws IOException {
        runOrApplyFileTemplate(project, file, templateName, new Properties(), new Properties(), true);
    }

    @RequiredReadAction
    private static void runOrApplyFileTemplate(
        Project project,
        VirtualFile file,
        String templateName,
        Properties properties,
        Properties conditions,
        boolean interactive
    ) throws IOException {
        FileTemplateManager manager = FileTemplateManager.getInstance(project);
        FileTemplate fileTemplate = manager.getJ2eeTemplate(templateName);
        Properties allProperties = manager.getDefaultProperties();
        if (!interactive) {
            allProperties.putAll(properties);
        }
        allProperties.putAll(conditions);
        String text = fileTemplate.getText(allProperties);
        Pattern pattern = Pattern.compile("\\$\\{(.*)\\}");
        Matcher matcher = pattern.matcher(text);
        StringBuffer builder = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(builder, "\\$" + matcher.group(1).toUpperCase() + "\\$");
        }
        matcher.appendTail(builder);
        text = builder.toString();

        Template template = TemplateManager.getInstance(project).createTemplate("", "", text);
        for (int i = 0; i < template.getSegmentsCount(); i++) {
            if (i == template.getEndSegmentNumber()) {
                continue;
            }
            String name = template.getSegmentName(i);
            String value = "\"" + properties.getProperty(name, "") + "\"";
            template.addVariable(name, value, value, true);
        }

        if (interactive) {
            OpenFileDescriptor descriptor = OpenFileDescriptorFactory.getInstance(project).builder(file).build();
            Editor editor = FileEditorManager.getInstance(project).openTextEditor(descriptor, true);
            editor.getDocument().setText("");
            TemplateManager.getInstance(project).startTemplate(editor, template);
        }
        else {
            VirtualFileUtil.saveText(file, template.getTemplateText());

            PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
            if (psiFile != null) {
                //new ReformatCodeProcessor(project, psiFile, null, false).run();
            }
        }
    }

    public static <T extends Collection<Pattern>> T collectPattern(String text, T result) {
        String antPattern = FileUtil.convertAntToRegexp(text.trim());
        try {
            result.add(Pattern.compile(antPattern));
        }
        catch (PatternSyntaxException ignore) {
        }
        return result;
    }

    public static boolean isIncluded(String relativeName, List<Pattern> includes, List<Pattern> excludes) {
        boolean result = false;
        for (Pattern each : includes) {
            if (each.matcher(relativeName).matches()) {
                result = true;
                break;
            }
        }
        if (!result) {
            return false;
        }
        for (Pattern each : excludes) {
            if (each.matcher(relativeName).matches()) {
                return false;
            }
        }
        return true;
    }

    public static void run(Project project, String title, final MavenTask task) throws MavenProcessCanceledException {
        final Exception[] canceledEx = new Exception[1];
        final RuntimeException[] runtimeEx = new RuntimeException[1];
        final Error[] errorEx = new Error[1];

        ProgressManager.getInstance().run(new Task.Modal(project, title, true) {
            @Override
            public void run(@Nonnull ProgressIndicator i) {
                try {
                    task.run(new MavenProgressIndicator(i));
                }
                catch (MavenProcessCanceledException | ProcessCanceledException e) {
                    canceledEx[0] = e;
                }
                catch (RuntimeException e) {
                    runtimeEx[0] = e;
                }
                catch (Error e) {
                    errorEx[0] = e;
                }
            }
        });
        if (canceledEx[0] instanceof MavenProcessCanceledException) {
            throw (MavenProcessCanceledException)canceledEx[0];
        }
        if (canceledEx[0] instanceof ProcessCanceledException) {
            throw new MavenProcessCanceledException();
        }

        if (runtimeEx[0] != null) {
            throw runtimeEx[0];
        }
        if (errorEx[0] != null) {
            throw errorEx[0];
        }
    }

    public static MavenTaskHandler runInBackground(
        final Project project,
        final String title,
        final boolean cancellable,
        final MavenTask task
    ) {
        final MavenProgressIndicator indicator = new MavenProgressIndicator();

        Runnable runnable = () -> {
            if (project.isDisposed()) {
                return;
            }

            try {
                task.run(indicator);
            }
            catch (MavenProcessCanceledException | ProcessCanceledException ignore) {
                indicator.cancel();
            }
        };

        if (isNoBackgroundMode()) {
            runnable.run();
            return () -> {
            };
        }
        else {
            final Future<?> future = AppExecutorUtil.getAppExecutorService().submit(runnable);
            final MavenTaskHandler handler = () -> {
                try {
                    future.get();
                }
                catch (InterruptedException | ExecutionException e) {
                    MavenLog.LOG.error(e);
                }
            };

            invokeLater(
                project,
                () -> {
                    if (future.isDone()) {
                        return;
                    }
                    new Task.Backgroundable(project, title, cancellable) {
                        @Override
                        public void run(@Nonnull ProgressIndicator i) {
                            indicator.setIndicator(i);
                            handler.waitFor();
                        }
                    }.queue();
                }
            );
            return handler;
        }
    }

    @Nullable
    public static Pair<File, Sdk> resolveMavenHome(@Nullable String mavenBundleName) {
        if (!StringUtil.isEmptyOrSpaces(mavenBundleName)) {
            Sdk sdk = SdkTable.getInstance().findSdk(mavenBundleName);
            if (sdk != null) {
                String homePath = sdk.getHomePath();
                if (homePath != null) {
                    return Pair.create(new File(homePath), sdk);
                }
            }

            return null;
        }

        List<Sdk> sdks = new ArrayList<>(SdkTable.getInstance().getSdksOfType(MavenBundleType.getInstance()));
        sdks.sort((o1, o2) -> StringUtil.compareVersionNumbers(o1.getVersionString(), o2.getVersionString()));

        Sdk sdk = ContainerUtil.getLastItem(sdks);
        if (sdk != null) {
            String homePath = sdk.getHomePath();
            if (homePath != null) {
                return Pair.create(new File(homePath), sdk);
            }
        }
        return null;
    }

    @Nullable
    public static File resolveMavenHomeDirectory(@Nullable String mavenBundleName) {
        Pair<File, Sdk> pair = resolveMavenHome(mavenBundleName);
        return pair != null ? pair.getKey() : null;
    }

    public static boolean isValidMavenHome(File home) {
        return getMavenConfFile(home).exists();
    }

    public static File getMavenConfFile(File mavenHome) {
        return new File(new File(mavenHome, BIN_DIR), M2_CONF_FILE);
    }

    @Nullable
    public static String getMavenVersion(@Nullable File mavenHome) {
        if (mavenHome == null) {
            return null;
        }
        String[] libs = new File(mavenHome, "lib").list();

        if (libs != null) {
            for (String lib : libs) {
                if (lib.startsWith("maven-core-") && lib.endsWith(".jar")) {
                    String version = lib.substring("maven-core-".length(), lib.length() - ".jar".length());
                    if (StringUtil.contains(version, ".x")) {
                        Properties props = JarUtil.loadProperties(
                            new File(mavenHome, "lib/" + lib),
                            "META-INF/maven/org.apache.maven/maven-core/pom.properties"
                        );
                        return props != null ? props.getProperty("version") : null;
                    }
                    else {
                        return version;
                    }
                }
                if (lib.startsWith("maven-") && lib.endsWith("-uber.jar")) {
                    return lib.substring("maven-".length(), lib.length() - "-uber.jar".length());
                }
            }
        }
        return null;
    }

    @Nullable
    public static String getMavenVersion(String mavenHome) {
        return getMavenVersion(new File(mavenHome));
    }

    public static boolean isMaven3(String mavenHome) {
        String version = getMavenVersion(mavenHome);
        return version != null && version.compareTo("3.0.0") >= 0;
    }

    @Nullable
    public static File resolveGlobalSettingsFile(@Nullable String overriddenMavenHome) {
        File directory = resolveMavenHomeDirectory(overriddenMavenHome);
        return new File(new File(directory, CONF_DIR), SETTINGS_XML);
    }

    @Nonnull
    public static File resolveUserSettingsFile(@Nullable String overriddenUserSettingsFile) {
        if (!StringUtil.isEmptyOrSpaces(overriddenUserSettingsFile)) {
            return new File(overriddenUserSettingsFile);
        }
        return new File(resolveM2Dir(), SETTINGS_XML);
    }

    @Nonnull
    public static File resolveM2Dir() {
        return new File(SystemProperties.getUserHome(), DOT_M2_DIR);
    }

    @Nonnull
    public static File resolveLocalRepository(
        @Nullable String overriddenLocalRepository,
        @Nullable String overriddenMavenHome,
        @Nullable String overriddenUserSettingsFile
    ) {
        File result = null;
        if (!StringUtil.isEmptyOrSpaces(overriddenLocalRepository)) {
            result = new File(overriddenLocalRepository);
        }
        if (result == null) {
            result = doResolveLocalRepository(
                resolveUserSettingsFile(overriddenUserSettingsFile),
                resolveGlobalSettingsFile(overriddenMavenHome)
            );
        }
        try {
            return result.getCanonicalFile();
        }
        catch (IOException e) {
            return result;
        }
    }

    @Nonnull
    public static File doResolveLocalRepository(@Nullable File userSettingsFile, @Nullable File globalSettingsFile) {
        if (userSettingsFile != null) {
            final String fromUserSettings = getRepositoryFromSettings(userSettingsFile);
            if (!StringUtil.isEmpty(fromUserSettings)) {
                return new File(fromUserSettings);
            }
        }

        if (globalSettingsFile != null) {
            final String fromGlobalSettings = getRepositoryFromSettings(globalSettingsFile);
            if (!StringUtil.isEmpty(fromGlobalSettings)) {
                return new File(fromGlobalSettings);
            }
        }

        return new File(resolveM2Dir(), REPOSITORY_DIR);
    }

    @Nullable
    public static String getRepositoryFromSettings(final File file) {
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            return expandProperties(MavenJDOMUtil.findChildValueByPath(MavenJDOMUtil.read(bytes, null), "localRepository", null));
        }
        catch (IOException e) {
            return null;
        }
    }

    public static String expandProperties(String text) {
        if (StringUtil.isEmptyOrSpaces(text)) {
            return text;
        }
        Properties props = MavenServerUtil.collectSystemProperties();
        for (Map.Entry<Object, Object> each : props.entrySet()) {
            Object val = each.getValue();
            text = text.replace("${" + each.getKey() + "}", val instanceof CharSequence ? (CharSequence)val : val.toString());
        }
        return text;
    }

    @Nullable
    public static VirtualFile resolveSuperPomFile(@Nullable File mavenHome) {
        VirtualFile result = null;
        if (mavenHome != null) {
            result = doResolveSuperPomFile(new File(mavenHome, LIB_DIR));
        }
        return result == null ? doResolveSuperPomFile(MavenServerManager.getMavenLibDirectory()) : result;
    }

    @Nullable
    public static VirtualFile doResolveSuperPomFile(@Nonnull File mavenHome) {
        File[] files = mavenHome.listFiles();
        if (files == null) {
            return null;
        }

        for (File library : files) {
            for (Pair<Pattern, String> path : SUPER_POM_PATHS) {
                if (path.first.matcher(library.getName()).matches()) {
                    VirtualFile libraryVirtualFile = LocalFileSystem.getInstance().findFileByIoFile(library);
                    if (libraryVirtualFile == null) {
                        continue;
                    }

                    VirtualFile root = ArchiveVfsUtil.getJarRootForLocalFile(libraryVirtualFile);
                    if (root == null) {
                        continue;
                    }

                    VirtualFile pomFile = root.findFileByRelativePath(path.second);
                    if (pomFile != null) {
                        return pomFile;
                    }
                }
            }
        }

        return null;
    }

    public static List<LookupElement> getPhaseVariants(MavenProjectsManager manager) {
        Set<String> goals = new HashSet<>();
        goals.addAll(MavenConstants.PHASES);

        for (MavenProject mavenProject : manager.getProjects()) {
            for (MavenPlugin plugin : mavenProject.getPlugins()) {
                MavenPluginInfo pluginInfo = MavenArtifactUtil.readPluginInfo(manager.getLocalRepository(), plugin.getMavenId());
                if (pluginInfo != null) {
                    for (MavenPluginInfo.Mojo mojo : pluginInfo.getMojos()) {
                        goals.add(mojo.getDisplayName());
                    }
                }
            }
        }

        List<LookupElement> res = new ArrayList<>(goals.size());
        for (String goal : goals) {
            res.add(LookupElementBuilder.create(goal).withIcon(PlatformIconGroup.nodesTask()));
        }

        return res;
    }

    public interface MavenTaskHandler {
        void waitFor();
    }

    public static int crcWithoutSpaces(@Nonnull InputStream in) throws IOException {
        try {
            final CRC32 crc = new CRC32();

            SAXParser parser = SAXParserFactory.newInstance().newSAXParser();

            parser.parse(in, new DefaultHandler() {

                boolean textContentOccur = false;
                int spacesCrc;

                private void putString(@Nullable String string) {
                    if (string == null) {
                        return;
                    }

                    for (int i = 0, end = string.length(); i < end; i++) {
                        crc.update(string.charAt(i));
                    }
                }

                @Override
                public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
                    textContentOccur = false;

                    crc.update(1);
                    putString(qName);

                    for (int i = 0; i < attributes.getLength(); i++) {
                        putString(attributes.getQName(i));
                        putString(attributes.getValue(i));
                    }
                }

                @Override
                public void endElement(String uri, String localName, String qName) throws SAXException {
                    textContentOccur = false;

                    crc.update(2);
                    putString(qName);
                }

                private void processTextOrSpaces(char[] ch, int start, int length) {
                    for (int i = start, end = start + length; i < end; i++) {
                        char a = ch[i];

                        if (Character.isWhitespace(a)) {
                            if (textContentOccur) {
                                spacesCrc = spacesCrc * 31 + a;
                            }
                        }
                        else {
                            if (textContentOccur && spacesCrc != 0) {
                                crc.update(spacesCrc);
                                crc.update(spacesCrc >> 8);
                            }

                            crc.update(a);

                            textContentOccur = true;
                            spacesCrc = 0;
                        }
                    }
                }

                @Override
                public void characters(char[] ch, int start, int length) throws SAXException {
                    processTextOrSpaces(ch, start, length);
                }

                @Override
                public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {
                    processTextOrSpaces(ch, start, length);
                }

                @Override
                public void processingInstruction(String target, String data) throws SAXException {
                    putString(target);
                    putString(data);
                }

                @Override
                public void skippedEntity(String name) throws SAXException {
                    putString(name);
                }

                @Override
                public void error(SAXParseException e) throws SAXException {
                    crc.update(100);
                }
            });

            return (int)crc.getValue();
        }
        catch (ParserConfigurationException e) {
            throw new RuntimeException(e);
        }
        catch (SAXException e) {
            return -1;
        }
    }

    public static int crcWithoutSpaces(@Nonnull VirtualFile xmlFile) throws IOException {
        try (InputStream inputStream = xmlFile.getInputStream()) {
            return crcWithoutSpaces(inputStream);
        }
    }

    public static String getSdkPath(@Nullable Sdk sdk) {
        if (sdk == null) {
            return null;
        }

        VirtualFile homeDirectory = sdk.getHomeDirectory();
        if (homeDirectory == null) {
            return null;
        }

        if (!"jre".equals(homeDirectory.getName())) {
            VirtualFile jreDir = homeDirectory.findChild("jre");
            if (jreDir != null) {
                homeDirectory = jreDir;
            }
        }

        return homeDirectory.getPath();
    }

    @Nullable
    public static String getModuleJreHome(@Nonnull MavenProjectsManager mavenProjectsManager, @Nonnull MavenProject mavenProject) {
        return getSdkPath(getModuleJdk(mavenProjectsManager, mavenProject));
    }

    @Nullable
    public static String getModuleJavaVersion(@Nonnull MavenProjectsManager mavenProjectsManager, @Nonnull MavenProject mavenProject) {
        Sdk sdk = getModuleJdk(mavenProjectsManager, mavenProject);
        if (sdk == null) {
            return null;
        }

        return sdk.getVersionString();
    }

    @Nullable
    public static Sdk getModuleJdk(@Nonnull MavenProjectsManager mavenProjectsManager, @Nonnull MavenProject mavenProject) {
        Module module = mavenProjectsManager.findModule(mavenProject);
        if (module == null) {
            return null;
        }

        return ModuleUtilCore.getSdk(module, JavaModuleExtension.class);
    }

    @Nonnull
    @SuppressWarnings("unchecked")
    public static <K, V extends Map> V getOrCreate(Map map, K key) {
        Map res = (Map)map.get(key);
        if (res == null) {
            res = new HashMap();
            map.put(key, res);
        }

        return (V)res;
    }

    public static String getArtifactName(String packaging, Module module, boolean exploded) {
        return module.getName() + ":" + packaging + (exploded ? " exploded" : "");
    }

    public static String getEjbClientArtifactName(Module module) {
        return module.getName() + ":ejb-client";
    }
}
