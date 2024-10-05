/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.project;

import consulo.codeEditor.CodeInsightColors;
import consulo.colorScheme.EditorColorsManager;
import consulo.colorScheme.EffectType;
import consulo.colorScheme.TextAttributes;
import consulo.execution.ui.console.Filter;
import consulo.execution.ui.console.OpenFileHyperlinkInfo;
import consulo.platform.Platform;
import consulo.project.Project;
import consulo.ui.style.StandardColors;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;

import javax.annotation.Nullable;
import java.awt.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Sergey Evdokimov
 */
public class MavenGroovyConsoleFilter implements Filter {
    private static final Pattern PATTERN = Pattern.compile("\\[ERROR\\] (\\S.+\\.groovy): (-?\\d{1,5}): .+", Pattern.DOTALL);

    private final Project myProject;

    public MavenGroovyConsoleFilter(Project project) {
        myProject = project;
    }

    @Nullable
    @Override
    public Result applyFilter(String line, int entireLength) {
        // Example of gmaven error line:
        // [ERROR] /home/user/ideaProjects/simpleMaven/src/main/groovy/com/A.groovy: 17:
        // [Static type checking] - Cannot assign value of type java.lang.String to variable of type int

        if (!line.startsWith("[ERROR] ") || !line.contains(".groovy: ")) {
            return null;
        }

        Matcher matcher = PATTERN.matcher(line);
        if (!matcher.matches()) {
            return null;
        }

        String path = matcher.group(1);

        VirtualFile file = LocalFileSystem.getInstance().findFileByPath(path);
        if (file == null) {
            if (Platform.current().os().isWindows() && path.matches("/[A-Z]:/.+")) {
                file = LocalFileSystem.getInstance().findFileByPath(path.substring(1));
            }
            if (file == null) {
                return null;
            }
        }

        int lineNumber = Integer.parseInt(matcher.group(2)) - 1;
        if (lineNumber < 0) {
            lineNumber = -1;
        }

        TextAttributes attr = createCompilationErrorAttr();

        return new Result(
            entireLength - line.length() + matcher.start(1),
            entireLength - line.length() + matcher.end(1),
            new OpenFileHyperlinkInfo(myProject, file, lineNumber), attr
        );
    }

    private static TextAttributes createCompilationErrorAttr() {
        TextAttributes attr =
            EditorColorsManager.getInstance().getGlobalScheme().getAttributes(CodeInsightColors.HYPERLINK_ATTRIBUTES).clone();
        attr.setForegroundColor(StandardColors.RED);
        attr.setEffectColor(StandardColors.RED);
        attr.setEffectType(EffectType.LINE_UNDERSCORE);
        attr.setFontType(Font.PLAIN);
        return attr;
    }

}
