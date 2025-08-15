package consulo.maven.importProvider;

import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.ide.moduleImport.ModuleImportProvider;
import consulo.ide.newModule.ui.UnifiedProjectOrModuleNameStep;
import consulo.localize.LocalizeValue;
import consulo.maven.rt.server.common.model.MavenConstants;
import consulo.maven.rt.server.common.model.MavenExplicitProfiles;
import consulo.module.ModifiableModuleModel;
import consulo.module.Module;
import consulo.project.Project;
import consulo.ui.ex.wizard.WizardStep;
import consulo.ui.image.Image;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.idea.maven.MavenIcons;
import org.jetbrains.idea.maven.importing.MavenDefaultModifiableModelsProvider;
import org.jetbrains.idea.maven.localize.MavenProjectLocalize;
import org.jetbrains.idea.maven.project.*;
import org.jetbrains.idea.maven.utils.MavenUtil;
import org.jetbrains.idea.maven.wizards.MavenProjectImportStep;
import org.jetbrains.idea.maven.wizards.SelectImportedProjectsStep;
import org.jetbrains.idea.maven.wizards.SelectProfilesStep;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

/**
 * @author VISTALL
 * @since 31-Jan-17
 */
@ExtensionImpl
public class MavenModuleImportProvider implements ModuleImportProvider<MavenImportModuleContext> {
    @Nonnull
    @Override
    public MavenImportModuleContext createContext(@Nullable Project project) {
        return new MavenImportModuleContext(project);
    }

    @Nonnull
    @Override
    public LocalizeValue getName() {
        return MavenProjectLocalize.mavenName();
    }

    @Nonnull
    @Override
    public Image getIcon() {
        return MavenIcons.MavenLogo;
    }

    @Override
    public boolean canImport(@Nonnull File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            return new File(fileOrDirectory, MavenConstants.POM_XML).exists();
        }
        else {
            return MavenConstants.POM_XML.equals(fileOrDirectory.getName());
        }
    }

    @Override
    public void buildSteps(@Nonnull Consumer<WizardStep<MavenImportModuleContext>> consumer, @Nonnull MavenImportModuleContext context) {
        consumer.accept(new MavenProjectImportStep(context));
        consumer.accept(new SelectProfilesStep(context));
        consumer.accept(new SelectImportedProjectsStep(context) {
            @Override
            protected String getElementText(final MavenProject project) {
                final StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append(project.getMavenId());
                VirtualFile root = context.getRootDirectory();
                if (root != null) {
                    final String relPath = VirtualFileUtil.getRelativePath(project.getDirectoryFile(), root, File.separatorChar);
                    if (StringUtil.isNotEmpty(relPath)) {
                        stringBuilder.append(" [").append(relPath).append("]");
                    }
                }

                if (!isElementEnabled(project)) {
                    stringBuilder.append(" (project is ignored. See Settings -> Maven -> Ignored Files)");
                }

                return stringBuilder.toString();
            }

            @Override
            protected boolean isElementEnabled(MavenProject mavenProject) {
                Project project = myContext.getProject();
                if (project == null) {
                    return true;
                }

                return !MavenProjectsManager.getInstance(project).isIgnored(mavenProject);
            }

            @Override
            public void updateDataModel() {
                super.updateDataModel();
                String suggestedProjectName = context.getSuggestedProjectName();
                if (suggestedProjectName != null) {
                    myContext.setName(suggestedProjectName);
                }
            }
        });
        consumer.accept(new UnifiedProjectOrModuleNameStep<>(context));
    }

    @RequiredReadAction
    @Override
    public void process(@Nonnull MavenImportModuleContext context, @Nonnull Project project, @Nonnull ModifiableModuleModel modifiableModuleModel, @Nonnull Consumer<Module> consumer) {
        MavenWorkspaceSettings settings = MavenWorkspaceSettingsComponent.getInstance(project).getSettings();

        settings.generalSettings = context.getGeneralSettings();
        settings.importingSettings = context.getImportingSettings();

        String settingsFile = System.getProperty("idea.maven.import.settings.file");
        if (!StringUtil.isEmptyOrSpaces(settingsFile)) {
            settings.generalSettings.setUserSettingsFile(settingsFile.trim());
        }

        MavenExplicitProfiles selectedProfiles = context.getSelectedProfiles();

        String enabledProfilesList = System.getProperty("idea.maven.import.enabled.profiles");
        String disabledProfilesList = System.getProperty("idea.maven.import.disabled.profiles");
        if (enabledProfilesList != null || disabledProfilesList != null) {
            selectedProfiles = selectedProfiles.clone();
            appendProfilesFromString(selectedProfiles.getEnabledProfiles(), enabledProfilesList);
            appendProfilesFromString(selectedProfiles.getDisabledProfiles(), disabledProfilesList);
        }

        MavenProjectsManager manager = MavenProjectsManager.getInstance(project);
        manager.addManagedFilesWithProfiles(MavenUtil.collectFiles(context.mySelectedProjects), selectedProfiles);
        manager.waitForReadingCompletion();

        List<Module> modules = manager.importProjects(new MavenDefaultModifiableModelsProvider(project));
        for (Module module : modules) {
            consumer.accept(module);
        }
    }

    private void appendProfilesFromString(Collection<String> selectedProfiles, String profilesList) {
        if (profilesList == null) {
            return;
        }

        for (String profile : StringUtil.split(profilesList, ",")) {
            String trimmedProfileName = profile.trim();
            if (!trimmedProfileName.isEmpty()) {
                selectedProfiles.add(trimmedProfileName);
            }
        }
    }
}
