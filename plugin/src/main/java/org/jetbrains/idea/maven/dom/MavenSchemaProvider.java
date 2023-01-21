/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.dom;

import consulo.annotation.component.ExtensionImpl;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import consulo.xml.javaee.ExternalResourceManager;
import consulo.xml.javaee.ExternalResourceManagerEx;
import consulo.xml.javaee.ResourceRegistrar;
import consulo.xml.javaee.StandardResourceProvider;

import javax.annotation.Nonnull;

@ExtensionImpl
public class MavenSchemaProvider implements StandardResourceProvider
{
	public static final String MAVEN_PROJECT_SCHEMA_URL = "http://maven.apache.org/xsd/maven-4.0.0.xsd";
	public static final String MAVEN_PROFILES_SCHEMA_URL = "http://maven.apache.org/xsd/profiles-1.0.0.xsd";
	public static final String MAVEN_SETTINGS_SCHEMA_URL = "http://maven.apache.org/xsd/settings-1.0.0.xsd";

	public void registerResources(ResourceRegistrar registrar)
	{
		registrar.addStdResource("http://maven.apache.org/maven-v4_0_0.xsd", "/schemas/maven-4.0.0.xsd", getClass());
		registrar.addStdResource(MAVEN_PROJECT_SCHEMA_URL, "/schemas/maven-4.0.0.xsd", getClass());

		registrar.addStdResource(MAVEN_PROFILES_SCHEMA_URL, "/schemas/profiles-1.0.0.xsd", getClass());
		registrar.addStdResource(MAVEN_SETTINGS_SCHEMA_URL, "/schemas/settings-1.0.0.xsd", getClass());
	}

	@Nonnull
	public static VirtualFile getSchemaFile(@Nonnull String url)
	{
		String location = ((ExternalResourceManagerEx) ExternalResourceManager.getInstance()).getStdResource(url, null);
		assert location != null : "cannot find a standard resource for " + url;

		VirtualFile result = VirtualFileUtil.findRelativeFile(location, null);
		assert result != null : "cannot find a schema file for URL: " + url + " location: " + location;

		return result;
	}
}

