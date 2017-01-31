package consulo.maven.importProvider;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenExplicitProfiles;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.project.MavenGeneralSettings;
import org.jetbrains.idea.maven.project.MavenImportingSettings;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectReader;
import org.jetbrains.idea.maven.project.MavenProjectReaderProjectLocator;
import org.jetbrains.idea.maven.project.MavenProjectsTree;
import org.jetbrains.idea.maven.project.MavenWorkspaceSettings;
import org.jetbrains.idea.maven.project.MavenWorkspaceSettingsComponent;
import org.jetbrains.idea.maven.project.ProjectBundle;
import org.jetbrains.idea.maven.utils.FileFinder;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;
import org.jetbrains.idea.maven.utils.MavenTask;
import org.jetbrains.idea.maven.utils.MavenUtil;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import consulo.annotations.RequiredReadAction;
import consulo.moduleImport.ModuleImportContext;

/**
 * @author VISTALL
 * @since 31-Jan-17
 */
public class MavenImportModuleContext extends ModuleImportContext
{
	protected Project myProjectToUpdate;

	protected MavenGeneralSettings myGeneralSettingsCache;
	protected MavenImportingSettings myImportingSettingsCache;

	protected VirtualFile myImportRoot;
	protected List<VirtualFile> myFiles;
	protected List<String> myProfiles = new ArrayList<>();
	protected List<String> myActivatedProfiles = new ArrayList<>();
	protected MavenExplicitProfiles mySelectedProfiles = MavenExplicitProfiles.NONE;

	protected MavenProjectsTree myMavenProjectTree;
	protected List<MavenProject> mySelectedProjects;

	protected boolean myOpenModulesConfigurator;


	@Override
	public boolean isOpenProjectSettingsAfter()
	{
		return myOpenModulesConfigurator;
	}

	@Override
	public MavenImportModuleContext setFileToImport(String path)
	{
		VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByPath(path);
		myImportRoot = file == null || file.isDirectory() ? file : file.getParent();
		return this;
	}

	public List<MavenProject> getList()
	{
		return mySelectedProjects;
	}

	public boolean isMarked(MavenProject element)
	{
		return mySelectedProjects.contains(element);
	}

	public MavenImportModuleContext setList(List<MavenProject> list)
	{
		mySelectedProjects = list;
		return this;
	}

	public String getSuggestedProjectName()
	{
		final List<MavenProject> list = myMavenProjectTree.getRootProjects();
		if(list.size() == 1)
		{
			return list.get(0).getMavenId().getArtifactId();
		}
		return null;
	}

	public void setOpenProjectSettingsAfter(boolean openProjectSettingsAfter)
	{
		myOpenModulesConfigurator = openProjectSettingsAfter;
	}

	@Nullable
	public Project getProjectToUpdate()
	{
		if(myProjectToUpdate == null)
		{
			myProjectToUpdate = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext());
		}
		return myProjectToUpdate;
	}

	@Nullable
	public VirtualFile getRootDirectory()
	{
		if(myImportRoot == null && isUpdate())
		{
			final Project project = getProjectToUpdate();
			myImportRoot = project != null ? project.getBaseDir() : null;
		}
		return myImportRoot;
	}

	public MavenExplicitProfiles getSelectedProfiles()
	{
		return mySelectedProfiles;
	}

	public List<String> getActivatedProfiles()
	{
		return myActivatedProfiles;
	}

	public List<String> getProfiles()
	{
		return myProfiles;
	}

	public boolean setRootDirectory(@Nullable Project projectToUpdate, final String root) throws ConfigurationException
	{
		myFiles = null;
		myProfiles.clear();
		myActivatedProfiles.clear();
		myMavenProjectTree = null;

		myProjectToUpdate = projectToUpdate; // We cannot determinate project in non-EDT thread.

		return runConfigurationProcess(ProjectBundle.message("maven.scanning.projects"), new MavenTask()
		{
			@Override
			public void run(MavenProgressIndicator indicator) throws MavenProcessCanceledException
			{
				indicator.setText(ProjectBundle.message("maven.locating.files"));

				myImportRoot = LocalFileSystem.getInstance().refreshAndFindFileByPath(root);
				if(myImportRoot == null)
				{
					throw new MavenProcessCanceledException();
				}

				final VirtualFile file = getRootDirectory();
				if(file == null)
				{
					throw new MavenProcessCanceledException();
				}

				myFiles = FileFinder.findPomFiles(file.getChildren(), getImportingSettings().isLookForNested(), indicator, new ArrayList<>());

				collectProfiles(indicator);
				if(myProfiles.isEmpty())
				{
					readMavenProjectTree(indicator);
				}

				indicator.setText("");
				indicator.setText2("");
			}
		});
	}

	public boolean setSelectedProfiles(MavenExplicitProfiles profiles)
	{
		myMavenProjectTree = null;
		mySelectedProfiles = profiles;

		return runConfigurationProcess(ProjectBundle.message("maven.scanning.projects"), new MavenTask()
		{
			@Override
			public void run(MavenProgressIndicator indicator) throws MavenProcessCanceledException
			{
				readMavenProjectTree(indicator);
				indicator.setText2("");
			}
		});
	}

	private void collectProfiles(MavenProgressIndicator process)
	{
		process.setText(ProjectBundle.message("maven.searching.profiles"));

		Set<String> availableProfiles = new LinkedHashSet<>();
		Set<String> activatedProfiles = new LinkedHashSet<>();
		MavenProjectReader reader = new MavenProjectReader();
		MavenGeneralSettings generalSettings = getGeneralSettings();
		MavenProjectReaderProjectLocator locator = new MavenProjectReaderProjectLocator()
		{
			@Override
			public VirtualFile findProjectFile(MavenId coordinates)
			{
				return null;
			}
		};
		for(VirtualFile f : myFiles)
		{
			MavenProject project = new MavenProject(f);
			process.setText2(ProjectBundle.message("maven.reading.pom", f.getPath()));
			project.read(generalSettings, MavenExplicitProfiles.NONE, reader, locator);
			availableProfiles.addAll(project.getProfilesIds());
			activatedProfiles.addAll(project.getActivatedProfilesIds().getEnabledProfiles());
		}
		myProfiles = new ArrayList<>(availableProfiles);
		myActivatedProfiles = new ArrayList<>(activatedProfiles);
	}

	private static boolean runConfigurationProcess(String message, MavenTask p)
	{
		try
		{
			MavenUtil.run(null, message, p);
		}
		catch(MavenProcessCanceledException e)
		{
			return false;
		}
		return true;
	}

	private void readMavenProjectTree(MavenProgressIndicator process) throws MavenProcessCanceledException
	{
		MavenProjectsTree tree = new MavenProjectsTree();
		tree.addManagedFilesWithProfiles(myFiles, mySelectedProfiles);
		tree.updateAll(false, getGeneralSettings(), process);

		myMavenProjectTree = tree;
		mySelectedProjects = tree.getRootProjects();
	}

	@RequiredReadAction
	private MavenWorkspaceSettings getDirectProjectsSettings()
	{
		ApplicationManager.getApplication().assertReadAccessAllowed();

		Project project = isUpdate() ? getProjectToUpdate() : null;
		if(project == null || project.isDisposed())
		{
			project = ProjectManager.getInstance().getDefaultProject();
		}

		return MavenWorkspaceSettingsComponent.getInstance(project).getSettings();
	}

	public MavenGeneralSettings getGeneralSettings()
	{
		if(myGeneralSettingsCache == null)
		{
			AccessToken accessToken = ApplicationManager.getApplication().acquireReadActionLock();
			try
			{
				myGeneralSettingsCache = getDirectProjectsSettings().generalSettings.clone();
			}
			finally
			{
				accessToken.finish();
			}
		}
		return myGeneralSettingsCache;
	}

	public MavenImportingSettings getImportingSettings()
	{
		if(myImportingSettingsCache == null)
		{
			AccessToken accessToken = ApplicationManager.getApplication().acquireReadActionLock();
			try
			{
				myImportingSettingsCache = getDirectProjectsSettings().importingSettings.clone();
			}
			finally
			{
				accessToken.finish();
			}
		}
		return myImportingSettingsCache;
	}
}
