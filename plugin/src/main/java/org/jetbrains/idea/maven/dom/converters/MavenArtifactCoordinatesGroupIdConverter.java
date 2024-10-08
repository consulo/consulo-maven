package org.jetbrains.idea.maven.dom.converters;

import consulo.util.lang.StringUtil;
import consulo.xml.util.xml.ConvertContext;
import org.jetbrains.idea.maven.indices.MavenProjectIndicesManager;
import consulo.maven.rt.server.common.model.MavenId;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class MavenArtifactCoordinatesGroupIdConverter extends MavenArtifactCoordinatesConverter implements MavenSmartConverter<String> {
    @Override
    protected boolean doIsValid(MavenId id, MavenProjectIndicesManager manager, ConvertContext context) {
        return !StringUtil.isEmpty(id.getGroupId()) && manager.hasGroupId(id.getGroupId());
    }

    @Override
    protected Set<String> doGetVariants(MavenId id, MavenProjectIndicesManager manager) {
        return manager.getGroupIds();
    }

    @Override
    public Collection<String> getSmartVariants(ConvertContext convertContext) {
        Set<String> groupIds = new HashSet<>();
        String artifactId = MavenArtifactCoordinatesHelper.getId(convertContext).getArtifactId();
        if (!StringUtil.isEmptyOrSpaces(artifactId)) {
            MavenProjectIndicesManager manager = MavenProjectIndicesManager.getInstance(convertContext.getFile().getProject());
            for (String grouipId : manager.getGroupIds()) {
                if (manager.getArtifactIds(grouipId).contains(artifactId)) {
                    groupIds.add(grouipId);
                }
            }
        }
        return groupIds;
    }
}
