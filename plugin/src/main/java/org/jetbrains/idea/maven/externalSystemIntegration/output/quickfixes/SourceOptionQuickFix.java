// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.externalSystemIntegration.output.quickfixes;

import com.intellij.build.events.BuildEvent;
import com.intellij.build.events.MessageEvent;
import com.intellij.build.events.impl.BuildIssueEventImpl;
import com.intellij.build.issue.BuildIssue;
import com.intellij.build.issue.BuildIssueQuickFix;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.pom.java.LanguageLevel;
import consulo.content.bundle.Sdk;
import consulo.module.Module;
import consulo.module.ModuleManager;
import consulo.module.content.ModuleRootManager;
import consulo.navigation.Navigatable;
import consulo.navigation.OpenFileDescriptor;
import consulo.project.Project;
import consulo.ui.ex.action.DataContext;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.idea.maven.externalSystemIntegration.output.LogMessageType;
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenLogEntryReader;
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenLoggedEventParser;
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenParsingContext;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectBundle;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class SourceOptionQuickFix implements MavenLoggedEventParser {
    @Override
    public boolean supportsType(@Nullable LogMessageType type) {
        return type == LogMessageType.ERROR;
    }

    @Override
    public boolean checkLogLine(@Nonnull Object parentId,
                                @Nonnull MavenParsingContext parsingContext,
                                @Nonnull MavenLogEntryReader.MavenLogEntry logLine,
                                @Nonnull MavenLogEntryReader logEntryReader,
                                @Nonnull Consumer<? super BuildEvent> messageConsumer) {
        if (logLine.getLine().startsWith("Source option 5 is no longer supported.")
            || logLine.getLine().startsWith("Source option 1.5 is no longer supported.")) {
            MavenLogEntryReader.MavenLogEntry targetLine = logEntryReader.readLine();

            if (targetLine != null && !targetLine.getLine().startsWith("Target option 1.5 is no longer supported.")) {
                logEntryReader.pushBack();
            }

            List<String> startedProjects = parsingContext.getStartedProjects();
            if (startedProjects.isEmpty()) return false;
            String lastErrorProject = startedProjects.get(startedProjects.size() - 1) + ":";

            String failedProject = null;
            for (String p : parsingContext.getProjectsInReactor()) {
                if (p.startsWith(lastErrorProject)) {
                    failedProject = p;
                    break;
                }
            }
            if (failedProject == null) return false;

            MavenProject mavenProject = MavenProjectsManager.getInstance(parsingContext.getIdeaProject()).findProject(new MavenId(failedProject));
            if (mavenProject == null) return false;

            Sdk moduleJdk = MavenUtil.getModuleJdk(MavenProjectsManager.getInstance(parsingContext.getIdeaProject()), mavenProject);
            if (moduleJdk == null) return false;

            messageConsumer.accept(
                new BuildIssueEventImpl(parentId,
                    new SourceLevelBuildIssue(logLine.getLine(), logLine.getLine(), mavenProject, moduleJdk),
                    MessageEvent.Kind.ERROR));
            return true;
        }

        return false;
    }
}

class SourceLevelBuildIssue implements BuildIssue {
    private final String message;
    private final String title;
    private final MavenProject mavenProject;
    private final Sdk moduleJdk;
    private final List<UpdateSourceLevelQuickFix> quickFixes;
    private final String description;

    SourceLevelBuildIssue(@Nonnull String message,
                          @Nonnull String title,
                          @Nonnull MavenProject mavenProject,
                          @Nonnull Sdk moduleJdk) {
        this.message = message;
        this.title = title;
        this.mavenProject = mavenProject;
        this.moduleJdk = moduleJdk;
        this.quickFixes = Collections.singletonList(new UpdateSourceLevelQuickFix(mavenProject));
        this.description = createDescription();
    }

    private String createDescription() {
        StringBuilder sb = new StringBuilder(message);
        sb.append("\n<br/>");
        for (UpdateSourceLevelQuickFix qf : quickFixes) {
            LanguageLevel level = LanguageLevel.parse(moduleJdk.getVersionString());
            sb.append(MavenProjectBundle.message("maven.source.level.not.supported.update",
                level != null ? level.toJavaVersion() : "unknown",
                qf.getId(), qf.getMavenProject().getDisplayName()));
        }
        return sb.toString();
    }

    @Nonnull
    @Override
    public String getTitle() {
        return title;
    }

    @Nonnull
    @Override
    public String getDescription() {
        return description;
    }

    @Nonnull
    @Override
    public List<UpdateSourceLevelQuickFix> getQuickFixes() {
        return quickFixes;
    }

    @Nonnull
    @Override
    public Navigatable getNavigatable(@Nonnull Project project) {
        return new OpenFileDescriptor(project, mavenProject.getFile());
    }
}

class UpdateSourceLevelQuickFix implements BuildIssueQuickFix {
    public static final String ID = "maven_quickfix_source_level_";

    private final MavenProject mavenProject;

    UpdateSourceLevelQuickFix(@Nonnull MavenProject mavenProject) {
        this.mavenProject = mavenProject;
    }

    @Nonnull
    public MavenProject getMavenProject() {
        return mavenProject;
    }

    @Nonnull
    @Override
    public String getId() {
        return ID + mavenProject.getMavenId().getDisplayString();
    }

    @Nonnull
    @Override
    public CompletableFuture<?> runQuickFix(@Nonnull Project project, @Nonnull DataContext dataContext) {
        LanguageLevelQuickFix languageLevelQuickFix = LanguageLevelQuickFixFactory.getInstance(project, mavenProject);
        return ProcessQuickFix.perform(languageLevelQuickFix, project, mavenProject);
    }
}

class ProcessQuickFix {
    @Nonnull
    static CompletableFuture<?> perform(@Nullable LanguageLevelQuickFix languageLevelQuickFix,
                                        @Nonnull Project project,
                                        @Nonnull MavenProject mavenProject) {
        if (languageLevelQuickFix == null) {
            new Notification(MavenUtil.MAVEN_NOTIFICATION_GROUP, "",
                MavenProjectBundle.message("maven.quickfix.cannot.update.source.level.module.not.found", mavenProject.getDisplayName()),
                NotificationType.INFORMATION).notify(project);
            return CompletableFuture.completedFuture(null);
        }

        Sdk moduleJdk = MavenUtil.getModuleJdk(MavenProjectsManager.getInstance(project), languageLevelQuickFix.getMavenProject());
        if (moduleJdk == null) {
            new Notification(MavenUtil.MAVEN_NOTIFICATION_GROUP, "",
                MavenProjectBundle.message("maven.quickfix.cannot.update.source.level.module.not.found",
                    languageLevelQuickFix.getMavenProject().getDisplayName()),
                NotificationType.INFORMATION).notify(project);
            return CompletableFuture.completedFuture(null);
        }

        LanguageLevel level = LanguageLevel.parse(moduleJdk.getVersionString());
        if (level != null) {
            languageLevelQuickFix.perform(level);
        }
        return CompletableFuture.completedFuture(null);
    }
}

/**
 * @deprecated use {@link JpsLanguageLevelQuickFix}
 */
@Deprecated
class JpsReleaseVersionQuickFix implements com.intellij.compiler.progress.BuildIssueContributor {
    @Nullable
    @Override
    public BuildIssue createBuildIssue(@Nonnull Project project,
                                       @Nonnull java.util.Collection<String> moduleNames,
                                       @Nonnull String title,
                                       @Nonnull String message,
                                       @Nonnull MessageEvent.Kind kind,
                                       @Nullable consulo.virtualFileSystem.VirtualFile virtualFile,
                                       @Nullable Navigatable navigatable) {
        MavenProjectsManager manager = MavenProjectsManager.getInstance(project);
        if (!manager.isMavenizedProject()) return null;

        if (moduleNames.size() != 1) {
            return null;
        }
        String moduleName = moduleNames.iterator().next();
        if (moduleName == null) return null;

        List<java.util.function.Predicate<String>> predicates = CacheForCompilerErrorMessages.getPredicatesToCheck(project, moduleName);
        MavenId failedId = extractFailedMavenId(project, moduleName);
        if (failedId == null) return null;

        MavenProject mavenProject = manager.findProject(failedId);
        if (mavenProject == null) return null;

        Sdk moduleJdk = MavenUtil.getModuleJdk(manager, mavenProject);
        if (moduleJdk == null) return null;

        for (java.util.function.Predicate<String> predicate : predicates) {
            if (predicate.test(message)) {
                return new SourceLevelBuildIssue(title, message, mavenProject, moduleJdk);
            }
        }
        return null;
    }

    @Nullable
    private MavenId extractFailedMavenId(@Nonnull Project project, @Nonnull String moduleName) {
        Module module = ModuleManager.getInstance(project).findModuleByName(moduleName);
        if (module == null) return null;
        MavenProject mavenProject = MavenProjectsManager.getInstance(project).findProject(module);
        return mavenProject != null ? mavenProject.getMavenId() : null;
    }
}
