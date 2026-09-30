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
package org.jetbrains.idea.maven.indices;

import consulo.application.util.DateFormatUtil;
import consulo.configurable.Configurable;
import consulo.configurable.ConfigurationException;
import consulo.configurable.SearchableConfigurable;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.localize.LocalizeValue;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.project.Project;
import consulo.ui.Button;
import consulo.ui.Component;
import consulo.ui.InputBoxBuilder;
import consulo.ui.InputProblem;
import consulo.ui.ListBox;
import consulo.ui.MessageBoxes;
import consulo.ui.SelectionMode;
import consulo.ui.Space;
import consulo.ui.Table;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.ActionToolbarPosition;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.AnActionWithSyncUpdate;
import consulo.ui.ex.action.DumbAwareAction;
import consulo.ui.ex.toolbar.AddAction;
import consulo.ui.ex.toolbar.DownMoveAction;
import consulo.ui.ex.toolbar.EditAction;
import consulo.ui.ex.toolbar.RemoveAction;
import consulo.ui.ex.toolbar.ToolbarDecoratorBuilderFactory;
import consulo.ui.ex.toolbar.UpMoveAction;
import consulo.ui.image.Image;
import consulo.ui.layout.DockLayout;
import consulo.ui.layout.LabeledLayout;
import consulo.ui.layout.ScrollableLayout;
import consulo.ui.layout.SplitLayoutPosition;
import consulo.ui.layout.TwoComponentSplitLayout;
import consulo.ui.layout.VerticalLayout;
import consulo.ui.model.FlatDataModel;
import consulo.ui.model.MutableFlatDataModel;
import consulo.ui.style.StandardColors;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import org.jetbrains.idea.maven.localize.MavenIndicesLocalize;
import org.jetbrains.idea.maven.services.MavenRepositoryServicesManager;
import org.jetbrains.idea.maven.utils.library.RepositoryAttachHandler;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class MavenRepositoriesConfigurable implements SearchableConfigurable, Configurable.NoScroll {
    private final Project myProject;
    private final MavenProjectIndicesManager myManager;

    private final MutableFlatDataModel<MavenIndex> myIndices = FlatDataModel.of(List.of());
    private final Map<MavenIndex, MavenIndicesManager.IndexUpdatingState> myUpdatingStates = new HashMap<>();
    private final MutableFlatDataModel<String> myServiceUrls = FlatDataModel.of(List.of());

    private @Nullable Table<MavenIndex> myIndicesTable;
    private @Nullable Button myUpdateButton;
    private @Nullable ListBox<String> myServiceList;
    private @Nullable ScheduledFuture<?> myStateRefresh;

    private volatile @Nullable String mySelectedServiceUrl;
    private volatile boolean myTestingService;

    public MavenRepositoriesConfigurable(Project project) {
        myProject = project;
        myManager = MavenProjectIndicesManager.getInstance(project);
    }

    @Override
    @RequiredUIAccess
    public Component createUIComponent(@Nonnull Disposable uiDisposable) {
        Table<MavenIndex> indicesTable = Table.create(myIndices);
        indicesTable.setSelectionMode(SelectionMode.MULTIPLE);
        indicesTable.addColumn(MavenIndicesLocalize.mavenIndexUrl(), MavenIndex::getRepositoryPathOrUrl);
        indicesTable.addColumn(MavenIndicesLocalize.mavenIndexType(), MavenRepositoriesConfigurable::getKindText)
            .setRender((presentation, item) -> presentation.append(item.getValue()));
        indicesTable.addColumn(MavenIndicesLocalize.mavenIndexUpdated(), MavenRepositoriesConfigurable::getUpdatedText)
            .setRender((presentation, item) -> presentation.append(item.getValue()));
        indicesTable.addColumn(LocalizeValue.empty(), this::getUpdatingState)
            .setWidth(40)
            .setRender((presentation, item) -> {
                Image icon = getStateIcon(item.getValue());
                if (icon != null) {
                    presentation.withIcon(icon);
                }
            });
        indicesTable.setRowBackgroundGetter(index -> index.getFailureMessage() == null ? null : StandardColors.LIGHT_RED);
        indicesTable.addSelectListener(event -> updateButtonsState());
        myIndicesTable = indicesTable;

        Button updateButton = Button.create(MavenIndicesLocalize.mavenRepositoriesUpdate(), event -> doUpdateIndex());
        updateButton.setEnabled(false);
        myUpdateButton = updateButton;

        ListBox<String> serviceList = ListBox.create(myServiceUrls);
        serviceList.setPlaceholder(MavenIndicesLocalize.mavenRepositoriesServicesEmpty());
        serviceList.addValueListener(event -> mySelectedServiceUrl = event.getValue());
        myServiceList = serviceList;

        Component servicesPanel = ToolbarDecoratorBuilderFactory.getInstance()
            .create(serviceList)
            .addOrReplaceAction(new ServiceAddAction())
            .addOrReplaceAction(new ServiceEditAction())
            .addOrReplaceAction(new ServiceRemoveAction())
            .addExtraAction(new ServiceTestAction())
            .disableAction(UpMoveAction.class)
            .disableAction(DownMoveAction.class)
            .withToolbarPosition(ActionToolbarPosition.TOP)
            .build();

        ScheduledFuture<?> stateRefresh = myProject.getUIAccess()
            .getScheduler()
            .scheduleWithFixedDelay(this::refreshUpdatingStates, 500, 500, TimeUnit.MILLISECONDS);
        myStateRefresh = stateRefresh;
        Disposer.register(uiDisposable, () -> stateRefresh.cancel(false));

        DockLayout indicesPanel = DockLayout.create(Space.SMALL)
            .center(ScrollableLayout.create(indicesTable))
            .right(VerticalLayout.create().add(updateButton));

        return TwoComponentSplitLayout.create(SplitLayoutPosition.VERTICAL)
            .withFirstComponent(LabeledLayout.create(MavenIndicesLocalize.mavenRepositoriesIndexed(), DockLayout.create().center(indicesPanel)))
            .withSecondComponent(LabeledLayout.create(MavenIndicesLocalize.mavenRepositoriesServices(), DockLayout.create().center(servicesPanel)))
            .withProportion(60);
    }

    @Override
    @RequiredUIAccess
    public boolean isModified() {
        return myServiceList != null && !getServiceUrls().equals(MavenRepositoryServicesManager.getInstance().getUrls());
    }

    @Override
    @RequiredUIAccess
    public void apply() throws ConfigurationException {
        if (myServiceList != null) {
            MavenRepositoryServicesManager.getInstance().setUrls(getServiceUrls());
        }
    }

    @Override
    @RequiredUIAccess
    public void reset() {
        myServiceUrls.replaceAll(MavenRepositoryServicesManager.getInstance().getUrls());

        myUpdatingStates.clear();
        myIndices.replaceAll(myManager.getIndices());
        refreshUpdatingStates();
        updateButtonsState();
    }

    @Override
    @RequiredUIAccess
    public void disposeUIResources() {
        ScheduledFuture<?> stateRefresh = myStateRefresh;
        if (stateRefresh != null) {
            stateRefresh.cancel(false);
            myStateRefresh = null;
        }

        myIndicesTable = null;
        myUpdateButton = null;
        myServiceList = null;
    }

    public void updateIndexHint(int row) {
    }

    @RequiredUIAccess
    private void refreshUpdatingStates() {
        for (MavenIndex index : myIndices) {
            MavenIndicesManager.IndexUpdatingState state = myManager.getUpdatingState(index);
            if (myUpdatingStates.put(index, state) != state) {
                myIndices.update(index);
            }
        }
    }

    private MavenIndicesManager.IndexUpdatingState getUpdatingState(MavenIndex index) {
        return myUpdatingStates.getOrDefault(index, MavenIndicesManager.IndexUpdatingState.IDLE);
    }

    @RequiredUIAccess
    private void updateButtonsState() {
        Table<MavenIndex> indicesTable = myIndicesTable;
        Button updateButton = myUpdateButton;
        if (indicesTable != null && updateButton != null) {
            updateButton.setEnabled(!indicesTable.getSelectedItems().isEmpty());
        }
    }

    @RequiredUIAccess
    private void doUpdateIndex() {
        Table<MavenIndex> indicesTable = myIndicesTable;
        if (indicesTable != null) {
            myManager.scheduleUpdate(new ArrayList<>(indicesTable.getSelectedItems()));
        }
    }

    private List<String> getServiceUrls() {
        List<String> urls = new ArrayList<>();
        for (String url : myServiceUrls) {
            urls.add(url);
        }
        return urls;
    }

    private CompletableFuture<String> askServiceUrl(Component owner, LocalizeValue title, String value) {
        return InputBoxBuilder.text()
            .title(title)
            .text(MavenIndicesLocalize.mavenRepositoriesServiceUrl())
            .value(value)
            .validator(MavenRepositoriesConfigurable::validateServiceUrl)
            .asQuestion()
            .showAsync(owner);
    }

    @RequiredUIAccess
    private void testServiceConnection(Component owner, String url) {
        myTestingService = true;
        RepositoryAttachHandler.searchRepositories(myProject, List.of(url), infos -> {
            myProject.getUIAccess().give(() -> {
                myTestingService = false;
                if (infos.isEmpty()) {
                    MessageBoxes.okWarning(MavenIndicesLocalize.mavenRepositoriesServiceTestFailed())
                        .title(MavenIndicesLocalize.mavenRepositoriesServiceTestFailedTitle())
                        .showAsync(owner);
                }
                else {
                    MessageBoxes.okInfo(MavenIndicesLocalize.mavenRepositoriesServiceTestSuccess(infos.size()))
                        .title(MavenIndicesLocalize.mavenRepositoriesServiceTestSuccessTitle())
                        .showAsync(owner);
                }
            });
            return true;
        });
    }

    private static @Nullable InputProblem validateServiceUrl(@Nullable String text) {
        if (text != null) {
            try {
                URI uri = new URI(text);
                if (uri.getScheme() != null && StringUtil.isNotEmpty(uri.getHost())) {
                    return null;
                }
            }
            catch (URISyntaxException ignored) {
            }
        }
        return InputProblem.error(MavenIndicesLocalize.mavenRepositoriesServiceUrlInvalid());
    }

    private static LocalizeValue getKindText(MavenIndex index) {
        return index.getKind() == MavenIndex.Kind.LOCAL
            ? MavenIndicesLocalize.mavenIndexKindLocal()
            : MavenIndicesLocalize.mavenIndexKindRemote();
    }

    private static LocalizeValue getUpdatedText(MavenIndex index) {
        if (index.getFailureMessage() != null) {
            return MavenIndicesLocalize.mavenIndexUpdatedError();
        }

        long timestamp = index.getUpdateTimestamp();
        if (timestamp == -1) {
            return MavenIndicesLocalize.mavenIndexUpdatedNever();
        }
        return LocalizeValue.of(DateFormatUtil.formatDate(timestamp));
    }

    private static @Nullable Image getStateIcon(MavenIndicesManager.@Nullable IndexUpdatingState state) {
        if (state == null) {
            return null;
        }

        return switch (state) {
            case UPDATING -> Image.busy();
            case WAITING -> PlatformIconGroup.processStep_passive();
            case IDLE -> null;
        };
    }

    @Override
    public LocalizeValue getDisplayName() {
        return MavenIndicesLocalize.mavenRepositoriesTitle();
    }

    @Override
    public String getHelpTopic() {
        return "reference.settings.project.maven.repository.indices";
    }

    @Nonnull
    @Override
    public String getId() {
        return getHelpTopic();
    }

    private class ServiceAddAction extends AddAction<String> {
        @Override
        @RequiredUIAccess
        protected void doAdd(AnActionEvent e) {
            ListBox<String> serviceList = myServiceList;
            if (serviceList == null) {
                return;
            }

            String selected = serviceList.getValue();
            askServiceUrl(serviceList, MavenIndicesLocalize.mavenRepositoriesServiceAdd(), selected == null ? "http://" : selected)
                .whenComplete((url, error) -> myProject.getUIAccess().give(() -> {
                    if (StringUtil.isNotEmpty(url)) {
                        myServiceUrls.add(url);
                        serviceList.setValue(url);
                    }
                }));
        }
    }

    private class ServiceEditAction extends EditAction<String> {
        @Override
        @RequiredUIAccess
        protected void doEdit(String url, AnActionEvent e) {
            ListBox<String> serviceList = myServiceList;
            if (serviceList == null) {
                return;
            }

            askServiceUrl(serviceList, MavenIndicesLocalize.mavenRepositoriesServiceEdit(), url)
                .whenComplete((newUrl, error) -> myProject.getUIAccess().give(() -> {
                    int index = myServiceUrls.indexOf(url);
                    if (StringUtil.isNotEmpty(newUrl) && index >= 0) {
                        myServiceUrls.remove(url);
                        myServiceUrls.add(newUrl, index);
                        serviceList.setValue(newUrl);
                    }
                }));
        }
    }

    private class ServiceRemoveAction extends RemoveAction<String> {
        @Override
        @RequiredUIAccess
        protected void doRemove(String url, AnActionEvent e) {
            myServiceUrls.remove(url);
        }
    }

    private class ServiceTestAction extends DumbAwareAction implements AnActionWithSyncUpdate {
        private ServiceTestAction() {
            super(MavenIndicesLocalize.mavenRepositoriesServiceTest(), LocalizeValue.empty(), PlatformIconGroup.actionsExecute());
        }

        @Override
        public void update(AnActionEvent e) {
            e.getPresentation().setEnabled(!myTestingService && mySelectedServiceUrl != null);
        }

        @Override
        @RequiredUIAccess
        public void actionPerformed(AnActionEvent e) {
            ListBox<String> serviceList = myServiceList;
            String url = mySelectedServiceUrl;
            if (serviceList != null && url != null) {
                testServiceConnection(serviceList, url);
            }
        }
    }
}
