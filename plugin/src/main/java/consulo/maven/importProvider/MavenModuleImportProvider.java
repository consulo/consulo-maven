package consulo.maven.importProvider;

import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import consulo.annotations.RequiredReadAction;
import consulo.ide.newProject.ui.ProjectOrModuleNameStep;
import consulo.moduleImport.ModuleImportProvider;
import consulo.ui.image.Image;
import consulo.ui.wizard.WizardStep;
import icons.MavenIcons;
import org.jetbrains.idea.maven.importing.MavenDefaultModifiableModelsProvider;
import org.jetbrains.idea.maven.model.MavenConstants;
import org.jetbrains.idea.maven.model.MavenExplicitProfiles;
import org.jetbrains.idea.maven.project.*;
import org.jetbrains.idea.maven.utils.MavenUtil;
import org.jetbrains.idea.maven.wizards.MavenProjectImportStep;
import org.jetbrains.idea.maven.wizards.SelectImportedProjectsStep;
import org.jetbrains.idea.maven.wizards.SelectProfilesStep;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

/**
 * @author VISTALL
 * @since 31-Jan-17
 */
public class MavenModuleImportProvider implements ModuleImportProvider<MavenImportModuleContext>
{
	@Nonnull
	@Override
	public MavenImportModuleContext createContext(@Nullable Project project)
	{
		return new MavenImportModuleContext(project);
	}

	@Nonnull
	@Override
	public String getName()
	{
		return ProjectBundle.message("maven.name");
	}

	@Nonnull
	@Override
	public Image getIcon()
	{
		return MavenIcons.MavenLogo;
	}

	@Nonnull
	@Override
	public String getFileSample()
	{
		return "<b>Maven</b> project file (pom.xml)";
	}

	@Override
	public boolean canImport(@Nonnull File fileOrDirectory)
	{
		if(fileOrDirectory.isDirectory())
		{
			return new File(fileOrDirectory, MavenConstants.POM_XML).exists();
		}
		else
		{
			return MavenConstants.POM_XML.equals(fileOrDirectory.getName());
		}
	}

	@Override
	public void buildSteps(@Nonnull Consumer<WizardStep<MavenImportModuleContext>> consumer, @Nonnull MavenImportModuleContext context)
	{
		consumer.accept(new MavenProjectImportStep(context));
		consumer.accept(new SelectProfilesStep(context));
		consumer.accept(new SelectImportedProjectsStep(context)
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
				Project project = myContext.getProject();
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
				myContext.setName(context.getSuggestedProjectName());
			}
		});
		consumer.accept(new ProjectOrModuleNameStep<>(context));
	}

	@RequiredReadAction
	@Override
	public void process(@Nonnull MavenImportModuleContext context, @Nonnull Project project, @Nonnull ModifiableModuleModel modifiableModuleModel, @Nonnull Consumer<Module> consumer)
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

		boolean isFromUI = false;
		List<Module> modules = manager.importProjects(new MavenDefaultModifiableModelsProvider(project));
		for(Module module : modules)
		{
			consumer.accept(module);
		}
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
