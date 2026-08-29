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

import consulo.application.Application;
import consulo.application.ApplicationPropertiesComponent;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.maven.newProject.MavenNewModuleContext;
import consulo.maven.rt.server.common.model.MavenArchetype;
import consulo.maven.rt.server.common.model.MavenId;
import consulo.project.Project;
import consulo.ui.Button;
import consulo.ui.CheckBox;
import consulo.ui.Component;
import consulo.ui.Label;
import consulo.ui.TextAttribute;
import consulo.ui.TextBox;
import consulo.ui.Tree;
import consulo.ui.TreeModel;
import consulo.ui.TreeNode;
import consulo.ui.UIAccess;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.dialog.DialogService;
import consulo.ui.ex.wizard.WizardStep;
import consulo.ui.ex.wizard.WizardStepValidationException;
import consulo.ui.layout.DockLayout;
import consulo.ui.layout.LoadingLayout;
import consulo.ui.layout.ScrollableLayout;
import consulo.ui.layout.VerticalLayout;
import consulo.ui.util.FormBuilder;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import org.jetbrains.idea.maven.indices.MavenIndicesManager;
import org.jetbrains.idea.maven.localize.MavenProjectLocalize;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

public class MavenModuleWizardStep implements WizardStep<MavenNewModuleContext> {
    private static final Logger LOG = Logger.getInstance(MavenModuleWizardStep.class);

    private static final String INHERIT_GROUP_ID_KEY = "MavenModuleWizard.inheritGroupId";
    private static final String INHERIT_VERSION_KEY = "MavenModuleWizard.inheritVersion";
    private static final String ARCHETYPE_ARTIFACT_ID_KEY = "MavenModuleWizard.archetypeArtifactIdKey";
    private static final String ARCHETYPE_GROUP_ID_KEY = "MavenModuleWizard.archetypeGroupIdKey";
    private static final String ARCHETYPE_VERSION_KEY = "MavenModuleWizard.archetypeVersionKey";

    /**
     * A group row and a version row can carry the same archetype - the first version stands for its
     * group - so the value alone does not say which level a node sits on. The flag does.
     */
    private record ArchetypeNode(MavenArchetype archetype, boolean group) {
    }

    private final Project myProjectOrNull;
    private final MavenNewModuleContext myContext;
    private MavenProject myAggregator;
    private MavenProject myParent;

    private String myInheritedGroupId;
    private String myInheritedVersion;

    /**
     * Source of truth for the whole step - the step can be entered before its component exists, and the
     * component is only a view over these.
     */
    private String myGroupId = "";
    private String myArtifactId = "";
    private String myVersion = "";
    private boolean myInheritGroupId;
    private boolean myInheritVersion;
    private boolean myUseArchetype;
    private @Nullable MavenArchetype mySelectedArchetype;

    /**
     * Archetypes by "groupId:artifactId", newest version first within a group.
     */
    private Map<String, List<MavenArchetype>> myArchetypesByKey = Map.of();

    private @Nullable TextBox myGroupIdBox;
    private @Nullable TextBox myArtifactIdBox;
    private @Nullable TextBox myVersionBox;
    private @Nullable CheckBox myInheritGroupIdBox;
    private @Nullable CheckBox myInheritVersionBox;
    private @Nullable CheckBox myUseArchetypeBox;
    private @Nullable Button myAddArchetypeButton;
    private @Nullable Tree<ArchetypeNode> myArchetypesTree;
    private @Nullable LoadingLayout<DockLayout> myArchetypesLayout;
    private @Nullable Label myArchetypeDescriptionLabel;

    public MavenModuleWizardStep(MavenNewModuleContext context) {
        myProjectOrNull = null;
        myContext = context;

        loadSettings();
    }

    @RequiredUIAccess
    @Nonnull
    @Override
    public Component getComponent(@Nonnull MavenNewModuleContext context, @Nonnull Disposable uiDisposable) {
        TextBox groupIdBox = myGroupIdBox = TextBox.create(myGroupId);
        TextBox artifactIdBox = myArtifactIdBox = TextBox.create(myArtifactId);
        TextBox versionBox = myVersionBox = TextBox.create(myVersion);

        groupIdBox.addValueListener(e -> myGroupId = StringUtil.notNullize(e.getValue()));
        artifactIdBox.addValueListener(e -> myArtifactId = StringUtil.notNullize(e.getValue()));
        versionBox.addValueListener(e -> myVersion = StringUtil.notNullize(e.getValue()));

        CheckBox inheritGroupIdBox = myInheritGroupIdBox =
            CheckBox.create(MavenProjectLocalize.mavenWizardInherit(), myInheritGroupId);
        CheckBox inheritVersionBox = myInheritVersionBox =
            CheckBox.create(MavenProjectLocalize.mavenWizardInherit(), myInheritVersion);

        inheritGroupIdBox.addValueListener(e -> {
            myInheritGroupId = Boolean.TRUE.equals(e.getValue());
            updateComponents();
        });
        inheritVersionBox.addValueListener(e -> {
            myInheritVersion = Boolean.TRUE.equals(e.getValue());
            updateComponents();
        });

        FormBuilder idForm = FormBuilder.create();
        idForm.addLabeled(MavenProjectLocalize.mavenWizardGroupId(), DockLayout.create().center(groupIdBox).right(inheritGroupIdBox));
        idForm.addLabeled(MavenProjectLocalize.mavenWizardArtifactId(), artifactIdBox);
        idForm.addLabeled(MavenProjectLocalize.mavenWizardVersion(), DockLayout.create().center(versionBox).right(inheritVersionBox));

        CheckBox useArchetypeBox = myUseArchetypeBox =
            CheckBox.create(MavenProjectLocalize.mavenWizardCreateFromArchetype(), myUseArchetype);
        useArchetypeBox.addValueListener(e -> {
            myUseArchetype = Boolean.TRUE.equals(e.getValue());
            updateComponents();
            archetypeMayBeChanged();
        });

        Button addArchetypeButton = myAddArchetypeButton =
            Button.create(MavenProjectLocalize.mavenWizardAddArchetype(), e -> doAddArchetype());

        Tree<ArchetypeNode> tree = myArchetypesTree = Tree.create(new ArchetypesTreeModel());
        Disposer.register(uiDisposable, tree.destroyHook());
        tree.setSpeedSearchConverter(node -> {
            ArchetypeNode value = node.getValue();
            if (value == null) {
                return "";
            }
            MavenArchetype archetype = value.archetype();
            return archetype.groupId + ":" + archetype.artifactId + ":" + archetype.version;
        });
        tree.addSelectListener(e -> {
            TreeNode<ArchetypeNode> node = e.getValue();
            ArchetypeNode value = node == null ? null : node.getValue();
            mySelectedArchetype = value == null ? null : value.archetype();

            updateArchetypeDescription();
            archetypeMayBeChanged();
        });

        LoadingLayout<DockLayout> archetypesLayout = myArchetypesLayout =
            LoadingLayout.create(DockLayout.create(), uiDisposable);
        archetypesLayout.setLoadingText(MavenProjectLocalize.mavenWizardLoadingArchetypes());

        Label descriptionLabel = myArchetypeDescriptionLabel = Label.create();
        descriptionLabel.setVisible(false);

        VerticalLayout top = VerticalLayout.create();
        top.add(idForm.build());
        top.add(DockLayout.create().left(useArchetypeBox).right(addArchetypeButton));

        DockLayout root = DockLayout.create();
        root.top(top);
        root.center(archetypesLayout);
        root.bottom(descriptionLabel);

        loadArchetypes(mySelectedArchetype);
        updateComponents();

        return root;
    }

    @Override
    public Component getPreferredFocusedComponent() {
        return myGroupIdBox;
    }

    @RequiredUIAccess
    private void loadArchetypes(@Nullable MavenArchetype toSelect) {
        LoadingLayout<DockLayout> layout = myArchetypesLayout;
        Tree<ArchetypeNode> tree = myArchetypesTree;
        if (layout == null || tree == null) {
            return;
        }

        UIAccess uiAccess = UIAccess.current();

        layout.startLoading(
            () -> {
                try {
                    return MavenIndicesManager.getInstance().getArchetypes();
                }
                catch (Exception e) {
                    LOG.error(e);
                    return Set.<MavenArchetype>of();
                }
            },
            (inner, archetypes) -> {
                myArchetypesByKey = groupAndSortArchetypes(archetypes);

                inner.center(ScrollableLayout.create(tree));

                tree.refreshAll().whenComplete((ignored, throwable) -> uiAccess.give(() -> selectArchetype(tree, toSelect)));
            }
        );
    }

    @RequiredUIAccess
    private void selectArchetype(Tree<ArchetypeNode> tree, @Nullable MavenArchetype toSelect) {
        if (toSelect == null) {
            return;
        }

        UIAccess uiAccess = UIAccess.current();

        tree.getRootNode()
            .findChildDeep(node -> !node.group() && node.archetype().equals(toSelect))
            .whenComplete((node, throwable) -> {
                if (node != null) {
                    uiAccess.give(() -> tree.select(node));
                }
            });
    }

    private void archetypeMayBeChanged() {
        MavenArchetype selectedArchetype = getSelectedArchetype();
        if ((myContext.getArchetype() == null) != (selectedArchetype == null)) {
            myContext.setArchetype(selectedArchetype);
        }
    }

    @RequiredUIAccess
    private void doAddArchetype() {
        UIAccess uiAccess = UIAccess.current();

        MavenAddArchetypeDialog descriptor = new MavenAddArchetypeDialog();

        Application.get().getInstance(DialogService.class).build(descriptor).showAsync().whenComplete((value, throwable) -> {
            if (throwable != null || value == null) {
                return;
            }

            uiAccess.give(() -> {
                MavenArchetype archetype = descriptor.getArchetype();
                MavenIndicesManager.getInstance().addArchetype(archetype);
                loadArchetypes(archetype);
            });
        });
    }

    @Override
    public void onStepLeave(MavenNewModuleContext context) {
        saveSettings();

        updateDataModel();
    }

    public void updateDataModel() {
        myContext.setAggregatorProject(myAggregator);
        myContext.setParentProject(myParent);

        myContext.setProjectId(new MavenId(myGroupId, myArtifactId, myVersion));
        myContext.setInheritedOptions(myInheritGroupId, myInheritVersion);

        myContext.setArchetype(getSelectedArchetype());
    }

    private void loadSettings() {
        myInheritGroupId = getSavedValue(INHERIT_GROUP_ID_KEY, true);
        myInheritVersion = getSavedValue(INHERIT_VERSION_KEY, true);
        myContext.setInheritedOptions(myInheritGroupId, myInheritVersion);

        String archGroupId = getSavedValue(ARCHETYPE_GROUP_ID_KEY, null);
        String archArtifactId = getSavedValue(ARCHETYPE_ARTIFACT_ID_KEY, null);
        String archVersion = getSavedValue(ARCHETYPE_VERSION_KEY, null);
        if (archGroupId == null || archArtifactId == null || archVersion == null) {
            myContext.setArchetype(null);
        }
        else {
            myContext.setArchetype(new MavenArchetype(archGroupId, archArtifactId, archVersion, null, null));
        }
    }

    private void saveSettings() {
        saveValue(INHERIT_GROUP_ID_KEY, myInheritGroupId);
        saveValue(INHERIT_VERSION_KEY, myInheritVersion);

        MavenArchetype arch = getSelectedArchetype();
        saveValue(ARCHETYPE_GROUP_ID_KEY, arch == null ? null : arch.groupId);
        saveValue(ARCHETYPE_ARTIFACT_ID_KEY, arch == null ? null : arch.artifactId);
        saveValue(ARCHETYPE_VERSION_KEY, arch == null ? null : arch.version);
    }

    private boolean getSavedValue(String key, boolean defaultValue) {
        return getSavedValue(key, String.valueOf(defaultValue)).equals(String.valueOf(true));
    }

    private static String getSavedValue(String key, String defaultValue) {
        String value = ApplicationPropertiesComponent.getInstance().getValue(key);
        return value == null ? defaultValue : value;
    }

    private void saveValue(String key, boolean value) {
        saveValue(key, String.valueOf(value));
    }

    private static void saveValue(String key, String value) {
        ApplicationPropertiesComponent.getInstance().setValue(key, value);
    }

    @Override
    public void validateStep(@Nonnull MavenNewModuleContext context) throws WizardStepValidationException {
        if (StringUtil.isEmptyOrSpaces(myGroupId)) {
            throw new WizardStepValidationException(MavenProjectLocalize.mavenWizardSpecifyGroupId().get());
        }

        if (StringUtil.isEmptyOrSpaces(myArtifactId)) {
            throw new WizardStepValidationException(MavenProjectLocalize.mavenWizardSpecifyArtifactId().get());
        }

        if (StringUtil.isEmptyOrSpaces(myVersion)) {
            throw new WizardStepValidationException(MavenProjectLocalize.mavenWizardSpecifyVersion().get());
        }
    }

    @RequiredUIAccess
    @Override
    public void onStepEnter(@Nonnull MavenNewModuleContext context) {
        if (isMavenizedProject()) {
            MavenProject parent = myContext.findPotentialParentProject(myProjectOrNull);
            myAggregator = parent;
            myParent = parent;
        }

        MavenId projectId = myContext.getProjectId();

        if (projectId == null) {
            myArtifactId = StringUtil.notNullize(myContext.getName());
            myGroupId = myParent == null
                ? StringUtil.notNullize(myContext.getName())
                : myParent.getMavenId().getGroupId();
            myVersion = myParent == null ? "1.0-SNAPSHOT" : myParent.getMavenId().getVersion();
        }
        else {
            myArtifactId = StringUtil.notNullize(projectId.getArtifactId());
            myGroupId = StringUtil.notNullize(projectId.getGroupId());
            myVersion = StringUtil.notNullize(projectId.getVersion());
        }

        myInheritGroupId = myContext.isInheritGroupId();
        myInheritVersion = myContext.isInheritVersion();

        if (mySelectedArchetype == null) {
            mySelectedArchetype = myContext.getArchetype();
        }
        if (myContext.getArchetype() != null) {
            myUseArchetype = true;
        }

        pushToComponents();
        updateComponents();
    }

    @RequiredUIAccess
    private void pushToComponents() {
        if (myGroupIdBox != null) {
            myGroupIdBox.setValue(myGroupId);
        }
        if (myArtifactIdBox != null) {
            myArtifactIdBox.setValue(myArtifactId);
        }
        if (myVersionBox != null) {
            myVersionBox.setValue(myVersion);
        }
        if (myInheritGroupIdBox != null) {
            myInheritGroupIdBox.setValue(myInheritGroupId);
        }
        if (myInheritVersionBox != null) {
            myInheritVersionBox.setValue(myInheritVersion);
        }
        if (myUseArchetypeBox != null) {
            myUseArchetypeBox.setValue(myUseArchetype);
        }
    }

    @RequiredUIAccess
    private void updateArchetypeDescription() {
        Label label = myArchetypeDescriptionLabel;
        if (label == null) {
            return;
        }

        MavenArchetype selected = getSelectedArchetype();
        String description = selected == null ? null : selected.description;
        if (StringUtil.isEmptyOrSpaces(description)) {
            label.setVisible(false);
        }
        else {
            // there is no multiline label in the unified api, so let html do the wrapping
            label.setText(LocalizeValue.of(
                "<html><body><div width=\"400\">" + StringUtil.escapeXmlEntities(description) + "</div></body></html>"
            ));
            label.setVisible(true);
        }
    }

    private static Map<String, List<MavenArchetype>> groupAndSortArchetypes(Collection<MavenArchetype> archetypes) {
        List<MavenArchetype> list = new ArrayList<>(archetypes);

        list.sort((o1, o2) -> {
            String key1 = o1.groupId + ":" + o1.artifactId;
            String key2 = o2.groupId + ":" + o2.artifactId;

            int result = key1.compareToIgnoreCase(key2);
            if (result != 0) {
                return result;
            }

            return o2.version.compareToIgnoreCase(o1.version);
        });

        Map<String, List<MavenArchetype>> map = new TreeMap<>();
        for (MavenArchetype each : list) {
            map.computeIfAbsent(each.groupId + ":" + each.artifactId, key -> new ArrayList<>()).add(each);
        }

        return new LinkedHashMap<>(map);
    }

    private boolean isMavenizedProject() {
        return myProjectOrNull != null && MavenProjectsManager.getInstance(myProjectOrNull).isMavenizedProject();
    }

    @RequiredUIAccess
    private void updateComponents() {
        if (myParent == null) {
            if (myGroupIdBox != null) {
                myGroupIdBox.setEnabled(true);
            }
            if (myVersionBox != null) {
                myVersionBox.setEnabled(true);
            }
            if (myInheritGroupIdBox != null) {
                myInheritGroupIdBox.setEnabled(false);
            }
            if (myInheritVersionBox != null) {
                myInheritVersionBox.setEnabled(false);
            }
        }
        else {
            if (myInheritGroupId || myGroupId.equals(myInheritedGroupId)) {
                myGroupId = myParent.getMavenId().getGroupId();
            }
            if (myInheritVersion || myVersion.equals(myInheritedVersion)) {
                myVersion = myParent.getMavenId().getVersion();
            }
            myInheritedGroupId = myGroupId;
            myInheritedVersion = myVersion;

            if (myGroupIdBox != null) {
                myGroupIdBox.setValue(myGroupId);
                myGroupIdBox.setEnabled(!myInheritGroupId);
            }
            if (myVersionBox != null) {
                myVersionBox.setValue(myVersion);
                myVersionBox.setEnabled(!myInheritVersion);
            }
            if (myInheritGroupIdBox != null) {
                myInheritGroupIdBox.setEnabled(true);
            }
            if (myInheritVersionBox != null) {
                myInheritVersionBox.setEnabled(true);
            }
        }

        if (myAddArchetypeButton != null) {
            myAddArchetypeButton.setEnabled(myUseArchetype);
        }
        if (myArchetypesTree != null) {
            myArchetypesTree.setEnabled(myUseArchetype);
        }
    }

    @Nullable
    private MavenArchetype getSelectedArchetype() {
        return myUseArchetype ? mySelectedArchetype : null;
    }

    private class ArchetypesTreeModel implements TreeModel<ArchetypeNode> {
        @Override
        public void buildChildren(Function<ArchetypeNode, TreeNode<ArchetypeNode>> nodeFactory, @Nullable ArchetypeNode parentValue) {
            if (parentValue == null) {
                for (List<MavenArchetype> versions : myArchetypesByKey.values()) {
                    TreeNode<ArchetypeNode> node = nodeFactory.apply(new ArchetypeNode(versions.get(0), true));
                    node.setLeaf(false);
                    node.setRenderer((item, presentation) -> {
                        presentation.append(item.archetype().groupId + ":", TextAttribute.GRAY);
                        presentation.append(item.archetype().artifactId, TextAttribute.REGULAR);
                    });
                }
            }
            else if (parentValue.group()) {
                MavenArchetype archetype = parentValue.archetype();
                List<MavenArchetype> versions = myArchetypesByKey.get(archetype.groupId + ":" + archetype.artifactId);
                if (versions == null) {
                    return;
                }

                for (MavenArchetype version : versions) {
                    TreeNode<ArchetypeNode> node = nodeFactory.apply(new ArchetypeNode(version, false));
                    node.setLeaf(true);
                    node.setRenderer((item, presentation) -> {
                        presentation.append(item.archetype().artifactId, TextAttribute.GRAY);
                        presentation.append(":" + item.archetype().version, TextAttribute.REGULAR);
                    });
                }
            }
        }
    }
}
