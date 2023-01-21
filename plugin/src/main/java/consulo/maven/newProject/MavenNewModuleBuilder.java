package consulo.maven.newProject;

import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.ide.newModule.NewModuleBuilder;
import consulo.ide.newModule.NewModuleBuilderProcessor;
import consulo.ide.newModule.NewModuleContext;
import consulo.ide.newModule.ui.UnifiedProjectOrModuleNameStep;
import consulo.maven.icon.MavenIconGroup;
import consulo.module.content.layer.ContentEntry;
import consulo.module.content.layer.ModifiableRootModel;
import consulo.project.Project;
import consulo.ui.ex.wizard.WizardStep;
import org.jetbrains.idea.maven.wizards.MavenModuleWizardStep;
import org.jetbrains.idea.maven.wizards.SelectPropertiesStep;

import javax.annotation.Nonnull;
import java.util.function.Consumer;

/**
 * @author VISTALL
 * @since 2019-10-06
 */
@ExtensionImpl
public class MavenNewModuleBuilder implements NewModuleBuilder
{
	@Override
	public void setupContext(@Nonnull NewModuleContext newModuleContext)
	{
		newModuleContext.get("jvm").add("From Maven", MavenIconGroup.mavenlogo(), new NewModuleBuilderProcessor<MavenNewModuleContext>()
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
