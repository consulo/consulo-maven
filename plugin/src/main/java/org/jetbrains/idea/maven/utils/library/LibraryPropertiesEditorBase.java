/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.utils.library;

import jakarta.annotation.Nonnull;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

import consulo.content.library.LibraryProperties;
import consulo.content.library.LibraryType;
import consulo.content.library.ui.LibraryPropertiesEditor;
import consulo.content.library.ui.LibraryEditorComponent;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.UIUtil;
import jakarta.annotation.Nullable;

/**
 * @author nik
 */
public abstract class LibraryPropertiesEditorBase<P extends LibraryProperties, T extends LibraryType<P>> extends LibraryPropertiesEditor {
    private JPanel myMainPanel;
    private JLabel myDescriptionLabel;
    private JButton myEditButton;
    private boolean myModified;
    protected final LibraryEditorComponent<P> myEditorComponent;
    protected final T myLibraryType;

    protected LibraryPropertiesEditorBase(final LibraryEditorComponent<P> editorComponent, T libraryType, @Nullable String editButtonText) {
        myEditorComponent = editorComponent;
        myLibraryType = libraryType;
        updateDescription();
        if (editButtonText != null) {
            myEditButton.setText(UIUtil.replaceMnemonicAmpersand(editButtonText));
        }
        myEditButton.setVisible(!myEditorComponent.isNewLibrary());
        myEditButton.addActionListener(e -> edit());
    }

    protected JPanel getMainPanel() {
        return myMainPanel;
    }

    protected void updateDescription() {
        myDescriptionLabel.setText(myLibraryType.getDescription(myEditorComponent.getProperties()));
    }

    protected abstract void edit();

    protected void setModified() {
        myModified = true;
        updateDescription();
    }

    @Nonnull
    @Override
    @RequiredUIAccess
    public JComponent createComponent() {
        return myMainPanel;
    }

    @Override
    @RequiredUIAccess
    public boolean isModified() {
        return myModified;
    }

    @Override
    @RequiredUIAccess
    public void reset() {
        updateDescription();
    }
}