/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.dom.refactorings.introduce;

import consulo.language.codeStyle.CodeStyleSettingsManager;
import consulo.language.editor.refactoring.ui.NameSuggestionsField;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.util.collection.ArrayUtil;
import consulo.util.lang.Pair;
import consulo.util.lang.StringLenComparator;
import consulo.util.lang.StringUtil;
import consulo.xml.psi.xml.XmlElement;
import consulo.xml.psi.xml.XmlTag;
import consulo.xml.util.xml.DomElement;
import consulo.xml.util.xml.DomFileElement;
import consulo.xml.util.xml.DomUtil;
import jakarta.annotation.Nonnull;
import org.jetbrains.idea.maven.dom.MavenDomProjectProcessorUtils;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.jetbrains.idea.maven.dom.model.MavenDomProperties;
import org.jetbrains.idea.maven.dom.model.MavenDomShortArtifactCoordinates;
import org.jetbrains.idea.maven.localize.MavenDomLocalize;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.utils.ComboBoxUtil;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.*;

public class IntroducePropertyDialog extends DialogWrapper {

    private final Project myProject;
    private final XmlElement myContext;
    private final MavenDomProjectModel myMavenDomProjectModel;

    private final String mySelectedString;
    private NameSuggestionsField myNameField;
    private NameSuggestionsField.DataChanged myNameChangedListener;

    private JComboBox myMavenProjectsComboBox;
    private JPanel myMainPanel;
    private JPanel myFieldNamePanel;

    public IntroducePropertyDialog(
        @Nonnull Project project,
        @Nonnull XmlElement context,
        @Nonnull MavenDomProjectModel mavenDomProjectModel,
        @Nonnull String selectedString
    ) {
        super(project, true);
        myProject = project;
        myContext = context;
        myMavenDomProjectModel = mavenDomProjectModel;

        mySelectedString = selectedString;

        setTitle(MavenDomLocalize.refactoringIntroduceProperty());
        init();
    }

    @Override
    protected void dispose() {
        myNameField.removeDataChangedListener(myNameChangedListener);

        super.dispose();
    }

    @Nonnull
    @Override
    protected Action[] createActions() {
        return new Action[]{getOKAction(), getCancelAction()};
    }

    @Override
    protected void init() {
        super.init();
        updateOkStatus();
    }

    public String getEnteredName() {
        return myNameField.getEnteredName().trim();
    }

    @Nonnull
    public MavenDomProjectModel getSelectedProject() {
        MavenDomProjectModel selectedItem =
            (MavenDomProjectModel)ComboBoxUtil.getSelectedValue((DefaultComboBoxModel)myMavenProjectsComboBox.getModel());

        return selectedItem == null ? myMavenDomProjectModel : selectedItem;
    }

    private String[] getSuggestions() {
        return getSuggestions(1);
    }

    private String[] getSuggestions(int level) {
        Collection<String> result = new HashSet<>();

        String value = mySelectedString.trim();
        boolean addUnqualifiedForm = true;

        XmlTag parent = PsiTreeUtil.getParentOfType(myContext, XmlTag.class, false);

        DomElement domParent = DomUtil.getDomElement(parent);
        if (domParent != null) {
            DomElement domSuperParent = domParent.getParent();
            DomFileElement<DomElement> domFile = DomUtil.getFileElement(domParent);
            if (domSuperParent != null && domFile != null && domFile.getRootElement() == domSuperParent) {
                value = domSuperParent.getXmlElementName();
                addUnqualifiedForm = false;
            }
            else {
                MavenDomShortArtifactCoordinates coordinates =
                    DomUtil.getParentOfType(domParent, MavenDomShortArtifactCoordinates.class, false);
                if (coordinates != null && !(coordinates instanceof MavenDomProjectModel) && domParent != coordinates.getArtifactId()) {
                    String artifactId = coordinates.getArtifactId().getStringValue();
                    if (!StringUtil.isEmptyOrSpaces(artifactId)) {
                        value = artifactId;
                        addUnqualifiedForm = false;
                    }
                }
            }
        }

        while (true) {
            String newValue = value.replaceAll("  ", " ");
            if (newValue.equals(value)) {
                break;
            }
            value = newValue;
        }

        value = value.replaceAll(" ", ".");
        List<String> parts = StringUtil.split(value, ".");
        String shortValue = parts.get(parts.size() - 1);

        if (addUnqualifiedForm) {
            result.add(value);
            result.add(shortValue);
        }

        String suffix = "";
        while (parent != null && level != 0) {
            suffix = parent.getName() + suffix;
            result.add(suffix);
            result.add(value + "." + suffix);
            result.add(shortValue + "." + suffix);
            suffix = "." + suffix;
            parent = parent.getParentTag();
            level--;
        }

        List<String> listResult = new ArrayList<>(result);
        Collections.sort(
            listResult,
            CodeStyleSettingsManager.getSettings(myProject).PREFER_LONGER_NAMES
                ? StringLenComparator.getDescendingInstance()
                : StringLenComparator.getInstance()
        );
        return ArrayUtil.toStringArray(listResult);
    }

    private static String joinWords(@Nonnull String s, @Nonnull String delimiter) {
        return joinWords(StringUtil.split(s, delimiter));
    }

    private static String joinWords(@Nonnull List<String> stringList) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < stringList.size(); i++) {
            String word = stringList.get(i);
            if (!StringUtil.isEmptyOrSpaces(word)) {
                sb.append(i == 0 ? StringUtil.decapitalize(word.trim()) : StringUtil.capitalize(word.trim()));
            }
        }
        return sb.toString();
    }

    @Override
    protected JComponent createCenterPanel() {
        myFieldNamePanel.setLayout(new BorderLayout());

        myNameField = new NameSuggestionsField(myProject);
        myNameChangedListener = this::updateOkStatus;
        myNameField.addDataChangedListener(myNameChangedListener);
        myNameField.setSuggestions(getSuggestions());

        myFieldNamePanel.add(myNameField, BorderLayout.CENTER);

        List<MavenDomProjectModel> projects = getProjects();

        ComboBoxUtil.setModel(
            myMavenProjectsComboBox,
            new DefaultComboBoxModel(),
            projects,
            model -> {
                String projectName = model.getName().getStringValue();
                MavenProject mavenProject = MavenDomUtil.findProject(model);
                if (mavenProject != null) {
                    projectName = mavenProject.getDisplayName();
                }
                if (StringUtil.isEmptyOrSpaces(projectName)) {
                    projectName = "pom.xml";
                }
                return Pair.create(projectName, model);
            }
        );

        myMavenProjectsComboBox.setSelectedItem(myMavenDomProjectModel);

        return myMainPanel;
    }


    private List<MavenDomProjectModel> getProjects() {
        List<MavenDomProjectModel> projects = new ArrayList<>();

        projects.add(myMavenDomProjectModel);
        projects.addAll(MavenDomProjectProcessorUtils.collectParentProjects(myMavenDomProjectModel));

        return projects;
    }

    private void updateOkStatus() {
        String text = getEnteredName();

        setOKActionEnabled(!StringUtil.isEmptyOrSpaces(text) && !isContainWrongSymbols(text) && !isPropertyExist(text));
    }

    private static boolean isContainWrongSymbols(@Nonnull String text) {
        return text.length() == 0
            || Character.isDigit(text.charAt(0))
            || StringUtil.containsAnyChar(text, "\t ;*'\"\\/,()^&<>={}[]");
    }

    private boolean isPropertyExist(@Nonnull String text) {
        MavenDomProjectModel project = getSelectedProject();

        if (isPropertyExist(text, project)) {
            return true;
        }

        for (MavenDomProjectModel child : MavenDomProjectProcessorUtils.getChildrenProjects(project)) {
            if (isPropertyExist(text, child)) {
                return true;
            }
        }

        for (MavenDomProjectModel parent : MavenDomProjectProcessorUtils.collectParentProjects(project)) {
            if (isPropertyExist(text, parent)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPropertyExist(String propertyName, MavenDomProjectModel project) {
        MavenDomProperties props = project.getProperties();

        XmlTag propsTag = props.getXmlTag();
        if (propsTag != null) {
            for (XmlTag each : propsTag.getSubTags()) {
                if (propertyName.equals(each.getName())) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    @RequiredUIAccess
    public JComponent getPreferredFocusedComponent() {
        return myNameField.getFocusableComponent();
    }
}