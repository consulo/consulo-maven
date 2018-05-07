package org.jetbrains.idea.maven.dom.generate;

import javax.annotation.Nonnull;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.Function;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.ElementPresentationManager;
import com.intellij.util.xml.ui.actions.generate.GenerateDomElementAction;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;

/**
 * User: Sergey.Vasiliev
 */
public class MavenGenerateTemplateAction extends GenerateDomElementAction {
  public MavenGenerateTemplateAction(@Nonnull final String description,
                                     @Nonnull final Class<? extends DomElement> childElementClass,
                                     @javax.annotation.Nullable final String mappingId,
                                     @Nonnull Function<MavenDomProjectModel, DomElement> parentFunction) {
    super(new MavenGenerateDomElementProvider(description, childElementClass, mappingId, parentFunction));

    getTemplatePresentation().setIcon(ElementPresentationManager.getIconForClass(childElementClass));
  }

  protected boolean isValidForFile(@Nonnull Project project, @Nonnull Editor editor, @Nonnull PsiFile file) {
    return file instanceof XmlFile && MavenDomUtil.getMavenDomModel(file, MavenDomProjectModel.class) != null;
  }
}