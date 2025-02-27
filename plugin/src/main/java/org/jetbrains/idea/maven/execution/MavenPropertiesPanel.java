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
package org.jetbrains.idea.maven.execution;

import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.AddEditRemovePanel;
import consulo.util.lang.Couple;
import consulo.util.lang.Pair;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * @author Sergey Evdokimov
 */
public class MavenPropertiesPanel extends AddEditRemovePanel<Pair<String, String>> {
    private Map<String, String> myAvailableProperties;

    public MavenPropertiesPanel(Map<String, String> availableProperties) {
        super(new MyPropertiesTableModel(), new ArrayList<>(), null);
        setPreferredSize(new Dimension(100, 100));
        myAvailableProperties = availableProperties;
    }

    protected Pair<String, String> addItem() {
        return doAddOrEdit(null);
    }

    protected boolean removeItem(Pair<String, String> o) {
        return true;
    }

    protected Pair<String, String> editItem(@Nonnull Pair<String, String> o) {
        return doAddOrEdit(o);
    }

    @Nullable
    @RequiredUIAccess
    private Pair<String, String> doAddOrEdit(@Nullable Pair<String, String> o) {
        EditMavenPropertyDialog d = new EditMavenPropertyDialog(o, myAvailableProperties);
        d.show();
        if (!d.isOK()) {
            return null;
        }
        return d.getValue();
    }

    public Map<String, String> getDataAsMap() {
        Map<String, String> result = new LinkedHashMap<>();
        for (Pair<String, String> p : getData()) {
            result.put(p.getFirst(), p.getSecond());
        }
        return result;
    }

    public void setDataFromMap(Map<String, String> map) {
        List<Pair<String, String>> result = new ArrayList<>();
        for (Map.Entry<String, String> e : map.entrySet()) {
            result.add(Couple.of(e.getKey(), e.getValue()));
        }
        setData(result);
    }

    private static class MyPropertiesTableModel extends AddEditRemovePanel.TableModel<Pair<String, String>> {
        @Override
        public int getColumnCount() {
            return 2;
        }

        @Override
        public String getColumnName(int c) {
            return c == 0 ? "Name" : "Value";
        }

        @Override
        public Object getField(Pair<String, String> o, int c) {
            return c == 0 ? o.getFirst() : o.getSecond();
        }
    }
}
