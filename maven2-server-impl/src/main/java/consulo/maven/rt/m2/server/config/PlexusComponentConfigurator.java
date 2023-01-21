package consulo.maven.rt.m2.server.config;

import org.codehaus.plexus.PlexusContainer;
import javax.annotation.Nonnull;

/**
 * @author Sergey.Anchipolevsky
 *         Date: 21.09.2009
 */
public interface PlexusComponentConfigurator {
  void configureComponents(@Nonnull PlexusContainer container);

}
