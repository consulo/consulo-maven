package org.jetbrains.idea.maven.navigator.structure;

import consulo.maven.rt.server.common.model.MavenProfileKind;
import consulo.navigation.Navigatable;
import consulo.navigation.NavigatableAdapter;
import consulo.ui.ex.popup.IPopupChooserBuilder;
import consulo.ui.ex.popup.JBPopupFactory;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.xml.psi.xml.XmlElement;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.dom.model.MavenDomProfile;
import org.jetbrains.idea.maven.dom.model.MavenDomProfiles;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.jetbrains.idea.maven.dom.model.MavenDomSettingsModel;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.utils.MavenUtil;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class ProfileNode extends MavenSimpleNode {
    private final String myProfileName;
    private MavenProfileKind myState;

    public ProfileNode(MavenProjectsStructure mavenProjectsStructure, ProfilesNode parent, String profileName) {
        super(mavenProjectsStructure, parent);
        myProfileName = profileName;
    }

    @Override
    public String getName() {
        return myProfileName;
    }

    public String getProfileName() {
        return myProfileName;
    }

    public MavenProfileKind getState() {
        return myState;
    }

    public void setState(MavenProfileKind state) {
        myState = state;
    }

    @Override
    @Nullable
    @NonNls
    protected String getActionId() {
        return "Maven.ToggleProfile";
    }

    @Nullable
    @Override
    public Navigatable getNavigatable() {
        final List<MavenDomProfile> profiles = ContainerUtil.newArrayList();

        // search in "Per User Maven Settings" - %USER_HOME%/.m2/settings.xml
        // and in "Global Maven Settings" - %M2_HOME%/conf/settings.xml
        for (VirtualFile virtualFile : myMavenProjectsStructure.getProjectsManager().getGeneralSettings().getEffectiveSettingsFiles()) {
            if (virtualFile != null) {
                final MavenDomSettingsModel model = MavenDomUtil.getMavenDomModel(myMavenProjectsStructure.getProject(), virtualFile, MavenDomSettingsModel.class);
                if (model != null) {
                    addProfiles(profiles, model.getProfiles().getProfiles());
                }
            }
        }

        for (MavenProject mavenProject : myMavenProjectsStructure.getProjectsManager().getProjects()) {
            // search in "Profile descriptors" - located in project basedir (profiles.xml)
            final VirtualFile mavenProjectFile = mavenProject.getFile();
            final VirtualFile profilesXmlFile = MavenUtil.findProfilesXmlFile(mavenProjectFile);
            if (profilesXmlFile != null) {
                final MavenDomProfiles profilesModel = MavenDomUtil.getMavenDomProfilesModel(myMavenProjectsStructure.getProject(), profilesXmlFile);
                if (profilesModel != null) {
                    addProfiles(profiles, profilesModel.getProfiles());
                }
            }

            // search in "Per Project" - Defined in the POM itself (pom.xml)
            final MavenDomProjectModel projectModel = MavenDomUtil.getMavenDomProjectModel(myMavenProjectsStructure.getProject(), mavenProjectFile);
            if (projectModel != null) {
                addProfiles(profiles, projectModel.getProfiles().getProfiles());
            }
        }
        return getNavigatable(profiles);
    }

    private Navigatable getNavigatable(@Nonnull final List<MavenDomProfile> profiles) {
        if (profiles.size() > 1) {
            return new NavigatableAdapter() {
                @Override
                public void navigate(final boolean requestFocus) {
                    IPopupChooserBuilder<MavenDomProfile> builder = JBPopupFactory.getInstance().createPopupChooserBuilder(profiles);
                    builder.setRenderer(new DefaultListCellRenderer() {
                        @Override
                        public Component getListCellRendererComponent(
                            JList list,
                            Object value,
                            int index,
                            boolean isSelected,
                            boolean cellHasFocus
                        ) {
                            Component result = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                            @SuppressWarnings("unchecked") MavenDomProfile mavenDomProfile = (MavenDomProfile) value;
                            XmlElement xmlElement = mavenDomProfile.getXmlElement();
                            if (xmlElement != null) {
                                setText(xmlElement.getContainingFile().getVirtualFile().getPath());
                            }
                            return result;
                        }
                    });
                    builder.setTitle("Choose file to open ");
                    builder.setItemChosenCallback(value ->
                    {
                        if (value instanceof MavenDomProfile) {
                            final Navigatable navigatable = getNavigatable(value);
                            if (navigatable != null) {
                                navigatable.navigate(requestFocus);
                            }
                        }
                    });
                    builder.createPopup().showInFocusCenter();
                }
            };
        }
        else {
            return getNavigatable(ContainerUtil.getFirstItem(profiles));
        }
    }

    @Nullable
    private Navigatable getNavigatable(@Nullable final MavenDomProfile profile) {
        if (profile == null) {
            return null;
        }
        XmlElement xmlElement = profile.getId().getXmlElement();
        return xmlElement instanceof Navigatable ? (Navigatable) xmlElement : null;
    }

    private void addProfiles(@Nonnull List<MavenDomProfile> result, @Nullable List<MavenDomProfile> profilesToAdd) {
        if (profilesToAdd == null) {
            return;
        }
        for (MavenDomProfile profile : profilesToAdd) {
            if (StringUtil.equals(profile.getId().getValue(), myProfileName)) {
                result.add(profile);
            }
        }
    }
}
