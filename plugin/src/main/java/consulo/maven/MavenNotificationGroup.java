package consulo.maven;

import consulo.annotation.component.ExtensionImpl;
import consulo.localize.LocalizeValue;
import consulo.project.ui.notification.NotificationGroup;
import consulo.project.ui.notification.NotificationGroupContributor;

import javax.annotation.Nonnull;
import java.util.function.Consumer;

/**
 * @author VISTALL
 * @since 19/01/2023
 */
@ExtensionImpl
public class MavenNotificationGroup implements NotificationGroupContributor
{
	public static final NotificationGroup REPOSITORY = NotificationGroup.balloonGroup("MavenRepository", LocalizeValue.localizeTODO("Maven Repository"));
	public static final NotificationGroup IMPORT = NotificationGroup.balloonGroup("MavenImport", LocalizeValue.localizeTODO("Maven Import"));
	public static final NotificationGroup ROOT = NotificationGroup.balloonGroup("Maven", LocalizeValue.localizeTODO("Maven"));

	@Override
	public void contribute(@Nonnull Consumer<NotificationGroup> consumer)
	{
		consumer.accept(REPOSITORY);
		consumer.accept(IMPORT);
		consumer.accept(ROOT);
	}
}
