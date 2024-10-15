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
package org.jetbrains.idea.maven.execution;

import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.util.collection.ArrayUtil;
import consulo.util.lang.Couple;
import consulo.util.lang.Pair;
import jakarta.annotation.Nullable;

import javax.swing.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.ItemEvent;
import java.util.Arrays;
import java.util.Map;

public class EditMavenPropertyDialog extends DialogWrapper {
    private JPanel contentPane;
    private JComboBox myNameBox;
    private JTextField myValueField;
    private final Map<String, String> myAvailableProperties;

    public EditMavenPropertyDialog(@Nullable Pair<String, String> value, Map<String, String> availableProperties) {
        super(false);
        setTitle(value == null ? "Add Maven Property" : "Edit Maven Property");

        myAvailableProperties = availableProperties;

        installFocusListeners();
        fillAvailableProperties();

        if (value != null) {
            myNameBox.getEditor().setItem(value.getFirst());
            myValueField.setText(value.getSecond());
        }

        installPropertySelectionListener();

        init();
    }

    private void installFocusListeners() {
        myNameBox.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                myNameBox.getEditor().selectAll();
            }
        });
        myValueField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                myValueField.selectAll();
            }
        });
    }

    private void installPropertySelectionListener() {
        myNameBox.addItemListener(e -> {
            if (e.getStateChange() != ItemEvent.SELECTED) {
                return;
            }
            String key = (String)e.getItem();
            String value = myAvailableProperties.get(key);
            if (value != null) {
                myValueField.setText(value);
            }
        });
    }

    private void fillAvailableProperties() {
        String[] keys = ArrayUtil.toStringArray(myAvailableProperties.keySet());
        Arrays.sort(keys);
        myNameBox.setModel(new DefaultComboBoxModel(keys));
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        return contentPane;
    }

    @Override
    @RequiredUIAccess
    public JComponent getPreferredFocusedComponent() {
        return myNameBox;
    }

    public Pair<String, String> getValue() {
        return Couple.of((String)myNameBox.getEditor().getItem(), myValueField.getText());
    }
}
