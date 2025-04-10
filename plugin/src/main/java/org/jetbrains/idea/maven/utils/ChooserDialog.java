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
package org.jetbrains.idea.maven.utils;

import consulo.project.Project;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.ui.ex.awt.ElementsChooser;
import consulo.ui.ex.awt.ScrollPaneFactory;
import consulo.ui.ex.awt.UIUtil;

import jakarta.annotation.Nullable;
import javax.swing.*;
import java.awt.*;

/**
 * @author Vladislav.Kaznacheev
 */
public class ChooserDialog<T> extends DialogWrapper {
    private final ElementsChooser<T> myChooser;
    private final String myDescription;

    public ChooserDialog(final Project project, ElementsChooser<T> chooser, final String title, final String description) {
        super(project, true);
        myChooser = chooser;
        myChooser.setPreferredSize(new Dimension(300, 150));
        setTitle(title);
        myDescription = description;

        init();
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        return ScrollPaneFactory.createScrollPane(myChooser);
    }

    @Override
    protected JComponent createNorthPanel() {
        JTextPane description = new JTextPane();

        JLabel label = new JLabel();
        description.setFont(label.getFont());
        description.setForeground(label.getForeground());
        description.setBackground(UIUtil.getOptionPaneBackground());
        description.setText(myDescription);

        final JPanel panel = new JPanel(new BorderLayout());
        panel.add(description);
        panel.setBorder(BorderFactory.createEmptyBorder(0, 5, 5, 5));
        return panel;
    }
}
