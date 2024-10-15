package consulo.maven.java;

import com.intellij.java.language.impl.psi.impl.light.LightJavaModuleBase;
import com.intellij.java.language.psi.JavaDirectoryService;
import com.intellij.java.language.psi.PsiJavaPackage;
import com.intellij.java.language.psi.PsiPackageAccessibilityStatement;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.util.lang.ObjectUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import consulo.virtualFileSystem.util.VirtualFileVisitor;

import jakarta.annotation.Nonnull;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author VISTALL
 * @since 26/04/2023
 */
public class MavenJavaModule extends LightJavaModuleBase
{
	private final VirtualFile[] myContentFolderFiles;
	private final VirtualFile myPOMFile;

	public MavenJavaModule(@Nonnull PsiManager manager, @Nonnull String name, VirtualFile pomFile, VirtualFile[] contentFolderFiles)
	{
		super(manager, name);
		myPOMFile = pomFile;
		myContentFolderFiles = contentFolderFiles;
	}

	@Override
	protected List<PsiPackageAccessibilityStatement> findExports()
	{
		List<PsiPackageAccessibilityStatement> exports = new ArrayList<>();

		Set<String> added = new HashSet<>();

		for(VirtualFile contentFolderFile : myContentFolderFiles)
		{
			JavaDirectoryService service = JavaDirectoryService.getInstance();
			VirtualFileUtil.visitChildrenRecursively(contentFolderFile, new VirtualFileVisitor<Void>()
			{

				@Override
				@RequiredReadAction
				public boolean visitFile(@Nonnull VirtualFile file)
				{
					if(file.isDirectory() && !contentFolderFile.equals(file))
					{
						PsiDirectory directory = getManager().findDirectory(file);
						if(directory != null)
						{
							PsiJavaPackage pkg = service.getPackage(directory);
							if(pkg != null)
							{
								String packageName = pkg.getQualifiedName();
								if(!packageName.isEmpty() && added.add(packageName) && !PsiUtil.isPackageEmpty(new PsiDirectory[]{directory}, packageName))
								{
									exports.add(new LightPackageAccessibilityStatement(getManager(), packageName));
								}
							}
						}
					}
					return true;
				}
			});

		}

		return exports;
	}

	@Override
	public PsiFile getContainingFile()
	{
		return myManager.findFile(myPOMFile);
	}

	@Override
	@Nonnull
	public PsiElement getNavigationElement()
	{
		return ObjectUtil.notNull(myManager.findFile(myPOMFile), super.getNavigationElement());
	}

	@Override
	public boolean equals(Object obj)
	{
		return obj instanceof MavenJavaModule && myPOMFile.equals(((MavenJavaModule) obj).myPOMFile) && getManager() == ((MavenJavaModule) obj).getManager();
	}

	@Nonnull
	@Override
	public VirtualFile getRootVirtualFile()
	{
		return myPOMFile;
	}
}
