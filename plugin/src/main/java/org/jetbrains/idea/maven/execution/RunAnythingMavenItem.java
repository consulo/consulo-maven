// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.execution;

import consulo.ide.runAnything.RunAnythingItemBase;
import consulo.localize.LocalizeValue;
import consulo.ui.ex.SimpleTextAttributes;
import consulo.ui.ex.awt.SimpleColoredComponent;
import consulo.ui.image.Image;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.execution.MavenCommandLineOptions.Option;

import javax.swing.*;
import java.awt.*;

import static consulo.util.lang.StringUtil.*;

public class RunAnythingMavenItem extends RunAnythingItemBase {

    public RunAnythingMavenItem(@NotNull String command, @Nullable Image icon) {
        super(command, icon);
    }

    @Override
    @NotNull
    public Component createComponent(@Nullable String pattern, boolean isSelected, boolean hasFocus) {
        String command = getCommand();
        JPanel component = (JPanel) super.createComponent(pattern, isSelected, hasFocus);

        String toComplete = notNullize(substringAfterLast(command, " "));
        if (toComplete.startsWith("-")) {
            Option option = MavenCommandLineOptions.findOption(toComplete);
            if (option != null) {
                LocalizeValue description = option.getDescription();
                SimpleColoredComponent descriptionComponent = new SimpleColoredComponent();
                //noinspection HardCodedStringLiteral
                descriptionComponent.append(" " + shortenTextWithEllipsis(description.get(), 200, 0), SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES);
                component.add(descriptionComponent, BorderLayout.EAST);
            }
        }

        return component;
    }
}