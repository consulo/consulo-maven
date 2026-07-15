package consulo.maven.importProvider;

import consulo.annotation.component.ExtensionImpl;
import consulo.project.Project;
import consulo.project.ProjectRunOnceExtension;
import jakarta.inject.Inject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

/**
 * @author VISTALL
 * @since 2026-07-15
 */
@ExtensionImpl
public class MavenProjectRunOnceExtension implements ProjectRunOnceExtension<MavenProjectRunOnceExtension.Maven> {
    public record Maven(String id) {

    }

    public static final String ID = "maven";

    private final Project myProject;

    @Inject
    public MavenProjectRunOnceExtension(Project project) {
        myProject = project;
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public Class<Maven> getInputClass() {
        return Maven.class;
    }

    @Override
    public void run(Maven maven) {
        MavenProjectsManager manager = MavenProjectsManager.getInstance(myProject);

        manager.doInit(); // do init - activity can run after
        manager.scheduleImportAndResolve();
    }
}
