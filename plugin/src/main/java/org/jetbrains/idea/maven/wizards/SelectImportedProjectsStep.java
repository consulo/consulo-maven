/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.maven.wizards;

import consulo.disposer.Disposable;
import consulo.ide.localize.IdeLocalize;
import consulo.localize.LocalizeValue;
import consulo.maven.importProvider.MavenImportModuleContext;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.ui.CheckBox;
import consulo.ui.Component;
import consulo.ui.ComponentItemRender;
import consulo.ui.Table;
import consulo.ui.TableItemEditor;
import consulo.ui.TextItemRender;
import consulo.ui.TextAttribute;
import consulo.ui.ValueComponent;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.ActionToolbarPosition;
import consulo.ui.ex.toolbar.ToolbarDecoratorBuilder;
import consulo.ui.ex.wizard.WizardStep;
import consulo.ui.ex.wizard.WizardStepValidationException;
import consulo.ui.image.Image;
import consulo.ui.layout.LabeledLayout;
import consulo.ui.model.FlatDataModel;
import consulo.ui.model.MutableFlatDataModel;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.idea.maven.localize.MavenProjectLocalize;
import org.jetbrains.idea.maven.project.MavenProject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Vladislav.Kaznacheev
 */
public abstract class SelectImportedProjectsStep implements WizardStep<MavenImportModuleContext> {
    protected final MavenImportModuleContext myContext;

    /**
     * Source of truth for both columns - the table reads through these, so they survive the step
     * being entered before its component is built.
     */
    private final List<MavenProject> myProjects = new ArrayList<>();
    private final Set<MavenProject> myMarkedProjects = new LinkedHashSet<>();

    private @Nullable MutableFlatDataModel<MavenProject> myModel;

    public SelectImportedProjectsStep(MavenImportModuleContext context) {
        myContext = context;
    }

    @Nullable
    protected Image getElementIcon(final MavenProject item) {
        return null;
    }

    protected abstract String getElementText(final MavenProject item);

    protected boolean isElementEnabled(MavenProject element) {
        return true;
    }

    @RequiredUIAccess
    @Nonnull
    @Override
    public Component getComponent(@Nonnull MavenImportModuleContext context, @Nonnull Disposable disposable) {
        MutableFlatDataModel<MavenProject> model = FlatDataModel.of(new ArrayList<>(myProjects));
        myModel = model;

        Table<MavenProject> table = Table.create(model);
        table.setShowHeader(false);

        table.addColumn(LocalizeValue.empty(), myMarkedProjects::contains)
            .setWidth(40)
            .setEditor(new TableItemEditor<>() {
                @RequiredUIAccess
                @Override
                public ValueComponent<Boolean> createComponent(MavenProject project) {
                    return CheckBox.create(LocalizeValue.empty(), myMarkedProjects.contains(project));
                }

                @RequiredUIAccess
                @Override
                public void commit(MavenProject project, @Nullable Boolean value) {
                    setMarked(project, Boolean.TRUE.equals(value));
                    model.update(project);
                }

                @Override
                public boolean isEditable(MavenProject project) {
                    return isElementEnabled(project);
                }
            })
            .setRender(ComponentItemRender.reusable(
                () -> CheckBox.create(LocalizeValue.empty()),
                (checkBox, item) -> checkBox.setValue(Boolean.TRUE.equals(item.getValue()))
            ));

        // typed local - setRender() is overloaded for text and component renders, a bare lambda is ambiguous
        TextItemRender<MavenProject> projectRender = (presentation, item) -> {
            MavenProject project = item.getValue();
            if (project == null) {
                return;
            }

            Image icon = getElementIcon(project);
            if (icon != null) {
                presentation.withIcon(icon);
            }

            // an ignored project can not be checked, so its name carries that signal
            presentation.append(
                getElementText(project),
                isElementEnabled(project) ? TextAttribute.REGULAR : TextAttribute.GRAYED
            );
        };

        table.addColumn(LocalizeValue.empty(), project -> project).setRender(projectRender);

        Component decorated = ToolbarDecoratorBuilder.newBuilder(table)
            .disableAll()
            .withToolbarPosition(ActionToolbarPosition.BOTTOM)
            .addExtraAction(new SetAllMarkedAction(
                MavenProjectLocalize.mavenImportSelectAll(),
                PlatformIconGroup.actionsSelectall(),
                true
            ))
            .addExtraAction(new SetAllMarkedAction(
                MavenProjectLocalize.mavenImportUnselectAll(),
                PlatformIconGroup.actionsUnselectall(),
                false
            ))
            .build();

        return LabeledLayout.create(IdeLocalize.projectImportSelectTitle(MavenProjectLocalize.mavenName().get()), decorated);
    }

    private void setMarked(MavenProject project, boolean marked) {
        if (marked) {
            myMarkedProjects.add(project);
        }
        else {
            myMarkedProjects.remove(project);
        }
    }

    @Override
    public void onStepEnter(@Nonnull MavenImportModuleContext context) {
        myProjects.clear();
        myMarkedProjects.clear();

        List<MavenProject> list = context.getList();
        if (list != null) {
            for (MavenProject element : list) {
                myProjects.add(element);
                if (isElementEnabled(element) && getContext().isMarked(element)) {
                    myMarkedProjects.add(element);
                }
            }
        }

        MutableFlatDataModel<MavenProject> model = myModel;
        if (model != null) {
            model.replaceAll(myProjects);
        }
    }

    @Override
    public void onStepLeave(@Nonnull MavenImportModuleContext context) {
        context.setList(new ArrayList<>(myMarkedProjects));

        updateDataModel();
    }

    @Override
    public void validateStep(@Nonnull MavenImportModuleContext context) throws WizardStepValidationException {
        onStepLeave(context);
        if (myMarkedProjects.isEmpty()) {
            throw new WizardStepValidationException(MavenProjectLocalize.mavenImportNothingToImport().get());
        }
    }

    public void updateDataModel() {
    }

    public MavenImportModuleContext getContext() {
        return myContext;
    }

    private class SetAllMarkedAction extends AnAction {
        private final boolean myMarked;

        private SetAllMarkedAction(LocalizeValue text, Image icon, boolean marked) {
            super(text, LocalizeValue.empty(), icon);
            myMarked = marked;
        }

        @RequiredUIAccess
        @Override
        public void actionPerformed(@Nonnull AnActionEvent e) {
            MutableFlatDataModel<MavenProject> model = myModel;

            for (MavenProject project : myProjects) {
                if (!isElementEnabled(project)) {
                    continue;
                }

                setMarked(project, myMarked);

                if (model != null) {
                    model.update(project);
                }
            }
        }

        @Override
        public boolean displayTextInToolbar() {
            return true;
        }
    }
}
