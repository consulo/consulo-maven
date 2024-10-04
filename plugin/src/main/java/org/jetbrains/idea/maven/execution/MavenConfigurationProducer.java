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

/*
 * User: anna
 * Date: 13-May-2010
 */
package org.jetbrains.idea.maven.execution;

import consulo.annotation.component.ExtensionImpl;
import consulo.execution.RunnerAndConfigurationSettings;
import consulo.execution.action.ConfigurationContext;
import consulo.execution.action.Location;
import consulo.execution.action.RuntimeConfigurationProducer;
import consulo.execution.configuration.RunConfiguration;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.maven.rt.server.common.model.MavenExplicitProfiles;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import javax.annotation.Nonnull;
import java.util.List;

@ExtensionImpl
public class MavenConfigurationProducer extends RuntimeConfigurationProducer {
    private PsiElement myPsiElement;

    public MavenConfigurationProducer() {
        super(MavenRunConfigurationType.getInstance());
    }

    @Override
    public PsiElement getSourceElement() {
        return myPsiElement;
    }

    @Override
    protected RunnerAndConfigurationSettings createConfigurationByElement(Location location, ConfigurationContext context) {
        myPsiElement = location.getPsiElement();
        final MavenRunnerParameters params = createBuildParameters(location);
        if (params == null) {
            return null;
        }

        return MavenRunConfigurationType.createRunnerAndConfigurationSettings(null, null, params, location.getProject());
    }

    @Override
    protected RunnerAndConfigurationSettings findExistingByElement(
        Location location,
        @Nonnull List<RunnerAndConfigurationSettings> existingConfigurations,
        ConfigurationContext context
    ) {

        final MavenRunnerParameters runnerParameters = createBuildParameters(location);
        for (RunnerAndConfigurationSettings existingConfiguration : existingConfigurations) {
            final RunConfiguration configuration = existingConfiguration.getConfiguration();
            if (configuration instanceof MavenRunConfiguration &&
                ((MavenRunConfiguration)configuration).getRunnerParameters().equals(runnerParameters)) {
                return existingConfiguration;
            }
        }
        return null;
    }

    private static MavenRunnerParameters createBuildParameters(Location l) {
        if (!(l instanceof MavenGoalLocation)) {
            return null;
        }

        VirtualFile f = ((PsiFile)l.getPsiElement()).getVirtualFile();
        List<String> goals = ((MavenGoalLocation)l).getGoals();
        MavenExplicitProfiles profiles = MavenProjectsManager.getInstance(l.getProject()).getExplicitProfiles();

        return new MavenRunnerParameters(true, f.getParent().getPath(), goals, profiles);
    }

    public int compareTo(Object o) {
        return PREFERED;  //To change body of implemented methods use File | Settings | File Templates.
    }
}
