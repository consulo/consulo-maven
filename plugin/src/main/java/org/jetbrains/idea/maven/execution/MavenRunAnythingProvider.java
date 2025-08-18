package org.jetbrains.idea.maven.execution;

import consulo.annotation.component.ExtensionImpl;
import consulo.dataContext.DataContext;
import consulo.ide.runAnything.*;
import consulo.maven.icon.MavenIconGroup;
import consulo.maven.rt.server.common.model.MavenConstants;
import consulo.maven.rt.server.common.model.MavenExplicitProfiles;
import consulo.maven.rt.server.common.model.MavenId;
import consulo.project.Project;
import consulo.ui.image.Image;
import jakarta.annotation.Nonnull;
import org.jetbrains.idea.maven.localize.MavenRunnerLocalize;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenArtifactUtil;

import java.util.*;
import java.util.stream.Collectors;

@ExtensionImpl
public class MavenRunAnythingProvider extends RunAnythingCommandLineProvider {

    public static final String HELP_COMMAND = "mvn";

    @Override
    public String getHelpCommand() {
        return HELP_COMMAND;
    }

    @Override
    public Image getHelpIcon() {
        return MavenIconGroup.mavenlogo();
    }

    @Override
    public String getHelpGroupTitle() {
        return "Maven";
    }

    @Override
    public Image getIcon(String value) {
        return MavenIconGroup.mavenlogo();
    }

    @Override
    public RunAnythingGroup getCompletionGroup() {
        return new RunAnythingCompletionGroup<>(this, MavenRunnerLocalize.popupTitleMavenGoals());
    }

    @Override
    public String getHelpCommandPlaceholder() {
        return "mvn <goals...> <options...>";
    }

    @Nonnull
    @Override
    public RunAnythingMavenItem getMainListItem(DataContext dataContext, String value) {
        return new RunAnythingMavenItem(getCommand(value), getIcon(value));
    }

    @Override
    public Iterable<String> suggestCompletionVariants(DataContext dataContext, CommandLine commandLine) {
        List<String> basicPhasesVariants = sortList(completeBasicPhases(commandLine));
        List<String> customGoalsVariants = sortList(completeCustomGoals(dataContext, commandLine));
        List<String> longOptionsVariants = sortList(completeOptions(commandLine, true));
        List<String> shortOptionsVariants = sortList(completeOptions(commandLine, false));

        String toComplete = commandLine.toComplete;
        List<String> result = new ArrayList<>();
        if (toComplete.startsWith("--")) {
            result.addAll(longOptionsVariants);
            result.addAll(shortOptionsVariants);
            result.addAll(basicPhasesVariants);
            result.addAll(customGoalsVariants);
        }
        else if (toComplete.startsWith("-")) {
            result.addAll(shortOptionsVariants);
            result.addAll(longOptionsVariants);
            result.addAll(basicPhasesVariants);
            result.addAll(customGoalsVariants);
        }
        else {
            result.addAll(basicPhasesVariants);
            result.addAll(customGoalsVariants);
            result.addAll(longOptionsVariants);
            result.addAll(shortOptionsVariants);
        }
        return result;
    }

    @Override
    public boolean run(DataContext dataContext, CommandLine commandLine) {
        Project project = dataContext.getRequiredData(Project.KEY);
        RunAnythingContext context = dataContext.getData(RunAnythingProvider.EXECUTING_CONTEXT);
        if (context == null) {
            context = new RunAnythingContext.ProjectContext(project);
        }
        MavenProjectsManager projectsManager = MavenProjectsManager.getInstance(project);
        MavenProject mavenProject = getMavenProject(context, projectsManager);
        String workingDirPath = (mavenProject != null) ? mavenProject.getDirectory() : context.getPath();
        if (workingDirPath == null) {
            return false;
        }

        MavenExplicitProfiles explicitProfiles = projectsManager.getExplicitProfiles();
        Collection<String> enabledProfiles = explicitProfiles.getEnabledProfiles();
        Collection<String> disabledProfiles = explicitProfiles.getDisabledProfiles();
        List<String> goals = commandLine.parameters;

        MavenRunnerParameters params = new MavenRunnerParameters(
            true,
            workingDirPath,
            goals,
            enabledProfiles,
            disabledProfiles
        );
        MavenRunner mavenRunner = MavenRunner.getInstance(project);
        mavenRunner.run(params, mavenRunner.getSettings(), null);
        return true;
    }

    /**
     * Returns the Maven project based on the given RunAnythingContext.
     */
    private static MavenProject getMavenProject(RunAnythingContext context, MavenProjectsManager projectsManager) {
        if (context instanceof RunAnythingContext.ProjectContext) {
            List<MavenProject> rootProjects = projectsManager.getRootProjects();
            return (rootProjects != null && !rootProjects.isEmpty()) ? rootProjects.get(0) : null;
        }
        else if (context instanceof RunAnythingContext.ModuleContext) {
            RunAnythingContext.ModuleContext moduleContext = (RunAnythingContext.ModuleContext) context;
            return projectsManager.findProject(moduleContext.getModule());
        }
        else if (context instanceof RunAnythingContext.RecentDirectoryContext) {
            return null;
        }
        else if (context instanceof RunAnythingContext.BrowseRecentDirectoryContext) {
            return null;
        }
        return null;
    }

    private List<String> completeOptions(CommandLine commandLine, boolean isLongOpt) {
        return MavenCommandLineOptions.getAllOptions().stream()
            .map(option -> option.getName(isLongOpt))
            .filter(Objects::nonNull)
            .filter(name -> !commandLine.contains(name))
            .collect(Collectors.toList());
    }

    private List<String> completeBasicPhases(CommandLine commandLine) {
        return MavenConstants.BASIC_PHASES.stream()
            .filter(phase -> !commandLine.contains(phase))
            .collect(Collectors.toList());
    }

    private List<String> completeCustomGoals(DataContext dataContext, CommandLine commandLine) {
        Project project = dataContext.getRequiredData(Project.KEY);
        RunAnythingContext context = dataContext.getData(RunAnythingProvider.EXECUTING_CONTEXT);
        if (context == null) {
            context = new RunAnythingContext.ProjectContext(project);
        }
        MavenProjectsManager projectsManager = MavenProjectsManager.getInstance(project);
        if (!projectsManager.isMavenizedProject()) {
            return Collections.emptyList();
        }
        MavenProject mavenProject = getMavenProject(context, projectsManager);
        if (mavenProject == null) {
            return Collections.emptyList();
        }
        return mavenProject.getDeclaredPlugins().stream()
            .map(pluginInfoHolder -> MavenArtifactUtil.readPluginInfo(projectsManager.getLocalRepository(), new MavenId(pluginInfoHolder.getGroupId(), pluginInfoHolder.getArtifactId())))
            .filter(Objects::nonNull)
            .flatMap(pluginInfo -> pluginInfo.getMojos().stream())
            .map(mojo -> mojo.getDisplayName())
            .filter(displayName -> !commandLine.contains(displayName))
            .collect(Collectors.toList());
    }

    private List<String> sortList(List<String> list) {
        Collections.sort(list);
        return list;
    }
}
