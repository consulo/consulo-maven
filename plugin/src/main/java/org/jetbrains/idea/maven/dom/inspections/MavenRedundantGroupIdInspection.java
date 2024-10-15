package org.jetbrains.idea.maven.dom.inspections;

import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.LocalQuickFixBase;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.scheme.InspectionManager;
import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.project.Project;
import consulo.xml.codeInspection.XmlSuppressableInspectionTool;
import consulo.xml.psi.xml.XmlFile;
import consulo.xml.psi.xml.XmlTag;
import consulo.xml.util.xml.DomFileElement;
import consulo.xml.util.xml.DomManager;
import org.jetbrains.idea.maven.dom.model.MavenDomParent;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.jetbrains.idea.maven.localize.MavenDomLocalize;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author Sergey Evdokimov
 */
@ExtensionImpl
public class MavenRedundantGroupIdInspection extends XmlSuppressableInspectionTool {
    @Nonnull
    @Override
    public String getGroupDisplayName() {
        return MavenDomLocalize.inspectionGroup().get();
    }

    @Nonnull
    @Override
    public String getDisplayName() {
        return MavenDomLocalize.inspectionRedundantGroupidName().get();
    }

    @Nonnull
    @Override
    public String getShortName() {
        return "MavenRedundantGroupId";
    }

    @Nonnull
    @Override
    public HighlightDisplayLevel getDefaultLevel() {
        return HighlightDisplayLevel.WARNING;
    }

    @Nullable
    @Override
    public ProblemDescriptor[] checkFile(@Nonnull PsiFile file, @Nonnull InspectionManager manager, boolean isOnTheFly) {
        if (file instanceof XmlFile xmlFile && (xmlFile.isPhysical() || xmlFile.getApplication().isUnitTestMode())) {
            DomFileElement<MavenDomProjectModel> model =
                DomManager.getDomManager(xmlFile.getProject()).getFileElement(xmlFile, MavenDomProjectModel.class);

            if (model != null) {
                MavenDomProjectModel projectModel = model.getRootElement();

                String groupId = projectModel.getGroupId().getStringValue();
                if (groupId != null && groupId.length() > 0) {
                    MavenDomParent parent = projectModel.getMavenParent();

                    String parentGroupId = parent.getGroupId().getStringValue();

                    if (groupId.equals(parentGroupId)) {
                        XmlTag xmlTag = projectModel.getGroupId().getXmlTag();

                        LocalQuickFix fix = new LocalQuickFixBase("Remove unnecessary <groupId>") {
                            @Override
                            @RequiredReadAction
                            public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
                                PsiElement xmlTag = descriptor.getPsiElement();

                                if (xmlTag.isValid() && FileModificationService.getInstance().preparePsiElementForWrite(xmlTag)) {
                                    xmlTag.delete();
                                }
                            }
                        };

                        return new ProblemDescriptor[]{
                            manager.createProblemDescriptor(xmlTag,
                                "Definition of groupId is redundant, because it's inherited from the parent",
                                fix, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, isOnTheFly
                            )
                        };
                    }
                }
            }
        }

        return null;
    }
}
