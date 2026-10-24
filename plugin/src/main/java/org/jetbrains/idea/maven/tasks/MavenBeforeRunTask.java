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
package org.jetbrains.idea.maven.tasks;

import com.dslplatform.json.CompiledJson;
import consulo.execution.BeforeRunTask;
import consulo.util.io.FileUtil;
import consulo.util.lang.StringUtil;
import consulo.util.xml.serializer.annotation.Attribute;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jspecify.annotations.Nullable;

@CompiledJson
public class MavenBeforeRunTask extends BeforeRunTask {
    private @Nullable String myProjectPath;
    private @Nullable String myGoal;

    @Attribute("file")
    public @Nullable String getProjectPath() {
        return myProjectPath;
    }

    public void setProjectPath(@Nullable String projectPath) {
        myProjectPath = StringUtil.nullize(projectPath, true);
    }

    @Attribute("goal")
    public @Nullable String getGoal() {
        return myGoal;
    }

    public void setGoal(@Nullable String goal) {
        myGoal = StringUtil.nullize(goal, true);
    }

    public boolean isFor(MavenProject project, String goal) {
        return !(myProjectPath == null || myGoal == null)
            && FileUtil.pathsEqual(project.getPath(), myProjectPath)
            && goal.equals(myGoal);
    }
}
