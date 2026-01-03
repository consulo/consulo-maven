package org.jetbrains.idea.maven.navigator.structure;

import consulo.annotation.access.RequiredReadAction;
import consulo.navigation.Navigatable;
import consulo.project.Project;
import consulo.ui.ex.JBColor;
import consulo.ui.ex.SimpleTextAttributes;
import consulo.ui.ex.awt.tree.CachingSimpleNode;
import consulo.ui.ex.awt.tree.SimpleNode;
import consulo.ui.ex.awt.tree.SimpleTree;
import consulo.ui.ex.tree.NodeDescriptor;
import consulo.ui.ex.tree.PresentationData;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.idea.maven.navigator.MavenNavigationUtil;
import org.jetbrains.idea.maven.utils.MavenUIUtil;

import java.awt.*;
import java.awt.event.InputEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class MavenSimpleNode extends CachingSimpleNode {
    protected final MavenProjectsStructure myMavenProjectsStructure;

    private MavenSimpleNode myParent;

    private MavenProjectsStructure.ErrorLevel myErrorLevel = MavenProjectsStructure.ErrorLevel.NONE;
    private MavenProjectsStructure.ErrorLevel myTotalErrorLevel = null;

    public MavenSimpleNode(MavenProjectsStructure mavenProjectsStructure, MavenSimpleNode parent) {
        super(mavenProjectsStructure.getProject(), null);
        myMavenProjectsStructure = mavenProjectsStructure;
        setParent(parent);
    }

    public Project getProject() {
        return myMavenProjectsStructure.getProject();
    }

    public void setParent(MavenSimpleNode parent) {
        myParent = parent;
    }

    @Override
    public NodeDescriptor getParentDescriptor() {
        return myParent;
    }

    public <T extends MavenSimpleNode> T findParent(Class<T> parentClass) {
        MavenSimpleNode node = this;
        while (true) {
            node = node.myParent;
            if (node == null || parentClass.isInstance(node)) {
                //noinspection unchecked
                return (T) node;
            }
        }
    }

    public boolean isVisible() {
        return getDisplayKind() != MavenProjectsStructure.DisplayKind.NEVER;
    }

    public MavenProjectsStructure.DisplayKind getDisplayKind() {
        Class[] visibles = myMavenProjectsStructure.getVisibleNodesClasses();
        if (visibles == null) {
            return MavenProjectsStructure.DisplayKind.NORMAL;
        }

        for (Class each : visibles) {
            if (each.isInstance(this)) {
                return MavenProjectsStructure.DisplayKind.ALWAYS;
            }
        }
        return MavenProjectsStructure.DisplayKind.NEVER;
    }


    @Override
    protected SimpleNode[] buildChildren() {
        List<? extends MavenSimpleNode> children = doGetChildren();
        if (children.isEmpty()) {
            return NO_CHILDREN;
        }

        List<MavenSimpleNode> result = new ArrayList<>();
        for (MavenSimpleNode each : children) {
            if (each.isVisible()) {
                result.add(each);
            }
        }
        return result.toArray(new MavenSimpleNode[result.size()]);
    }

    protected List<? extends MavenSimpleNode> doGetChildren() {
        return Collections.emptyList();
    }

    @Override
    public void cleanUpCache() {
        super.cleanUpCache();
        myTotalErrorLevel = null;
    }

    protected void childrenChanged() {
        MavenSimpleNode each = this;
        while (each != null) {
            each.cleanUpCache();
            each = (MavenSimpleNode) each.getParent();
        }
        myMavenProjectsStructure.updateUpTo(this);
    }

    public MavenProjectsStructure.ErrorLevel getTotalErrorLevel() {
        if (myTotalErrorLevel == null) {
            myTotalErrorLevel = calcTotalErrorLevel();
        }
        return myTotalErrorLevel;
    }

    private MavenProjectsStructure.ErrorLevel calcTotalErrorLevel() {
        MavenProjectsStructure.ErrorLevel childrenErrorLevel = getChildrenErrorLevel();
        return childrenErrorLevel.compareTo(myErrorLevel) > 0 ? childrenErrorLevel : myErrorLevel;
    }

    public MavenProjectsStructure.ErrorLevel getChildrenErrorLevel() {
        MavenProjectsStructure.ErrorLevel result = MavenProjectsStructure.ErrorLevel.NONE;
        for (SimpleNode each : getChildren()) {
            MavenProjectsStructure.ErrorLevel eachLevel = ((MavenSimpleNode) each).getTotalErrorLevel();
            if (eachLevel.compareTo(result) > 0) {
                result = eachLevel;
            }
        }
        return result;
    }

    public void setErrorLevel(MavenProjectsStructure.ErrorLevel level) {
        if (myErrorLevel == level) {
            return;
        }
        myErrorLevel = level;
        myMavenProjectsStructure.updateUpTo(this);
    }

    @Override
    protected void doUpdate() {
        setNameAndTooltip(getName(), null);
    }

    public boolean showDescription() {
        //return myMavenProjectsStructure.getDisplayMode() == SHOW_ALL;
        return true;
    }

    protected void setNameAndTooltip(@Nonnull PresentationData presentation,
                                     String name,
                                     @Nullable String tooltip,
                                     @Nullable String hint) {
        setNameAndTooltip(presentation, name, tooltip, getPlainAttributes());
        if (showDescription() && !StringUtil.isEmptyOrSpaces(hint)) {
            presentation.addText(" (" + hint + ")", SimpleTextAttributes.GRAY_ATTRIBUTES);
        }
    }

    protected void setNameAndTooltip(@Nonnull PresentationData presentation,
                                     String name,
                                     @Nullable String tooltip,
                                     SimpleTextAttributes attributes) {
        presentation.clearText();
        presentation.addText(name, prepareAttributes(attributes));
        getTemplatePresentation().setTooltip(tooltip);
    }

    protected void setNameAndTooltip(String name, @Nullable String tooltip) {
        setNameAndTooltip(name, tooltip, (String) null);
    }

    protected void setNameAndTooltip(String name, @Nullable String tooltip, @Nullable String hint) {
        setNameAndTooltip(name, tooltip, getPlainAttributes());
        if (myMavenProjectsStructure.showDescriptions() && !StringUtil.isEmptyOrSpaces(hint)) {
            addColoredFragment(" (" + hint + ")", SimpleTextAttributes.GRAY_ATTRIBUTES);
        }
    }

    protected void setNameAndTooltip(String name, @Nullable String tooltip, SimpleTextAttributes attributes) {
        clearColoredText();
        addColoredFragment(name, prepareAttributes(attributes));
        getTemplatePresentation().setTooltip(tooltip);
    }

    private SimpleTextAttributes prepareAttributes(SimpleTextAttributes from) {
        MavenProjectsStructure.ErrorLevel level = getTotalErrorLevel();
        Color waveColor = level == MavenProjectsStructure.ErrorLevel.NONE ? null : JBColor.RED;
        int style = from.getStyle();
        if (waveColor != null) {
            style |= SimpleTextAttributes.STYLE_WAVED;
        }
        return new SimpleTextAttributes(from.getBgColor(), from.getFgColor(), waveColor, style);
    }

    @Nullable
    String getActionId() {
        return null;
    }

    @Nullable
    public String getMenuId() {
        return null;
    }

    @Nullable
    public VirtualFile getVirtualFile() {
        return null;
    }

    @Nullable
    @RequiredReadAction
    public Navigatable getNavigatable() {
        return MavenNavigationUtil.createNavigatableForPom(getProject(), getVirtualFile());
    }

    @Override
    public void handleDoubleClickOrEnter(SimpleTree tree, InputEvent inputEvent) {
        String actionId = getActionId();
        if (actionId != null) {
            MavenUIUtil.executeAction(actionId, inputEvent);
        }
    }
}
