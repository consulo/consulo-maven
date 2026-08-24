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
import consulo.maven.rt.server.common.model.MavenArchetype;
import consulo.ui.AdvancedLabel;
import consulo.ui.Component;
import consulo.ui.TextAttribute;
import consulo.ui.TextBox;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.dialog.DialogDescriptor;
import consulo.ui.util.FormBuilder;
import consulo.util.lang.StringUtil;
import org.jetbrains.idea.maven.localize.MavenProjectLocalize;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Asks for the coordinates of an archetype which is not in any index, so it can be registered by hand.
 */
public class MavenAddArchetypeDialog extends DialogDescriptor {
    private final TextBox myGroupIdBox = TextBox.create();
    private final TextBox myArtifactIdBox = TextBox.create();
    private final TextBox myVersionBox = TextBox.create();
    private final TextBox myRepositoryBox = TextBox.create();

    private @Nullable AdvancedLabel myErrorLabel;

    public MavenAddArchetypeDialog() {
        super(MavenProjectLocalize.mavenWizardAddArchetypeTitle());
    }

    @Override
    public String getHelpId() {
        return "Add_Archetype_Dialog";
    }

    @RequiredUIAccess
    @Override
    public Component createCenterComponent(Disposable uiDisposable) {
        AdvancedLabel errorLabel = myErrorLabel = AdvancedLabel.create();

        for (TextBox box : List.of(myGroupIdBox, myArtifactIdBox, myVersionBox, myRepositoryBox)) {
            box.addValueListener(event -> validateInput());
        }

        FormBuilder builder = FormBuilder.create();
        builder.addLabeled(MavenProjectLocalize.mavenWizardGroupId(), myGroupIdBox);
        builder.addLabeled(MavenProjectLocalize.mavenWizardArtifactId(), myArtifactIdBox);
        builder.addLabeled(MavenProjectLocalize.mavenWizardVersion(), myVersionBox);
        builder.addLabeled(MavenProjectLocalize.mavenWizardRepositoryOptional(), myRepositoryBox);
        builder.addBottom(errorLabel);

        Component component = builder.build();

        validateInput();

        return component;
    }

    @RequiredUIAccess
    @Override
    public Component getPreferredFocusedComponent() {
        return myGroupIdBox;
    }

    @Override
    public boolean doUpdateOkButtonState() {
        return collectMissingFields().isEmpty();
    }

    /**
     * @return the display names of the required fields which are still empty, in field order.
     */
    @RequiredUIAccess
    private List<String> collectMissingFields() {
        List<String> errors = new ArrayList<>();
        if (StringUtil.isEmptyOrSpaces(myGroupIdBox.getValue())) {
            errors.add(MavenProjectLocalize.mavenWizardGroupId().get());
        }
        if (StringUtil.isEmptyOrSpaces(myArtifactIdBox.getValue())) {
            errors.add(MavenProjectLocalize.mavenWizardArtifactId().get());
        }
        if (StringUtil.isEmptyOrSpaces(myVersionBox.getValue())) {
            errors.add(MavenProjectLocalize.mavenWizardVersion().get());
        }
        return errors;
    }

    @RequiredUIAccess
    private void validateInput() {
        AdvancedLabel errorLabel = myErrorLabel;
        if (errorLabel == null) {
            return;
        }

        List<String> errors = collectMissingFields();

        LocalizeValue message = errors.isEmpty()
            ? LocalizeValue.empty()
            : MavenProjectLocalize.mavenWizardSpecifyFields(StringUtil.join(errors, ", "));

        errorLabel.updatePresentation(presentation -> {
            presentation.clearText();
            presentation.append(message, TextAttribute.ERROR);
        });

        updateOkButtonState();
    }

    @RequiredUIAccess
    public MavenArchetype getArchetype() {
        return new MavenArchetype(
            StringUtil.notNullize(myGroupIdBox.getValue()),
            StringUtil.notNullize(myArtifactIdBox.getValue()),
            StringUtil.notNullize(myVersionBox.getValue()),
            StringUtil.notNullize(myRepositoryBox.getValue()),
            null
        );
    }
}
