package consulo.maven.newProject;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import consulo.annotation.access.RequiredReadAction;
import consulo.ide.newProject.NewModuleBuilder;
import consulo.ide.newProject.NewModuleBuilderProcessor;
import consulo.ide.newProject.NewModuleContext;
import consulo.ide.newProject.ui.UnifiedProjectOrModuleNameStep;
import consulo.maven.icon.MavenIconGroup;
import consulo.ui.wizard.WizardStep;
import org.jetbrains.idea.maven.wizards.MavenModuleWizardStep;
import org.jetbrains.idea.maven.wizards.SelectPropertiesStep;

import javax.annotation.Nonnull;
import java.util.function.Consumer;

/**
 * @author VISTALL
 * @since 2019-10-06
 */
public class MavenNewModuleBuilder implements NewModuleBuilder
{
	@Override
	public void setupContext(@Nonnull NewModuleContext newModuleContext)
	{
		newModuleContext.get("jvm").add("From Maven", MavenIconGroup.mavenLogo(), new NewModuleBuilderProcessor<MavenNewModuleContext>()
		{
			@Nonnull
			@Override
			public MavenNewModuleContext createContext(boolean isNewProject)
			{
				return new MavenNewModuleContext(isNewProject);
			}

			@Override
			public void buildSteps(@Nonnull Consumer<WizardStep<MavenNewModuleContext>> consumer, @Nonnull MavenNewModuleContext context)
			{
				consumer.accept(new MavenModuleWizardStep(context));
				consumer.accept(new SelectPropertiesStep());
				consumer.accept(new UnifiedProjectOrModuleNameStep<>(context));
			}

			@RequiredReadAction
			@Override
			public void process(@Nonnull MavenNewModuleContext context, @Nonnull ContentEntry contentEntry, @Nonnull ModifiableRootModel modifiableRootModel)
			{
				final Project project = modifiableRootModel.getProject();

				context.registerMavenInitialize(project, contentEntry.getFile());
			}
		});
	}
}
