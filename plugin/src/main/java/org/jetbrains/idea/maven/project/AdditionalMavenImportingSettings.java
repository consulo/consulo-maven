package org.jetbrains.idea.maven.project;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;
import consulo.configurable.UnnamedConfigurable;
import consulo.project.Project;

@ExtensionAPI(ComponentScope.APPLICATION)
public interface AdditionalMavenImportingSettings {
    ExtensionPointName<AdditionalMavenImportingSettings> EP_NAME = ExtensionPointName.create(AdditionalMavenImportingSettings.class);

    UnnamedConfigurable createConfigurable(Project project);
}
