package consulo.maven.java;

import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.icon.IconDescriptor;
import consulo.language.icon.IconDescriptorUpdater;
import consulo.language.psi.PsiElement;
import consulo.maven.icon.MavenIconGroup;
import jakarta.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 26/04/2023
 */
@ExtensionImpl(order = "after java")
public class MavenJavaModuleIconDescriptor implements IconDescriptorUpdater
{
	@RequiredReadAction
	@Override
	public void updateIcon(@Nonnull IconDescriptor iconDescriptor, @Nonnull PsiElement psiElement, int flags)
	{
		if(psiElement instanceof MavenJavaModule)
		{
			iconDescriptor.setMainIcon(MavenIconGroup.mavenlogo());
		}
	}
}
