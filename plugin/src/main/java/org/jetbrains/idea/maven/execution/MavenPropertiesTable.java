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

import consulo.ui.Component;
import consulo.ui.Table;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.ActionToolbarPosition;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.toolbar.AddAction;
import consulo.ui.ex.toolbar.EditAction;
import consulo.ui.ex.toolbar.RemoveAction;
import consulo.ui.ex.toolbar.ToolbarDecoratorBuilder;
import consulo.ui.model.FlatDataModel;
import consulo.ui.model.MutableFlatDataModel;
import consulo.util.lang.Couple;
import consulo.util.lang.Pair;
import org.jetbrains.idea.maven.localize.MavenProjectLocalize;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Name/value table of maven properties, on the unified ui. Replaces the swing {@code AddEditRemovePanel}
 * based panel.
 *
 * @author Sergey Evdokimov
 */
public class MavenPropertiesTable {
    private final Map<String, String> myAvailableProperties;

    private final MutableFlatDataModel<Pair<String, String>> myModel = FlatDataModel.of(new ArrayList<>());
    private final Table<Pair<String, String>> myTable;
    private final Component myComponent;

    @RequiredUIAccess
    public MavenPropertiesTable(Map<String, String> availableProperties) {
        myAvailableProperties = availableProperties;

        myTable = Table.create(myModel);
        // Pair::getFirst / Pair::getSecond are ambiguous - there is a static overload of each
        myTable.addColumn(MavenProjectLocalize.mavenPropertyTableName(), row -> row.getFirst());
        myTable.addColumn(MavenProjectLocalize.mavenPropertyTableValue(), row -> row.getSecond());

        myComponent = ToolbarDecoratorBuilder.newBuilder(myTable)
            .withToolbarPosition(ActionToolbarPosition.TOP)
            .addOrReplaceAction(new AddAction<>() {
                @RequiredUIAccess
                @Override
                protected void doAdd(AnActionEvent e) {
                    Pair<String, String> property = editProperty(null);
                    if (property != null) {
                        myModel.add(property);
                    }
                }
            })
            .addOrReplaceAction(new EditAction<Pair<String, String>>() {
                @RequiredUIAccess
                @Override
                protected void doEdit(Pair<String, String> selected, AnActionEvent e) {
                    Pair<String, String> property = editProperty(selected);
                    if (property != null) {
                        replace(selected, property);
                    }
                }
            })
            .addOrReplaceAction(new RemoveAction<Pair<String, String>>() {
                @RequiredUIAccess
                @Override
                protected void doRemove(Pair<String, String> selected, AnActionEvent e) {
                    myModel.remove(selected);
                }
            })
            .build();
    }

    public Component getComponent() {
        return myComponent;
    }

    @Nullable
    @RequiredUIAccess
    private Pair<String, String> editProperty(@Nullable Pair<String, String> property) {
        EditMavenPropertyDialog dialog = new EditMavenPropertyDialog(property, myAvailableProperties);
        dialog.show();
        return dialog.isOK() ? dialog.getValue() : null;
    }

    /**
     * The model keeps the row order, so an edit has to land back where the old row was rather than at the end.
     */
    @RequiredUIAccess
    private void replace(Pair<String, String> old, Pair<String, String> updated) {
        List<Pair<String, String>> rows = getData();
        int index = rows.indexOf(old);
        if (index < 0) {
            myModel.add(updated);
            return;
        }

        rows.set(index, updated);
        myModel.replaceAll(rows);
    }

    private List<Pair<String, String>> getData() {
        List<Pair<String, String>> rows = new ArrayList<>(myModel.getSize());
        for (Pair<String, String> row : myModel) {
            rows.add(row);
        }
        return rows;
    }

    public Map<String, String> getDataAsMap() {
        Map<String, String> result = new LinkedHashMap<>();
        for (Pair<String, String> row : myModel) {
            result.put(row.getFirst(), row.getSecond());
        }
        return result;
    }

    @RequiredUIAccess
    public void setDataFromMap(Map<String, String> map) {
        List<Pair<String, String>> rows = new ArrayList<>(map.size());
        for (Map.Entry<String, String> entry : map.entrySet()) {
            rows.add(Couple.of(entry.getKey(), entry.getValue()));
        }
        myModel.replaceAll(rows);
    }
}
