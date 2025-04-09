// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.execution;

import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import org.jetbrains.idea.maven.localize.MavenRunnerLocalize;

import java.util.*;

public final class MavenCommandLineOptions {

    private static final Map<String, Option> ourOptionsIndexMap;
    private static final Set<Option> ourAllOptions;

    static {
        ourAllOptions = new HashSet<>();

        ourAllOptions.add(new Option("-am", "--also-make", MavenRunnerLocalize.mavenOptionsDescriptionAm()));
        ourAllOptions.add(new Option("-amd", "--also-make-dependents", MavenRunnerLocalize.mavenOptionsDescriptionAmd()));
        ourAllOptions.add(new Option("-B", "--batch-mode", MavenRunnerLocalize.mavenOptionsDescriptionB()));
        ourAllOptions.add(new Option("-b", "--builder", MavenRunnerLocalize.mavenOptionsDescriptionB()));
        ourAllOptions.add(new Option("-C", "--strict-checksums", MavenRunnerLocalize.mavenOptionsDescriptionC()));
        ourAllOptions.add(new Option("-c", "--lax-checksums", MavenRunnerLocalize.mavenOptionsDescriptionC()));
        ourAllOptions.add(new Option("-cpu", "--check-plugin-updates", MavenRunnerLocalize.mavenOptionsDescriptionCpu()));
        ourAllOptions.add(new Option("-D", "--define", MavenRunnerLocalize.mavenOptionsDescriptionD()));
        ourAllOptions.add(new Option("-e", "--errors", MavenRunnerLocalize.mavenOptionsDescriptionE()));
        ourAllOptions.add(new Option("-emp", "--encrypt-master-password", MavenRunnerLocalize.mavenOptionsDescriptionEmp()));
        ourAllOptions.add(new Option("-ep", "--encrypt-password", MavenRunnerLocalize.mavenOptionsDescriptionEp()));
        ourAllOptions.add(new Option("-f", "--file", MavenRunnerLocalize.mavenOptionsDescriptionF()));
        ourAllOptions.add(new Option("-fae", "--fail-at-end", MavenRunnerLocalize.mavenOptionsDescriptionFae()));
        ourAllOptions.add(new Option("-ff", "--fail-fast", MavenRunnerLocalize.mavenOptionsDescriptionFf()));
        ourAllOptions.add(new Option("-fn", "--fail-never", MavenRunnerLocalize.mavenOptionsDescriptionFn()));
        ourAllOptions.add(new Option("-gs", "--global-settings", MavenRunnerLocalize.mavenOptionsDescriptionGs()));
        ourAllOptions.add(new Option("-gt", "--global-toolchains", MavenRunnerLocalize.mavenOptionsDescriptionGt()));
        ourAllOptions.add(new Option("-h", "--help", MavenRunnerLocalize.mavenOptionsDescriptionH()));
        ourAllOptions.add(new Option("-l", "--log-file", MavenRunnerLocalize.mavenOptionsDescriptionL()));
        ourAllOptions.add(new Option("-llr", "--legacy-local-repository", MavenRunnerLocalize.mavenOptionsDescriptionLlr()));
        ourAllOptions.add(new Option("-N", "--non-recursive", MavenRunnerLocalize.mavenOptionsDescriptionN()));
        ourAllOptions.add(new Option("-npr", "--no-plugin-registry", MavenRunnerLocalize.mavenOptionsDescriptionNpr()));
        ourAllOptions.add(new Option("-npu", "--no-plugin-updates", MavenRunnerLocalize.mavenOptionsDescriptionNpu()));
        ourAllOptions.add(new Option("-nsu", "--no-snapshot-updates", MavenRunnerLocalize.mavenOptionsDescriptionNsu()));
        ourAllOptions.add(new Option("-o", "--offline", MavenRunnerLocalize.mavenOptionsDescriptionO()));
        ourAllOptions.add(new Option("-P", "--activate-profiles", MavenRunnerLocalize.mavenOptionsDescriptionP()));
        ourAllOptions.add(new Option("-pl", "--projects", MavenRunnerLocalize.mavenOptionsDescriptionPl()));
        ourAllOptions.add(new Option("-q", "--quiet", MavenRunnerLocalize.mavenOptionsDescriptionQ()));
        ourAllOptions.add(new Option("-rf", "--resume-from", MavenRunnerLocalize.mavenOptionsDescriptionRf()));
        ourAllOptions.add(new Option("-s", "--settings", MavenRunnerLocalize.mavenOptionsDescriptionS()));
        ourAllOptions.add(new Option("-t", "--toolchains", MavenRunnerLocalize.mavenOptionsDescriptionT()));
        ourAllOptions.add(new Option("-T", "--threads", MavenRunnerLocalize.mavenOptionsDescriptionT()));
        ourAllOptions.add(new Option("-U", "--update-snapshots", MavenRunnerLocalize.mavenOptionsDescriptionU()));
        ourAllOptions.add(new Option("-up", "--update-plugins", MavenRunnerLocalize.mavenOptionsDescriptionUp()));
        ourAllOptions.add(new Option("-v", "--version", MavenRunnerLocalize.mavenOptionsDescriptionV()));
        ourAllOptions.add(new Option("-V", "--show-version", MavenRunnerLocalize.mavenOptionsDescriptionV()));
        ourAllOptions.add(new Option("-X", "--debug", MavenRunnerLocalize.mavenOptionsDescriptionX()));

        ourOptionsIndexMap = new HashMap<>();
        for (Option option : ourAllOptions) {
            ourOptionsIndexMap.put(option.getName(false), option);
            ourOptionsIndexMap.put(option.getName(true), option);
        }
    }

    public static class Option {
        private final String myName;
        private final String myLongName;
        private final LocalizeValue myDescription;

        public Option(@Nonnull String name, @Nonnull String longName, @Nonnull LocalizeValue description) {
            myName = name;
            myLongName = longName;
            myDescription = description;
        }

        public String getName(boolean longName) {
            return longName ? myLongName : myName;
        }

        @Nonnull
        public LocalizeValue getDescription() {
            return myDescription;
        }
    }

    public static Collection<Option> getAllOptions() {
        return Collections.unmodifiableSet(ourAllOptions);
    }

    public static Option findOption(String name) {
        return ourOptionsIndexMap.get(name);
    }
}
