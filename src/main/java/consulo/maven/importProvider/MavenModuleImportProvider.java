package consulo.maven.importProvider;

import java.io.File;
import java.util.Collection;
import java.util.List;

import javax.swing.Icon;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.importing.MavenDefaultModifiableModelsProvider;
import org.jetbrains.idea.maven.importing.MavenUIModifiableModelsProvider;
import org.jetbrains.idea.maven.model.MavenConstants;
import org.jetbrains.idea.maven.model.MavenExplicitProfiles;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.project.MavenWorkspaceSettings;
import org.jetbrains.idea.maven.project.MavenWorkspaceSettingsComponent;
import org.jetbrains.idea.maven.project.ProjectBundle;
import org.jetbrains.idea.maven.utils.MavenUtil;
import org.jetbrains.idea.maven.wizards.MavenProjectImportStep;
import org.jetbrains.idea.maven.wizards.ProjectNameStep;
import org.jetbrains.idea.maven.wizards.SelectImportedProjectsStep;
import org.jetbrains.idea.maven.wizards.SelectProfilesStep;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.ModifiableArtifactModel;
import consulo.moduleImport.ModuleImportProvider;
import icons.MavenIcons;

/**
 * @author VISTALL
 * @since 31-Jan-17
 */
public class MavenModuleImportProvider implements ModuleImportProvider<MavenImportModuleContext>
{
	@NotNull
	@Override
	public MavenImportModuleContext createContext()
	{
		return new MavenImportModuleContext();
	}

	@NotNull
	@Override
	public String getName()
	{
		return ProjectBundle.message("maven.name");
	}

	@Nullable
	@Override
	public Icon getIcon()
	{
		return MavenIcons.MavenLogo;
	}

	@NotNull
	@Override
	public String getFileSample()
	{
		return "<b>Maven</b> project file (pom.xml)";
	}

	@Override
	public boolean canImport(@NotNull VirtualFile fileOrDirectory)
	{
		return MavenConstants.POM_XML.equals(fileOrDirectory.getName());
	}

	@Override
	public ModuleWizardStep[] createSteps(@NotNull WizardContext wizardContext, @NotNull MavenImportModuleContext context)
	{
		return new ModuleWizardStep[]{
				new MavenProjectImportStep(context, wizardContext),
				new SelectProfilesStep(context, wizardContext),
				new SelectImportedProjectsStep(context, wizardContext)
				{
					@Override
					protected String getElementText(final MavenProject project)
					{
						final StringBuilder stringBuilder = new StringBuilder();
						stringBuilder.append(project.getMavenId());
						VirtualFile root = context.getRootDirectory();
						if(root != null)
						{
							final String relPath = VfsUtilCore.getRelativePath(project.getDirectoryFile(), root, File.separatorChar);
							if(StringUtil.isNotEmpty(relPath))
							{
								stringBuilder.append(" [").append(relPath).append("]");
							}
						}

						if(!isElementEnabled(project))
						{
							stringBuilder.append(" (project is ignored. See Settings -> Maven -> Ignored Files)");
						}

						return stringBuilder.toString();
					}

					@Override
					protected boolean isElementEnabled(MavenProject mavenProject)
					{
						Project project = wizardContext.getProject();
						if(project == null)
						{
							return true;
						}

						return !MavenProjectsManager.getInstance(project).isIgnored(mavenProject);
					}

					@Override
					public void updateDataModel()
					{
						super.updateDataModel();
						getWizardContext().setProjectName(context.getSuggestedProjectName());
					}

					@Override
					public String getHelpId()
					{
						return "reference.dialogs.new.project.import.maven.page3";
					}
				},
				new ProjectNameStep(context, wizardContext)
		};
	}

	@NotNull
	@Override
	public List<Module> commit(@NotNull MavenImportModuleContext context,
			@NotNull Project project,
			@Nullable ModifiableModuleModel model,
			@NotNull ModulesProvider modulesProvider,
			@Nullable ModifiableArtifactModel artifactModel)
	{
		MavenWorkspaceSettings settings = MavenWorkspaceSettingsComponent.getInstance(project).getSettings();

		settings.generalSettings = context.getGeneralSettings();
		settings.importingSettings = context.getImportingSettings();

		String settingsFile = System.getProperty("idea.maven.import.settings.file");
		if(!StringUtil.isEmptyOrSpaces(settingsFile))
		{
			settings.generalSettings.setUserSettingsFile(settingsFile.trim());
		}

		MavenExplicitProfiles selectedProfiles = context.getSelectedProfiles();

		String enabledProfilesList = System.getProperty("idea.maven.import.enabled.profiles");
		String disabledProfilesList = System.getProperty("idea.maven.import.disabled.profiles");
		if(enabledProfilesList != null || disabledProfilesList != null)
		{
			selectedProfiles = selectedProfiles.clone();
			appendProfilesFromString(selectedProfiles.getEnabledProfiles(), enabledProfilesList);
			appendProfilesFromString(selectedProfiles.getDisabledProfiles(), disabledProfilesList);
		}

		MavenProjectsManager manager = MavenProjectsManager.getInstance(project);
		manager.addManagedFilesWithProfiles(MavenUtil.collectFiles(context.mySelectedProjects), selectedProfiles);
		manager.waitForReadingCompletion();

		boolean isFromUI = model != null;
		return manager.importProjects(isFromUI ? new MavenUIModifiableModelsProvider(project, model, (ModulesConfigurator) modulesProvider, artifactModel) : new MavenDefaultModifiableModelsProvider
				(project));
	}


	private void appendProfilesFromString(Collection<String> selectedProfiles, String profilesList)
	{
		if(profilesList == null)
		{
			return;
		}

		for(String profile : StringUtil.split(profilesList, ","))
		{
			String trimmedProfileName = profile.trim();
			if(!trimmedProfileName.isEmpty())
			{
				selectedProfiles.add(trimmedProfileName);
			}
		}
	}
}
