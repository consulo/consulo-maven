package consulo.maven.importProvider;

import consulo.annotation.access.RequiredReadAction;
import consulo.application.ApplicationManager;
import consulo.application.ReadAction;
import consulo.component.ProcessCanceledException;
import consulo.dataContext.DataManager;
import consulo.language.editor.CommonDataKeys;
import consulo.logging.Logger;
import consulo.maven.rt.server.common.model.MavenExplicitProfiles;
import consulo.maven.rt.server.common.model.MavenId;
import consulo.module.creation.importing.ModuleImportContext;
import consulo.project.Project;
import consulo.project.ProjectManager;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.idea.maven.project.*;
import org.jetbrains.idea.maven.utils.*;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * @author VISTALL
 * @since 31-Jan-17
 */
public class MavenImportModuleContext extends ModuleImportContext {
    private static final Logger LOG = Logger.getInstance(MavenImportModuleContext.class);

    protected Project myProjectToUpdate;

    protected MavenGeneralSettings myGeneralSettingsCache;
    protected MavenImportingSettings myImportingSettingsCache;

    protected VirtualFile myImportRoot;
    protected List<VirtualFile> myFiles;
    protected List<String> myProfiles = new ArrayList<>();
    protected List<String> myActivatedProfiles = new ArrayList<>();
    protected MavenExplicitProfiles mySelectedProfiles = MavenExplicitProfiles.NONE;

    protected volatile MavenProjectsTree myMavenProjectTree;
    protected List<MavenProject> mySelectedProjects;

    private volatile @Nullable String myRootPath;
    private volatile boolean myProfilesScanned;

    public MavenImportModuleContext(@Nullable Project project) {
        super(project);
    }

    @Override
    public void setFileToImport(String path) {
        VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByPath(path);
        myImportRoot = file == null || file.isDirectory() ? file : file.getParent();
    }

    @Nullable
    public List<MavenProject> getList() {
        return mySelectedProjects;
    }

    public boolean isMarked(MavenProject element) {
        return mySelectedProjects != null && mySelectedProjects.contains(element);
    }

    public MavenImportModuleContext setList(List<MavenProject> list) {
        mySelectedProjects = list;
        return this;
    }

    @Nullable
    public String getSuggestedProjectName() {
        MavenProjectsTree tree = myMavenProjectTree;
        if (tree == null) {
            return null;
        }

        List<MavenProject> list = tree.getRootProjects();
        return list.size() == 1 ? list.get(0).getMavenId().getArtifactId() : null;
    }

    @Nullable
    public Project getProjectToUpdate() {
        if (myProjectToUpdate == null) {
            myProjectToUpdate = DataManager.getInstance().getDataContext().getData(CommonDataKeys.PROJECT);
        }
        return myProjectToUpdate;
    }

    @Nullable
    public VirtualFile getRootDirectory() {
        if (myImportRoot == null && isUpdate()) {
            Project project = getProjectToUpdate();
            myImportRoot = project != null ? project.getBaseDir() : null;
        }
        return myImportRoot;
    }

    public MavenExplicitProfiles getSelectedProfiles() {
        return mySelectedProfiles;
    }

    public List<String> getActivatedProfiles() {
        return myActivatedProfiles;
    }

    public List<String> getProfiles() {
        return myProfiles;
    }

    public void setRootDirectory(@Nullable Project projectToUpdate, String root) {
        myFiles = null;
        myProfiles.clear();
        myActivatedProfiles.clear();
        myMavenProjectTree = null;

        myProjectToUpdate = projectToUpdate;
        myRootPath = root;
        myProfilesScanned = false;
    }

    public boolean isProfilesScanned() {
        return myProfilesScanned;
    }

    public boolean isProjectTreeRead() {
        return myMavenProjectTree != null;
    }

    public synchronized boolean scanProfiles() {
        if (myProfilesScanned) {
            return true;
        }

        String rootPath = myRootPath;
        if (rootPath == null) {
            return false;
        }

        boolean finished = runConfigurationProcess(indicator -> {
            indicator.setText(ProjectBundle.message("maven.locating.files"));

            myImportRoot = LocalFileSystem.getInstance().refreshAndFindFileByPath(rootPath);
            if (myImportRoot == null) {
                throw new MavenProcessCanceledException();
            }

            myFiles = FileFinder.findPomFiles(myImportRoot.getChildren(), getImportingSettings().isLookForNested(), indicator, new ArrayList<>());

            collectProfiles(indicator);

            indicator.setText("");
            indicator.setText2("");
        });

        myProfilesScanned = finished && Objects.equals(rootPath, myRootPath);
        return finished;
    }

    private boolean isUpdate() {
        return !isNewProject();
    }

    public void setSelectedProfiles(MavenExplicitProfiles profiles) {
        if (!profiles.equals(mySelectedProfiles)) {
            myMavenProjectTree = null;
        }

        mySelectedProfiles = profiles;
    }

    public synchronized boolean readProjectTree() {
        if (!scanProfiles()) {
            return false;
        }

        if (myMavenProjectTree != null) {
            return true;
        }

        return runConfigurationProcess(indicator -> {
            readMavenProjectTree(indicator);
            indicator.setText2("");
        });
    }

    private void collectProfiles(MavenProgressIndicator process) {
        process.setText(ProjectBundle.message("maven.searching.profiles"));

        Set<String> availableProfiles = new LinkedHashSet<>();
        Set<String> activatedProfiles = new LinkedHashSet<>();
        MavenProjectReader reader = new MavenProjectReader();
        MavenGeneralSettings generalSettings = getGeneralSettings();
        MavenProjectReaderProjectLocator locator = new MavenProjectReaderProjectLocator() {
            @Override
            public VirtualFile findProjectFile(MavenId coordinates) {
                return null;
            }
        };
        for (VirtualFile f : myFiles) {
            MavenProject project = new MavenProject(f);
            process.setText2(ProjectBundle.message("maven.reading.pom", f.getPath()));
            project.read(generalSettings, MavenExplicitProfiles.NONE, reader, locator);
            availableProfiles.addAll(project.getProfilesIds());
            activatedProfiles.addAll(project.getActivatedProfilesIds().getEnabledProfiles());
        }
        myProfiles = new ArrayList<>(availableProfiles);
        myActivatedProfiles = new ArrayList<>(activatedProfiles);
    }

    private static boolean runConfigurationProcess(MavenTask task) {
        try {
            task.run(new MavenProgressIndicator());
        }
        catch (MavenProcessCanceledException | ProcessCanceledException e) {
            return false;
        }
        catch (Exception e) {
            LOG.error(e);
            return false;
        }
        return true;
    }

    private void readMavenProjectTree(MavenProgressIndicator process) throws MavenProcessCanceledException {
        MavenExplicitProfiles profiles = mySelectedProfiles;

        MavenProjectsTree tree = new MavenProjectsTree();
        tree.addManagedFilesWithProfiles(myFiles, profiles);
        tree.updateAll(false, getGeneralSettings(), process);

        if (profiles.equals(mySelectedProfiles)) {
            mySelectedProjects = tree.getRootProjects();
            myMavenProjectTree = tree;
        }
    }

    @RequiredReadAction
    private MavenWorkspaceSettings getDirectProjectsSettings() {
        ApplicationManager.getApplication().assertReadAccessAllowed();

        Project project = isUpdate() ? getProjectToUpdate() : null;
        if (project == null || project.isDisposed()) {
            project = ProjectManager.getInstance().getDefaultProject();
        }

        return MavenWorkspaceSettingsComponent.getInstance(project).getSettings();
    }

    public MavenGeneralSettings getGeneralSettings() {
        if (myGeneralSettingsCache == null) {
            myGeneralSettingsCache = ReadAction.compute(() -> getDirectProjectsSettings().generalSettings.clone());
        }
        return myGeneralSettingsCache;
    }

    public MavenImportingSettings getImportingSettings() {
        if (myImportingSettingsCache == null) {
            myImportingSettingsCache = ReadAction.compute(() -> getDirectProjectsSettings().importingSettings.clone());
        }
        return myImportingSettingsCache;
    }
}
