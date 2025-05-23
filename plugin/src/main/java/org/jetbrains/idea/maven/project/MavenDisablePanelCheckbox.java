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

import consulo.ui.ex.JBColor;
import consulo.ui.ex.awt.JBUI;
import consulo.util.lang.Pair;
import jakarta.annotation.Nonnull;

import javax.swing.*;
import java.awt.*;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Sergey Evdokimov
 */
public class MavenDisablePanelCheckbox extends JCheckBox {
    private final JComponent myPanel;
    private Set<JComponent> myDisabledComponents;

    public MavenDisablePanelCheckbox(String text, @Nonnull JComponent panel) {
        super(text);
        myPanel = panel;

        addChangeListener(e -> {
            if (MavenDisablePanelCheckbox.this.isSelected()) {
                if (myDisabledComponents == null) {
                    myDisabledComponents = new HashSet<>();
                    disable(myPanel);
                }
            }
            else if (myDisabledComponents != null) {
                enable(myPanel);
                myDisabledComponents = null;
            }
        });
    }

    private void disable(JComponent c) {
        if (c.isEnabled()) {
            myDisabledComponents.add(c);
            c.setEnabled(false);
        }

        for (Component component : c.getComponents()) {
            if (component instanceof JComponent jComponent) {
                disable(jComponent);
            }
        }
    }

    private void enable(JComponent c) {
        if (myDisabledComponents.contains(c)) {
            c.setEnabled(true);
        }

        for (Component component : c.getComponents()) {
            if (component instanceof JComponent jComponent) {
                enable(jComponent);
            }
        }
    }

    public static Pair<JPanel, JCheckBox> createPanel(JComponent component, String title) {
        JPanel panel = new JPanel(new BorderLayout()) {
            @Override
            public void setEnabled(boolean enabled) {
                super.setEnabled(enabled);
                Color c = enabled ? JBColor.GRAY : JBColor.LIGHT_GRAY;
                setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(1, 0, 0, 0, c),
                    BorderFactory.createEmptyBorder(10, 0, 0, 0)
                ));
            }
        };
        panel.setEnabled(true);
        panel.add(component);

        JCheckBox checkbox = new MavenDisablePanelCheckbox(title, panel);

        JPanel res = new JPanel(new BorderLayout(0, 10));
        res.setBorder(JBUI.Borders.empty(5, 0, 0, 0));
        res.add(checkbox, BorderLayout.NORTH);
        res.add(panel, BorderLayout.CENTER);

        return Pair.create(res, checkbox);
    }
}
