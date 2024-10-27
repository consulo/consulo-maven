package consulo.maven.template;

import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.template.LiveTemplateContributor;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import org.jetbrains.idea.maven.utils.MavenLiveTemplateContextType;

@ExtensionImpl
public class MavenLiveTemplateContributor implements LiveTemplateContributor {
  @Override
  @Nonnull
  public String groupId() {
    return "maven";
  }

  @Override
  @Nonnull
  public LocalizeValue groupName() {
    return LocalizeValue.localizeTODO("Maven");
  }

  @Override
  public void contribute(@Nonnull LiveTemplateContributor.Factory factory) {
    try(Builder builder = factory.newBuilder("mavenDep", "dep", "<dependency>\n"
        + "   <groupId>$GROUP$</groupId>\n"
        + "   <artifactId>$ARTIFACT$</artifactId>\n"
        + "   <version>$VERSION$</version>\n"
        + "</dependency>", LocalizeValue.localizeTODO("dependency"))) {
      builder.withReformat();

      builder.withVariable("ARTIFACT", "complete()", "", true);
      builder.withVariable("GROUP", "completeSmart()", "", true);
      builder.withVariable("VERSION", "complete()", "", true);

      builder.withContext(MavenLiveTemplateContextType.class, true);
    }

    try(Builder builder = factory.newBuilder("mavenPl", "pl", "<plugin>\n"
        + "   <groupId>$GROUP$</groupId>\n"
        + "   <artifactId>$ARTIFACT$</artifactId>\n"
        + "   <version>$VERSION$</version>\n"
        + "</plugin>", LocalizeValue.localizeTODO("plugin"))) {
      builder.withReformat();

      builder.withVariable("ARTIFACT", "complete()", "", true);
      builder.withVariable("GROUP", "completeSmart()", "", true);
      builder.withVariable("VERSION", "complete()", "", true);

      builder.withContext(MavenLiveTemplateContextType.class, true);
    }

    try(Builder builder = factory.newBuilder("mavenRepo", "repo", "<repository>\n"
        + "  <id>$ID$</id>\n"
        + "  <name>$NAME$</name>\n"
        + "  <url>$URL$</url>\n"
        + "</repository>", LocalizeValue.localizeTODO("repository"))) {
      builder.withReformat();

      builder.withVariable("ID", "complete()", "", true);
      builder.withVariable("NAME", "complete()", "", true);
      builder.withVariable("URL", "complete()", "", true);

      builder.withContext(MavenLiveTemplateContextType.class, true);
    }

  }
}
