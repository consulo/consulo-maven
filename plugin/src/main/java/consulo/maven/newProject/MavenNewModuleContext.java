package consulo.maven.newProject;

import consulo.application.dumb.DumbAwareRunnable;
import consulo.ide.newModule.NewModuleWizardContextBase;
import consulo.maven.rt.server.common.model.MavenArchetype;
import consulo.maven.rt.server.common.model.MavenId;
import consulo.project.Project;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.idea.maven.project.MavenEnvironmentForm;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenUtil;
import org.jetbrains.idea.maven.wizards.MavenModuleBuilderHelper;

import javax.annotation.Nonnull;
import java.io.File;
import java.util.Map;

/**
 * @author VISTALL
 * @since 2019-10-06
 */
public class MavenNewModuleContext extends NewModuleWizardContextBase
{
	private MavenProject myAggregatorProject;
	private MavenProject myParentProject;

	private boolean myInheritGroupId;
	private boolean myInheritVersion;

	private MavenId myProjectId;
	private MavenArchetype myArchetype;

	private MavenEnvironmentForm myEnvironmentForm;

	private Map<String, String> myPropertiesToCreateByArtifact;

	public MavenNewModuleContext(boolean isNewProject)
	{
		super(isNewProject);
	}

	public void registerMavenInitialize(@Nonnull Project project, @Nonnull VirtualFile root)
	{
		MavenUtil.runWhenInitialized(project, new DumbAwareRunnable()
		{
			public void run()
			{
				if(myEnvironmentForm != null)
				{
					myEnvironmentForm.setData(MavenProjectsManager.getInstance(project).getGeneralSettings());
				}

				new MavenModuleBuilderHelper(myProjectId, myAggregatorProject, myParentProject, myInheritGroupId,
						myInheritVersion, myArchetype, myPropertiesToCreateByArtifact, "Create new Maven module").configure(project, root, false);
			}
		});
	}

	public MavenProject findPotentialParentProject(Project project)
	{
		if(!MavenProjectsManager.getInstance(project).isMavenizedProject())
		{
			return null;
		}

		String path = getPath();
		File parentDir = new File(path).getParentFile();
		if(parentDir == null)
		{
			return null;
		}
		VirtualFile parentPom = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(parentDir, "pom.xml"));
		if(parentPom == null)
		{
			return null;
		}

		return MavenProjectsManager.getInstance(project).findProject(parentPom);
	}

	public void setAggregatorProject(MavenProject project)
	{
		myAggregatorProject = project;
	}

	public MavenProject getAggregatorProject()
	{
		return myAggregatorProject;
	}

	public void setParentProject(MavenProject project)
	{
		myParentProject = project;
	}

	public MavenProject getParentProject()
	{
		return myParentProject;
	}

	public void setInheritedOptions(boolean groupId, boolean version)
	{
		myInheritGroupId = groupId;
		myInheritVersion = version;
	}

	public boolean isInheritGroupId()
	{
		return myInheritGroupId;
	}

	public boolean isInheritVersion()
	{
		return myInheritVersion;
	}

	public void setProjectId(MavenId id)
	{
		myProjectId = id;
	}

	public MavenId getProjectId()
	{
		return myProjectId;
	}

	public void setArchetype(MavenArchetype archetype)
	{
		myArchetype = archetype;
	}

	public MavenArchetype getArchetype()
	{
		return myArchetype;
	}

	public MavenEnvironmentForm getEnvironmentForm()
	{
		return myEnvironmentForm;
	}

	public void setEnvironmentForm(MavenEnvironmentForm environmentForm)
	{
		myEnvironmentForm = environmentForm;
	}

	public Map<String, String> getPropertiesToCreateByArtifact()
	{
		return myPropertiesToCreateByArtifact;
	}

	public void setPropertiesToCreateByArtifact(Map<String, String> propertiesToCreateByArtifact)
	{
		myPropertiesToCreateByArtifact = propertiesToCreateByArtifact;
	}
}
