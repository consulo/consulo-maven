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
import consulo.localize.LocalizeValue;
import consulo.maven.importProvider.MavenImportModuleContext;
import consulo.maven.rt.server.common.model.MavenExplicitProfiles;
import consulo.ui.Component;
import consulo.ui.ComponentItemRender;
import consulo.ui.Label;
import consulo.ui.Table;
import consulo.ui.TableItemEditor;
import consulo.ui.TriStateCheckBox;
import consulo.ui.ValueComponent;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.wizard.WizardStep;
import consulo.ui.layout.DockLayout;
import consulo.ui.layout.ScrollableLayout;
import consulo.ui.model.FlatDataModel;
import consulo.ui.model.MutableFlatDataModel;
import consulo.util.lang.ThreeState;
import org.jetbrains.idea.maven.localize.MavenProjectLocalize;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Vladislav.Kaznacheev
 */
public class SelectProfilesStep implements WizardStep<MavenImportModuleContext> {
    private final MavenImportModuleContext myContext;

    /**
     * Source of truth for the checkbox column - the table reads through it, so it survives the step
     * being entered before its component is built.
     */
    private final Map<String, ThreeState> myProfileStates = new LinkedHashMap<>();

    /**
     * Profiles turned on by their own activation rules. Only those may go indeterminate, the rest
     * toggle between on and off.
     */
    private final Set<String> myActivatedProfiles = new LinkedHashSet<>();

    private @Nullable MutableFlatDataModel<String> myModel;

    public SelectProfilesStep(MavenImportModuleContext context) {
        myContext = context;
    }

    @Override
    public boolean isVisible(MavenImportModuleContext context) {
        return !myContext.getProfiles().isEmpty();
    }

    @RequiredUIAccess
    @Override
    public Component getComponent(MavenImportModuleContext context, Disposable uiDisposable) {
        MutableFlatDataModel<String> model = FlatDataModel.of(new ArrayList<>(myProfileStates.keySet()));
        myModel = model;

        Table<String> table = Table.create(model);
        table.setShowHeader(false);

        table.addColumn(LocalizeValue.empty(), this::getState)
            .setWidth(40)
            .setRender(ComponentItemRender.reusable(
                () -> TriStateCheckBox.create(LocalizeValue.empty()),
                (checkBox, item) -> checkBox.setValue(item.getValue() == null ? ThreeState.NO : item.getValue())))
            .setEditor(new TableItemEditor<>() {
                @RequiredUIAccess
                @Override
                public ValueComponent<ThreeState> createComponent(String profile) {
                    TriStateCheckBox checkBox = TriStateCheckBox.create(LocalizeValue.empty(), getState(profile));
                    checkBox.setUnsureEnabled(myActivatedProfiles.contains(profile));
                    return checkBox;
                }

                @RequiredUIAccess
                @Override
                public void commit(String profile, @Nullable ThreeState value) {
                    myProfileStates.put(profile, value == null ? ThreeState.NO : value);
                    model.update(profile);
                }
            });

        table.addColumn(LocalizeValue.empty(), profile -> profile);

        DockLayout root = DockLayout.create();
        root.top(Label.create(MavenProjectLocalize.mavenImportLabelSelectProfiles()));
        root.center(ScrollableLayout.create(table));
        return root;
    }

    private ThreeState getState(String profile) {
        return myProfileStates.getOrDefault(profile, ThreeState.NO);
    }

    @RequiredUIAccess
    @Override
    public void onStepEnter(MavenImportModuleContext context) {
        List<String> allProfiles = myContext.getProfiles();

        myActivatedProfiles.clear();
        myActivatedProfiles.addAll(myContext.getActivatedProfiles());
        myActivatedProfiles.retainAll(allProfiles);

        MavenExplicitProfiles selectedProfiles = myContext.getSelectedProfiles();
        Collection<String> enabledProfiles = selectedProfiles.getEnabledProfiles();
        Collection<String> disabledProfiles = selectedProfiles.getDisabledProfiles();

        myProfileStates.clear();
        for (String profile : allProfiles) {
            myProfileStates.put(profile, initialState(profile, enabledProfiles, disabledProfiles));
        }

        MutableFlatDataModel<String> model = myModel;
        if (model != null) {
            model.replaceAll(allProfiles);
        }
    }

    /**
     * An explicitly enabled profile is on, an explicitly disabled one is off, and anything left is
     * shown the way it would be resolved without a choice - indeterminate when its activation rules
     * turn it on, off otherwise.
     */
    private ThreeState initialState(String profile, Collection<String> enabledProfiles, Collection<String> disabledProfiles) {
        if (enabledProfiles.contains(profile)) {
            return ThreeState.YES;
        }
        if (disabledProfiles.contains(profile)) {
            return ThreeState.NO;
        }
        return myActivatedProfiles.contains(profile) ? ThreeState.UNSURE : ThreeState.NO;
    }

    @Override
    public void onStepLeave(MavenImportModuleContext context) {
        MavenExplicitProfiles newSelectedProfiles = MavenExplicitProfiles.NONE.clone();

        for (Map.Entry<String, ThreeState> entry : myProfileStates.entrySet()) {
            String profile = entry.getKey();
            switch (entry.getValue()) {
                case YES -> newSelectedProfiles.getEnabledProfiles().add(profile);
                // only worth recording when something has to be turned back off
                case NO -> {
                    if (myActivatedProfiles.contains(profile)) {
                        newSelectedProfiles.getDisabledProfiles().add(profile);
                    }
                }
                case UNSURE -> {
                }
            }
        }

        myContext.setSelectedProfiles(newSelectedProfiles);
    }
}
