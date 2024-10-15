package consulo.maven.importProvider;

import consulo.annotation.access.RequiredReadAction;
import consulo.application.ApplicationManager;
import consulo.application.ReadAction;
import consulo.dataContext.DataManager;
import consulo.ide.moduleImport.ModuleImportContext;
import consulo.language.editor.CommonDataKeys;
import consulo.maven.rt.server.common.model.MavenExplicitProfiles;
import consulo.maven.rt.server.common.model.MavenId;
import consulo.project.Project;
import consulo.project.ProjectManager;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.idea.maven.project.*;
import org.jetbrains.idea.maven.utils.*;

import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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

	public MavenImportModuleContext(@Nullable Project project)
	{
		super(project);
	}

	@Override
	public void setFileToImport(String path)
	{
		VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByPath(path);
		myImportRoot = file == null || file.isDirectory() ? file : file.getParent();
	}

	@Nullable
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

	@Nullable
	public Project getProjectToUpdate()
	{
		if(myProjectToUpdate == null)
		{
			myProjectToUpdate = DataManager.getInstance().getDataContext().getData(CommonDataKeys.PROJECT);
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

	public boolean setRootDirectory(@Nullable Project projectToUpdate, final String root)
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

	private boolean isUpdate()
	{
		return !isNewProject();
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
			myGeneralSettingsCache = ReadAction.compute(() -> getDirectProjectsSettings().generalSettings.clone());
		}
		return myGeneralSettingsCache;
	}

	public MavenImportingSettings getImportingSettings()
	{
		if(myImportingSettingsCache == null)
		{
			myImportingSettingsCache = ReadAction.compute(() -> getDirectProjectsSettings().importingSettings.clone());
		}
		return myImportingSettingsCache;
	}
}
