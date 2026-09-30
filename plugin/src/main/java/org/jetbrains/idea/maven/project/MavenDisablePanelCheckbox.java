/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.project;

import consulo.ui.CheckBox;
import consulo.ui.Component;
import consulo.ui.Separator;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.layout.DockLayout;

/**
 * @author Sergey Evdokimov
 */
public final class MavenDisablePanelCheckbox {
    private MavenDisablePanelCheckbox() {
    }

    @RequiredUIAccess
    public static Component createPanel(Component component, CheckBox checkbox) {
        component.setEnabled(!checkbox.getValueOrError());
        checkbox.addValueListener(event -> component.setEnabled(!Boolean.TRUE.equals(event.getValue())));

        DockLayout panel = DockLayout.create();
        panel.top(Separator.horizontal());
        panel.center(component);

        DockLayout layout = DockLayout.create();
        layout.top(checkbox);
        layout.center(panel);
        return layout;
    }
}
