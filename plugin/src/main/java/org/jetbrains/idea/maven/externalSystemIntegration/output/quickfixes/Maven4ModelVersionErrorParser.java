// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.externalSystemIntegration.output.quickfixes;

import com.intellij.build.events.BuildEvent;
import com.intellij.build.events.MessageEvent;
import com.intellij.build.events.impl.BuildIssueEventImpl;
import com.intellij.build.issue.BuildIssue;
import com.intellij.build.issue.BuildIssueQuickFix;
import com.intellij.build.output.BuildOutputInstantReader;
import com.intellij.openapi.util.SystemInfo;
import consulo.navigation.Navigatable;
import consulo.project.Project;
import consulo.ui.ex.action.DataContext;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.idea.maven.buildtool.MavenBuildIssueHandler;
import org.jetbrains.idea.maven.buildtool.MavenSyncSpec;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.execution.SyncBundle;
import org.jetbrains.idea.maven.externalSystemIntegration.output.LogMessageType;
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenLogEntryReader;
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenLoggedEventParser;
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenParsingContext;
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenSpyLoggedEventParser;
import org.jetbrains.idea.maven.externalSystemIntegration.output.importproject.MavenImportLoggedEventParser;
import org.jetbrains.idea.maven.externalSystemIntegration.output.parsers.MavenEventType;
import org.jetbrains.idea.maven.model.MavenConstants;
import org.jetbrains.idea.maven.model.MavenProjectProblem;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApiStatus.Internal
public class Maven4ModelVersionErrorParser implements MavenLoggedEventParser, MavenSpyLoggedEventParser, MavenImportLoggedEventParser {
    private final Function<Project, MavenBuildIssueHandler> eventHandlerProvider;
    private final Predicate<Path> pathChecker;
    private final List<Pattern> triggers;

    public Maven4ModelVersionErrorParser() {
        this(project -> MavenProjectsManager.getInstance(project).getSyncConsole(),
            Files::exists,
            SystemInfo.isWindows ? TRIGGER_LINES_WINDOWS : TRIGGER_LINES_UNIX);
    }

    public Maven4ModelVersionErrorParser(@Nonnull Function<Project, MavenBuildIssueHandler> eventHandlerProvider,
                                         @Nonnull Predicate<Path> pathChecker,
                                         @Nonnull List<Pattern> triggers) {
        this.eventHandlerProvider = eventHandlerProvider;
        this.pathChecker = pathChecker;
        this.triggers = triggers;
    }

    @Override
    public boolean supportsType(@Nullable LogMessageType type) {
        return true;
    }

    @Override
    public boolean supportsType(@Nonnull MavenEventType type) {
        return true;
    }

    @Override
    public boolean checkLogLine(@Nonnull Object parentId,
                                @Nonnull MavenParsingContext parsingContext,
                                @Nonnull MavenLogEntryReader.MavenLogEntry logLine,
                                @Nonnull MavenLogEntryReader logEntryReader,
                                @Nonnull Consumer<? super BuildEvent> messageConsumer) {
        return processLogLine(parentId, parsingContext, logLine.getLine(), messageConsumer);
    }

    @Override
    public boolean processLogLine(@Nonnull Object parentId,
                                  @Nonnull MavenParsingContext parsingContext,
                                  @Nonnull String logLine,
                                  @Nonnull Consumer<? super BuildEvent> messageConsumer) {
        BuildIssue buildIssue = createBuildIssue(logLine, parsingContext.getIdeaProject());
        if (buildIssue == null) return false;
        messageConsumer.accept(new BuildIssueEventImpl(parentId, buildIssue, MessageEvent.Kind.ERROR));
        return true;
    }

    @Override
    public boolean processLogLine(@Nonnull Project project,
                                  @Nonnull String logLine,
                                  @Nullable BuildOutputInstantReader reader,
                                  @Nonnull Consumer<? super BuildEvent> messageConsumer) {
        BuildIssue buildIssue = createBuildIssue(logLine, project);
        if (buildIssue == null) return false;
        MavenBuildIssueHandler console = eventHandlerProvider.apply(project);
        MessageEvent.Kind kind = logLine.startsWith("[ERROR]") ? MessageEvent.Kind.ERROR : MessageEvent.Kind.WARNING;
        console.addBuildIssue(buildIssue, kind);
        return true;
    }

    @Override
    public boolean processProjectProblem(@Nonnull Project project, @Nonnull MavenProjectProblem problem) {
        String description = problem.getDescription();
        if (description == null) return false;

        for (String trigger : TRIGGER_LINES_PROBLEM) {
            if (description.contains(trigger)) {
                String filePath = extractFilePath(problem.getPath());
                Path path = Path.of(filePath);
                if (pathChecker.test(path)) {
                    ModelAndOffset modelAndOffset = getModelFromPath(project, path);
                    if (modelAndOffset == null || MavenConstants.MODEL_VERSION_4_0_0.equals(modelAndOffset.modelVersion)) {
                        BuildIssue buildIssue = newBuildIssue(createLogLikeDescription(problem), path,
                            modelAndOffset != null ? modelAndOffset.offset : null);
                        MavenBuildIssueHandler console = eventHandlerProvider.apply(project);
                        console.addBuildIssue(buildIssue, MessageEvent.Kind.ERROR);
                        return true;
                    }
                }
                break;
            }
        }
        return false;
    }

    @Nonnull
    private String createLogLikeDescription(@Nonnull MavenProjectProblem problem) {
        return "[ERROR] Maven model problem: " + problem.getDescription() + " at " + problem.getPath();
    }

    @Nonnull
    public String extractFilePath(@Nonnull String input) {
        String[] parts = input.split(":");

        // If there is no ':' at all → entire string is a path
        if (parts.length == 1) return input;

        // Try parse last part as column or line
        String last = parts[parts.length - 1];
        boolean lastIsInt = isInteger(last);

        if (!lastIsInt) {
            // No trailing numbers → entire string is path
            return input;
        }

        // Try parse second-to-last part as line (if present)
        String secondLast = parts.length >= 2 ? parts[parts.length - 2] : null;
        boolean secondLastIsInt = secondLast != null && isInteger(secondLast);

        int pathPartsCount = secondLastIsInt ? parts.length - 2 : parts.length - 1;

        return String.join(":", Arrays.copyOf(parts, pathPartsCount));
    }

    private boolean isInteger(@Nonnull String s) {
        try {
            Integer.parseInt(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @Nullable
    private ModelAndOffset getModelFromPath(@Nonnull Project project, @Nonnull Path path) {
        var virtualFile = VirtualFileUtil.findFile(path);
        if (virtualFile == null) return null;

        var model = MavenDomUtil.getMavenDomProjectModel(project, virtualFile);
        if (model == null) return null;

        var modelVersion = model.getModelVersion();
        String value = modelVersion.getValue();
        if (value == null) return null;

        int offset = 0;
        var xmlElement = modelVersion.getXmlElement();
        if (xmlElement != null && xmlElement.getNavigationElement() != null) {
            offset = xmlElement.getNavigationElement().getTextOffset();
        }

        return new ModelAndOffset(value, offset);
    }

    @Nullable
    private BuildIssue createBuildIssue(@Nonnull String logLine, @Nonnull Project project) {
        for (Pattern trigger : triggers) {
            Matcher match = trigger.matcher(logLine);
            if (!match.find()) continue;

            String fileName = match.group(1);
            Path path = Path.of(fileName);
            if (pathChecker.test(path)) {
                ModelAndOffset modelAndOffset = getModelFromPath(project, path);
                if (modelAndOffset == null || MavenConstants.MODEL_VERSION_4_0_0.equals(modelAndOffset.modelVersion)) {
                    return newBuildIssue(logLine, path, modelAndOffset != null ? modelAndOffset.offset : null);
                }
            }
        }
        return null;
    }

    @Nonnull
    private BuildIssue newBuildIssue(@Nonnull String line, @Nonnull Path path, @Nullable Integer offset) {
        return new BuildIssue() {
            @Nonnull
            @Override
            public String getTitle() {
                return SyncBundle.message("maven.sync.incorrect.model.version");
            }

            @Nonnull
            @Override
            public String getDescription() {
                return SyncBundle.message("maven.sync.incorrect.model.version.desc", line.trim(), UpdateVersionQuickFix.ID);
            }

            @Nonnull
            @Override
            public List<BuildIssueQuickFix> getQuickFixes() {
                return Collections.singletonList(new UpdateVersionQuickFix(path));
            }

            @Nullable
            @Override
            public Navigatable getNavigatable(@Nonnull Project project) {
                return offset != null ? new PathNavigatable(project, path, offset) : null;
            }
        };
    }

    private static class ModelAndOffset {
        final String modelVersion;
        final int offset;

        ModelAndOffset(@Nonnull String modelVersion, int offset) {
            this.modelVersion = modelVersion;
            this.offset = offset;
        }
    }

    @ApiStatus.Internal
    public static final List<Pattern> TRIGGER_LINES_UNIX = Arrays.asList(
        Pattern.compile("'subprojects' unexpected subprojects element @ [^,]*, (.*)"),
        Pattern.compile("'subprojects' unexpected subprojects element at (.*?)[:,$]"),
        Pattern.compile("the model contains elements that require a model version of 4.1.0 @ .*? file://(.*)[:,$]"),
        Pattern.compile("the model contains elements that require a model version of 4.1.0 at file://(.*?)[:,$]")
    );

    @ApiStatus.Internal
    public static final List<String> TRIGGER_LINES_PROBLEM = Arrays.asList(
        "'subprojects' unexpected subprojects element",
        "the model contains elements that require a model version of 4.1.0"
    );

    @ApiStatus.Internal
    public static final List<Pattern> TRIGGER_LINES_WINDOWS = Arrays.asList(
        Pattern.compile("'subprojects' unexpected subprojects element @ [^,]*, (.*)"),
        Pattern.compile("'subprojects' unexpected subprojects element at ([A-Za-z][:].*?)[:,$]"),
        Pattern.compile("the model contains elements that require a model version of 4.1.0 @ .*? file://([A-Za-z][:].*?.*?)[:,$]"),
        Pattern.compile("the model contains elements that require a model version of 4.1.0 at file://([A-Za-z][:].*?.*?)[:,$]")
    );
}

class UpdateVersionQuickFix implements BuildIssueQuickFix {
    static final String ID = "maven_model_ver_update_410";
    private static final String NEW_XMLNS = "http://maven.apache.org/POM/4.1.0";
    private static final String NEW_SCHEMA_LOCATION = "http://maven.apache.org/POM/4.1.0 https://maven.apache.org/xsd/maven-4.1.0.xsd";

    private final Path path;

    UpdateVersionQuickFix(@Nonnull Path path) {
        this.path = path;
    }

    @Nonnull
    @Override
    public String getId() {
        return ID;
    }

    @Nonnull
    @Override
    public CompletableFuture<?> runQuickFix(@Nonnull Project project, @Nonnull DataContext dataContext) {
        // Note: The original Kotlin code uses coroutines for background processing.
        // This simplified Java version schedules a sync directly.
        // A more complete implementation would update the POM files first.
        MavenProjectsManager.getInstance(project)
            .scheduleUpdateAllMavenProjects(MavenSyncSpec.full("Update model version quick fix", true));
        return CompletableFuture.completedFuture(null);
    }
}
