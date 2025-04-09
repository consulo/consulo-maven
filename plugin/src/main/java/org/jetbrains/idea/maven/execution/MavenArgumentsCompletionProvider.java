// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.execution;

import consulo.application.dumb.DumbAware;
import consulo.language.editor.completion.CompletionResultSet;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.jetbrains.idea.maven.execution.cmd.CommandLineCompletionProvider;
import org.jetbrains.idea.maven.localize.MavenLocalize;
import org.jetbrains.idea.maven.localize.MavenRunnerLocalize;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.util.List;

public class MavenArgumentsCompletionProvider extends CommandLineCompletionProvider implements DumbAware {

    private static final Options ourOptions;

    static {
        // Copy pasted from org.apache.maven.cli.CLIManager.<init>()

        Options options = new Options();
        options.addOption(Option.builder("h").longOpt("help").desc(MavenRunnerLocalize.mavenOptionsDescriptionH().get()).build());
        options.addOption(Option.builder("f").longOpt("file").hasArg().desc(MavenRunnerLocalize.mavenOptionsDescriptionF().get()).build());
        options.addOption(Option.builder("D").longOpt("define").hasArg().desc(MavenRunnerLocalize.mavenOptionsDescriptionD().get()).build());
        options.addOption(Option.builder("o").longOpt("offline").desc(MavenRunnerLocalize.mavenOptionsDescriptionO().get()).build());
        options.addOption(Option.builder("v").longOpt("version").desc(MavenRunnerLocalize.mavenOptionsDescriptionV().get()).build());
        options.addOption(Option.builder("q").longOpt("quiet").desc(MavenRunnerLocalize.mavenOptionsDescriptionQ().get()).build());
        options.addOption(Option.builder("X").longOpt("debug").desc(MavenRunnerLocalize.mavenOptionsDescriptionX().get()).build());
        options.addOption(Option.builder("e").longOpt("errors").desc(MavenRunnerLocalize.mavenOptionsDescriptionE().get()).build());
        options.addOption(Option.builder("N").longOpt("non-recursive").desc(MavenRunnerLocalize.mavenOptionsDescriptionN().get()).build());
        options.addOption(Option.builder("U").longOpt("update-snapshots")
            .desc(MavenLocalize.mavenSettingsGeneralUpdateSnapshotsTooltip().get()).build());
        options.addOption(
            Option.builder("P").longOpt("activate-profiles").desc(MavenRunnerLocalize.mavenOptionsDescriptionP().get()).hasArg().build());
        options.addOption(Option.builder("B").longOpt("batch-mode").desc(MavenRunnerLocalize.mavenOptionsDescriptionB().get()).build());
        options
            .addOption(Option.builder("nsu").longOpt("no-snapshot-updates").desc(MavenRunnerLocalize.mavenOptionsDescriptionNsu().get()).build());
        options.addOption(Option.builder("C").longOpt("strict-checksums").desc(MavenRunnerLocalize.mavenOptionsDescriptionC().get()).build());
        options.addOption(Option.builder("c").longOpt("lax-checksums").desc(MavenRunnerLocalize.mavenOptionsDescriptionC().get()).build());
        options.addOption(Option.builder("s").longOpt("settings").desc(MavenRunnerLocalize.mavenOptionsDescriptionS().get()).hasArg().build());
        options.addOption(
            Option.builder("gs").longOpt("global-settings").desc(MavenRunnerLocalize.mavenOptionsDescriptionGs().get()).hasArg().build());
        options.addOption(Option.builder("t").longOpt("toolchains").desc(MavenRunnerLocalize.mavenOptionsDescriptionT().get()).hasArg().build());
        options.addOption(Option.builder("ff").longOpt("fail-fast").desc(MavenRunnerLocalize.mavenOptionsDescriptionFf().get()).build());
        options.addOption(Option.builder("fae").longOpt("fail-at-end").desc(MavenRunnerLocalize.mavenOptionsDescriptionFae().get()).build());
        options.addOption(Option.builder("fn").longOpt("fail-never").desc(MavenRunnerLocalize.mavenOptionsDescriptionFn().get()).build());
        options
            .addOption(Option.builder("rf").longOpt("resume-from").hasArg().desc(MavenRunnerLocalize.mavenOptionsDescriptionRf().get()).build());
        options.addOption(Option.builder("pl").longOpt("projects").desc(MavenRunnerLocalize.mavenOptionsDescriptionPl().get()).hasArg().build());
        options.addOption(Option.builder("am").longOpt("also-make").desc(MavenRunnerLocalize.mavenOptionsDescriptionAm().get()).build());
        options
            .addOption(Option.builder("amd").longOpt("also-make-dependents").desc(MavenRunnerLocalize.mavenOptionsDescriptionAmd().get()).build());
        options.addOption(Option.builder("l").longOpt("log-file").hasArg().desc(MavenRunnerLocalize.mavenOptionsDescriptionL().get()).build());
        options.addOption(Option.builder("V").longOpt("show-version").desc(MavenRunnerLocalize.mavenOptionsDescriptionV().get()).build());
        options.addOption(
            Option.builder("emp").longOpt("encrypt-master-password").hasArg().desc(MavenRunnerLocalize.mavenOptionsDescriptionEmp().get())
                .build());
        options.addOption(
            Option.builder("ep").longOpt("encrypt-password").hasArg().desc(MavenRunnerLocalize.mavenOptionsDescriptionEp().get()).build());
        options.addOption(Option.builder("T").longOpt("threads").hasArg().desc(MavenRunnerLocalize.mavenOptionsDescriptionT().get()).build());

        ourOptions = options;
    }

    private volatile List<LookupElement> myCachedElements;
    private final Project myProject;


    public MavenArgumentsCompletionProvider(@Nonnull Project project) {
        super(ourOptions);
        myProject = project;
    }

    @Override
    protected void addArgumentVariants(@Nonnull CompletionResultSet result) {
        List<LookupElement> cachedElements = myCachedElements;
        if (cachedElements == null) {
            cachedElements = MavenUtil.getPhaseVariants(MavenProjectsManager.getInstance(myProject));

            myCachedElements = cachedElements;
        }

        result.addAllElements(cachedElements);
    }
}
