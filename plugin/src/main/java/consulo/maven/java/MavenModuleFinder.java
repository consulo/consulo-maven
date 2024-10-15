package consulo.maven.java;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiElementFinder;
import com.intellij.java.language.psi.PsiJavaModule;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.content.LanguageContentFolderScopes;
import consulo.language.psi.PsiManager;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.util.ModuleUtilCore;
import consulo.maven.module.extension.MavenModuleExtension;
import consulo.maven.rt.server.common.model.MavenPlugin;
import consulo.module.Module;
import consulo.module.content.ModuleRootManager;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import org.jdom.Element;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import jakarta.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 26/04/2023
 */
@ExtensionImpl
public class MavenModuleFinder extends PsiElementFinder
{
	private final Project myProject;
	private final PsiManager myPsiManager;
	private final Provider<MavenProjectsManager> myMavenProjectsManager;

	@Inject
	public MavenModuleFinder(Project project, PsiManager psiManager, Provider<MavenProjectsManager> mavenProjectsManager)
	{
		myProject = project;
		myPsiManager = psiManager;
		myMavenProjectsManager = mavenProjectsManager;
	}

	@Nullable
	@Override
	public PsiJavaModule findModule(@Nonnull VirtualFile file)
	{
		Module module = ModuleUtilCore.findModuleForFile(file, myProject);
		if(module == null)
		{
			return null;
		}

		MavenModuleExtension extension = module.getExtension(MavenModuleExtension.class);
		if(extension == null)
		{
			return null;
		}

		MavenProjectsManager mavenProjectsManager = myMavenProjectsManager.get();

		MavenProject project = mavenProjectsManager.findProject(module);
		if(project == null)
		{
			return null;
		}

		MavenPlugin plugin = project.findPlugin("org.apache.maven.plugins", "maven-jar-plugin");
		if(plugin == null)
		{
			return null;
		}

		Element configurationElement = plugin.getConfigurationElement();
		if(configurationElement == null)
		{
			return null;
		}

		Element archiveElement = configurationElement.getChild("archive");
		if(archiveElement == null)
		{
			return null;
		}

		Element manifestEntries = archiveElement.getChild("manifestEntries");
		if(manifestEntries == null)
		{
			return null;
		}

		String moduleName = manifestEntries.getChildTextTrim(PsiJavaModule.AUTO_MODULE_NAME);

		if(moduleName == null)
		{
			return null;
		}

		VirtualFile[] contentFolderFiles = ModuleRootManager.getInstance(module).getContentFolderFiles(LanguageContentFolderScopes.onlyProduction());

		return new MavenJavaModule(myPsiManager, moduleName, project.getFile(), contentFolderFiles);
	}

	@Nullable
	@Override
	public PsiClass findClass(@Nonnull String qualifiedName, @Nonnull GlobalSearchScope scope)
	{
		return null;
	}

	@Nonnull
	@Override
	public PsiClass[] findClasses(@Nonnull String qualifiedName, @Nonnull GlobalSearchScope scope)
	{
		return PsiClass.EMPTY_ARRAY;
	}
}
