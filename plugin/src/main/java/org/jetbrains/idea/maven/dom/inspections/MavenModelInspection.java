/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.dom.inspections;

import consulo.annotation.component.ExtensionImpl;
import consulo.localize.LocalizeValue;
import consulo.virtualFileSystem.VirtualFile;
import consulo.xml.util.xml.DomUtil;
import consulo.xml.util.xml.GenericDomValue;
import consulo.xml.util.xml.highlighting.BasicDomElementsInspection;
import jakarta.annotation.Nonnull;
import org.jetbrains.idea.maven.dom.converters.MavenDomSoftAwareConverter;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.jetbrains.idea.maven.localize.MavenDomLocalize;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

@ExtensionImpl
public class MavenModelInspection extends BasicDomElementsInspection<MavenDomProjectModel, Object> {
    public MavenModelInspection() {
        super(MavenDomProjectModel.class);
    }

    @Nonnull
    @Override
    public LocalizeValue getGroupDisplayName() {
        return MavenDomLocalize.inspectionGroup();
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return MavenDomLocalize.inspectionName();
    }

    @Nonnull
    @Override
    public String getShortName() {
        return "MavenModelInspection";
    }

    private static boolean isElementInsideManagedFile(GenericDomValue value) {
        VirtualFile virtualFile = DomUtil.getFile(value).getVirtualFile();
        if (virtualFile == null) {
            return false;
        }

        MavenProjectsManager projectsManager = MavenProjectsManager.getInstance(value.getManager().getProject());

        return projectsManager.findProject(virtualFile) != null;
    }

    @Override
    protected boolean shouldCheckResolveProblems(GenericDomValue value) {
        return isElementInsideManagedFile(value)
            && !(value.getConverter() instanceof MavenDomSoftAwareConverter domSoftAwareConverter && domSoftAwareConverter.isSoft(value));
    }
}