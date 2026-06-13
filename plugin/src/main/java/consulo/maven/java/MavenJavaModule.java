package consulo.maven.java;

import com.intellij.java.language.impl.psi.impl.light.LightJavaModule;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.util.lang.ObjectUtil;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 26/04/2023
 */
public class MavenJavaModule extends LightJavaModule {
    private final VirtualFile myPOMFile;

    public MavenJavaModule(@Nonnull PsiManager manager, @Nonnull String name, VirtualFile pomFile, VirtualFile root) {
        super(manager, root, name);
        myPOMFile = pomFile;
    }

    @Override
    public PsiFile getContainingFile() {
        return myManager.findFile(myPOMFile);
    }

    @Override
    @Nonnull
    public PsiElement getNavigationElement() {
        return ObjectUtil.notNull(myManager.findFile(myPOMFile), super.getNavigationElement());
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof MavenJavaModule
            && myPOMFile.equals(((MavenJavaModule) obj).myPOMFile)
            && getManager() == ((MavenJavaModule) obj).getManager();
    }

    @Nonnull
    @Override
    public VirtualFile getRootVirtualFile() {
        return myPOMFile;
    }
}
