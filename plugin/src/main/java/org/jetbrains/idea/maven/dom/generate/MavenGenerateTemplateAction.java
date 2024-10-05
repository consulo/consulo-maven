package org.jetbrains.idea.maven.dom.generate;

import consulo.codeEditor.Editor;
import consulo.language.psi.PsiFile;
import consulo.project.Project;
import consulo.xml.psi.xml.XmlFile;
import consulo.xml.util.xml.DomElement;
import consulo.xml.util.xml.ElementPresentationManager;
import consulo.xml.util.xml.ui.actions.generate.GenerateDomElementAction;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.function.Function;

/**
 * User: Sergey.Vasiliev
 */
public class MavenGenerateTemplateAction extends GenerateDomElementAction {
    public MavenGenerateTemplateAction(
        @Nonnull final String description,
        @Nonnull final Class<? extends DomElement> childElementClass,
        @Nullable final String mappingId,
        @Nonnull Function<MavenDomProjectModel, DomElement> parentFunction
    ) {
        super(new MavenGenerateDomElementProvider(description, childElementClass, mappingId, parentFunction));

        getTemplatePresentation().setIcon(ElementPresentationManager.getIconForClass(childElementClass));
    }

    protected boolean isValidForFile(@Nonnull Project project, @Nonnull Editor editor, @Nonnull PsiFile file) {
        return file instanceof XmlFile && MavenDomUtil.getMavenDomModel(file, MavenDomProjectModel.class) != null;
    }
}