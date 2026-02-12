// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.output.quickfixes;

import com.intellij.build.events.MessageEvent;
import com.intellij.build.issue.BuildIssue;
import com.intellij.build.issue.BuildIssueQuickFix;
import com.intellij.compiler.progress.BuildIssueContributor;
import com.intellij.openapi.roots.ui.configuration.ClasspathEditor;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import com.intellij.pom.java.LanguageLevel;
import consulo.content.bundle.Sdk;
import consulo.module.Module;
import consulo.module.ModuleManager;
import consulo.module.content.ModuleRootManager;
import consulo.navigation.Navigatable;
import consulo.project.Project;
import consulo.ui.ex.action.DataContext;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectBundle;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class JpsLanguageLevelQuickFix implements BuildIssueContributor {
    private static final List<List<String>> MATCHERS_LIST = Arrays.asList(
        Arrays.asList("source release", "requires target release"),
        Arrays.asList("release version", "not supported"),
        Arrays.asList("invalid source release:"),
        Arrays.asList("invalid target release")
    );

    @Nullable
    @Override
    public BuildIssue createBuildIssue(@Nonnull Project project,
                                       @Nonnull Collection<String> moduleNames,
                                       @Nonnull String title,
                                       @Nonnull String message,
                                       @Nonnull MessageEvent.Kind kind,
                                       @Nullable VirtualFile virtualFile,
                                       @Nullable Navigatable navigatable) {
        if (project.isDisposed()) return null;
        MavenProjectsManager mavenManager = MavenProjectsManager.getInstance(project);
        if (!mavenManager.isMavenizedProject()) return null;

        if (moduleNames.size() != 1) {
            return null;
        }
        String moduleName = moduleNames.iterator().next();
        Module module = ModuleManager.getInstance(project).findModuleByName(moduleName);
        if (module == null) return null;

        MavenProject mavenProject = mavenManager.findProject(module);
        if (mavenProject == null) return null;

        ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
        Sdk moduleJdk = moduleRootManager.getSdk();

        LanguageLevel moduleProjectLanguageLevel = moduleJdk != null ? LanguageLevel.parse(moduleJdk.getVersionString()) : null;
        if (moduleProjectLanguageLevel == null) return null;

        LanguageLevel sourceLanguageLevel = getLanguageLevelFromError(message);
        if (sourceLanguageLevel == null) return null;

        if (sourceLanguageLevel.isLessThan(moduleProjectLanguageLevel)) {
            return getBuildIssueSourceVersionLess(sourceLanguageLevel, moduleProjectLanguageLevel, message, mavenProject, moduleRootManager);
        } else {
            return getBuildIssueSourceVersionGreat(sourceLanguageLevel, moduleProjectLanguageLevel, message, moduleRootManager);
        }
    }

    @Nullable
    public LanguageLevel getLanguageLevelFromError(@Nonnull String message) {
        String targetMessage = null;
        for (List<String> matchers : MATCHERS_LIST) {
            boolean allMatch = true;
            for (String matcher : matchers) {
                if (!message.contains(matcher)) {
                    allMatch = false;
                    break;
                }
            }
            if (allMatch) {
                targetMessage = message.substring(message.indexOf(matchers.get(0)));
                break;
            }
        }
        if (targetMessage == null) return null;

        String cleaned = targetMessage.replaceAll("[^.0123456789]", " ").trim();
        String[] parts = cleaned.split(" ");
        if (parts.length > 0 && !parts[0].isEmpty()) {
            return LanguageLevel.parse(parts[0]);
        }
        return null;
    }

    @Nonnull
    private BuildIssue getBuildIssueSourceVersionGreat(@Nonnull LanguageLevel sourceLanguageLevel,
                                                       @Nonnull LanguageLevel moduleProjectLanguageLevel,
                                                       @Nonnull String errorMessage,
                                                       @Nonnull ModuleRootManager moduleRootManager) {
        String moduleName = moduleRootManager.getModule().getName();
        SetupModuleSdkQuickFix setupModuleSdkQuickFix = new SetupModuleSdkQuickFix(moduleName, moduleRootManager.isSdkInherited());
        List<BuildIssueQuickFix> quickFixes = List.of(setupModuleSdkQuickFix);

        String issueDescription = errorMessage + "\n\n" +
            MavenProjectBundle.message("maven.quickfix.source.version.great", moduleName,
                moduleProjectLanguageLevel.toJavaVersion(), sourceLanguageLevel.toJavaVersion(),
                setupModuleSdkQuickFix.getId());

        return new BuildIssue() {
            @Nonnull
            @Override
            public String getTitle() {
                return errorMessage;
            }

            @Nonnull
            @Override
            public String getDescription() {
                return issueDescription;
            }

            @Nonnull
            @Override
            public List<BuildIssueQuickFix> getQuickFixes() {
                return quickFixes;
            }

            @Nullable
            @Override
            public Navigatable getNavigatable(@Nonnull Project project) {
                return null;
            }
        };
    }

    @Nonnull
    private BuildIssue getBuildIssueSourceVersionLess(@Nonnull LanguageLevel sourceLanguageLevel,
                                                      @Nonnull LanguageLevel moduleProjectLanguageLevel,
                                                      @Nonnull String errorMessage,
                                                      @Nonnull MavenProject mavenProject,
                                                      @Nonnull ModuleRootManager moduleRootManager) {
        String moduleName = moduleRootManager.getModule().getName();
        List<BuildIssueQuickFix> quickFixes = new ArrayList<>();

        StringBuilder issueDescription = new StringBuilder(errorMessage);
        issueDescription.append("\n\n");
        issueDescription.append(MavenProjectBundle.message("maven.quickfix.source.version.less.header", moduleName,
            moduleProjectLanguageLevel.toJavaVersion(), sourceLanguageLevel.toJavaVersion()));

        SetupModuleSdkQuickFix setupModuleSdkQuickFix = new SetupModuleSdkQuickFix(moduleName, moduleRootManager.isSdkInherited());
        quickFixes.add(setupModuleSdkQuickFix);
        issueDescription.append("\n");
        issueDescription.append(MavenProjectBundle.message("maven.quickfix.source.version.less.part1",
            sourceLanguageLevel.toJavaVersion(), setupModuleSdkQuickFix.getId()));

        UpdateSourceLevelQuickFix updateSourceLevelQuickFix = new UpdateSourceLevelQuickFix(mavenProject);
        quickFixes.add(updateSourceLevelQuickFix);
        issueDescription.append("\n");
        issueDescription.append(MavenProjectBundle.message("maven.quickfix.source.version.less.part2",
            moduleProjectLanguageLevel.toJavaVersion(), updateSourceLevelQuickFix.getId()));

        String finalDescription = issueDescription.toString();
        return new BuildIssue() {
            @Nonnull
            @Override
            public String getTitle() {
                return errorMessage;
            }

            @Nonnull
            @Override
            public String getDescription() {
                return finalDescription;
            }

            @Nonnull
            @Override
            public List<BuildIssueQuickFix> getQuickFixes() {
                return quickFixes;
            }

            @Nullable
            @Override
            public Navigatable getNavigatable(@Nonnull Project project) {
                return null;
            }
        };
    }
}

class SetupModuleSdkQuickFix implements BuildIssueQuickFix {
    private final String moduleName;
    private final boolean isSdkInherited;

    SetupModuleSdkQuickFix(@Nonnull String moduleName, boolean isSdkInherited) {
        this.moduleName = moduleName;
        this.isSdkInherited = isSdkInherited;
    }

    @Nonnull
    @Override
    public String getId() {
        return "setup_module_sdk_quick_fix";
    }

    @Nonnull
    @Override
    public CompletableFuture<?> runQuickFix(@Nonnull Project project, @Nonnull DataContext dataContext) {
        if (isSdkInherited) {
            ProjectSettingsService.getInstance(project).openProjectSettings();
        } else {
            ProjectSettingsService.getInstance(project).showModuleConfigurationDialog(moduleName, ClasspathEditor.getName());
        }
        return CompletableFuture.completedFuture(null);
    }
}
