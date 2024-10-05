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

import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.impl.schema.XmlNSDescriptorImpl;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.application.util.CachedValue;
import consulo.application.util.CachedValueProvider;
import consulo.application.util.CachedValuesManager;
import consulo.ide.ServiceManager;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.psi.PsiModificationTracker;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import consulo.xml.javaee.ExternalResourceManager;
import consulo.xml.psi.xml.XmlFile;
import consulo.xml.psi.xml.XmlTag;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

@Singleton
@ServiceAPI(ComponentScope.PROJECT)
@ServiceImpl
public class MavenDomElementDescriptorHolder {
    private static final Logger LOG = Logger.getInstance(MavenDomElementDescriptorHolder.class);

    private enum FileKind {
        PROJECT_FILE {
            public String getSchemaUrl() {
                return MavenSchemaProvider.MAVEN_PROJECT_SCHEMA_URL;
            }
        },
        PROFILES_FILE {
            public String getSchemaUrl() {
                return MavenSchemaProvider.MAVEN_PROFILES_SCHEMA_URL;
            }
        },
        SETTINGS_FILE {
            public String getSchemaUrl() {
                return MavenSchemaProvider.MAVEN_SETTINGS_SCHEMA_URL;
            }
        };

        public abstract String getSchemaUrl();
    }

    private final Project myProject;
    private final Map<FileKind, CachedValue<XmlNSDescriptorImpl>> myDescriptorsMap = new HashMap<>();

    @Inject
    public MavenDomElementDescriptorHolder(Project project) {
        myProject = project;
    }

    public static MavenDomElementDescriptorHolder getInstance(@Nonnull Project project) {
        return ServiceManager.getService(project, MavenDomElementDescriptorHolder.class);
    }

    @Nullable
    public XmlElementDescriptor getDescriptor(@Nonnull XmlTag tag) {
        FileKind kind = getFileKind(tag.getContainingFile());
        if (kind == null) {
            return null;
        }

        XmlNSDescriptorImpl desc;
        synchronized (this) {
            desc = tryGetOrCreateDescriptor(kind);
            if (desc == null) {
                return null;
            }
        }
        LOG.assertTrue(tag.isValid());
        LOG.assertTrue(desc.isValid());
        return desc.getElementDescriptor(tag.getName(), desc.getDefaultNamespace());
    }

    @Nullable
    private XmlNSDescriptorImpl tryGetOrCreateDescriptor(final FileKind kind) {
        CachedValue<XmlNSDescriptorImpl> result = myDescriptorsMap.get(kind);
        if (result == null) {
            result = CachedValuesManager.getManager(myProject).createCachedValue(new CachedValueProvider<XmlNSDescriptorImpl>() {
                @Override
                public Result<XmlNSDescriptorImpl> compute() {
                    return Result.create(doCreateDescriptor(kind), PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT);
                }
            }, false);
            myDescriptorsMap.put(kind, result);
        }
        return result.getValue();
    }

    @Nullable
    private XmlNSDescriptorImpl doCreateDescriptor(FileKind kind) {
        String schemaUrl = kind.getSchemaUrl();
        String location = ExternalResourceManager.getInstance().getResourceLocation(schemaUrl);
        if (schemaUrl.equals(location)) {
            return null;
        }

        VirtualFile schema;
        try {
            schema = VirtualFileUtil.findFileByURL(new URL(location));
        }
        catch (MalformedURLException ignore) {
            return null;
        }

        if (schema == null) {
            return null;
        }

        PsiFile psiFile = PsiManager.getInstance(myProject).findFile(schema);
        if (!(psiFile instanceof XmlFile)) {
            return null;
        }

        XmlNSDescriptorImpl result = new XmlNSDescriptorImpl();
        result.init(psiFile);
        return result;
    }

    @Nullable
    private FileKind getFileKind(PsiFile file) {
        if (MavenDomUtil.isProjectFile(file)) {
            return FileKind.PROJECT_FILE;
        }
        if (MavenDomUtil.isProfilesFile(file)) {
            return FileKind.PROFILES_FILE;
        }
        if (MavenDomUtil.isSettingsFile(file)) {
            return FileKind.SETTINGS_FILE;
        }
        return null;
    }
}
