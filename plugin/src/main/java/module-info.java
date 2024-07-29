/**
 * @author VISTALL
 * @since 20/01/2023
 */
open module org.jetbrains.idea.maven
{
	requires consulo.ide.api;

	requires com.intellij.xml;

	requires com.intellij.properties;

	requires consulo.java.execution.impl;
	requires consulo.java.compiler.artifact.impl;
	requires consulo.java.language.api;
	requires consulo.java;

	requires com.google.gson;
	requires com.google.common;
	requires commons.cli;

	requires java.rmi;

	requires lucene.core;
	requires wadl.core;

	requires jakarta.xml.bind;

	requires org.jetbrains.idea.maven.artifact.resolver.common;
	requires org.jetbrains.idea.maven.server.common;
	requires org.jetbrains.idea.maven.artifact.resolver.m31;
	requires org.jetbrains.idea.maven.artifact.resolver.m3;
	requires org.jetbrains.idea.maven.server.m3.common;
	requires org.jetbrains.idea.maven.server.m30;
	requires org.jetbrains.idea.maven.server.m32;

	// TODO remove in future
	requires consulo.ide.impl;
	requires java.desktop;
	requires forms.rt;

	exports consulo.maven;
	exports consulo.maven.bundle;
	exports consulo.maven.icon;
	exports consulo.maven.importProvider;
	exports consulo.maven.importing;
	exports consulo.maven.internal.org.jvnet.ws.wadl.util;
	exports consulo.maven.internal.plugin;
	exports consulo.maven.module.extension;
	exports consulo.maven.newProject;
	exports consulo.maven.plugin;
	exports consulo.maven.plugin.extension;
	exports consulo.maven.plugin.extension.impl;
	exports consulo.maven.toolWindow;
	exports consulo.maven.util;
	exports org.jetbrains.idea.maven;
	exports org.jetbrains.idea.maven.compiler;
	exports org.jetbrains.idea.maven.dom;
	exports org.jetbrains.idea.maven.dom.annotator;
	exports org.jetbrains.idea.maven.dom.code;
	exports org.jetbrains.idea.maven.dom.converters;
	exports org.jetbrains.idea.maven.dom.converters.repositories;
	exports org.jetbrains.idea.maven.dom.converters.repositories.beans;
	exports org.jetbrains.idea.maven.dom.generate;
	exports org.jetbrains.idea.maven.dom.inspections;
	exports org.jetbrains.idea.maven.dom.intentions;
	exports org.jetbrains.idea.maven.dom.model;
	exports org.jetbrains.idea.maven.dom.model.completion;
	exports org.jetbrains.idea.maven.dom.plugin;
	exports org.jetbrains.idea.maven.dom.refactorings;
	exports org.jetbrains.idea.maven.dom.refactorings.extract;
	exports org.jetbrains.idea.maven.dom.refactorings.introduce;
	exports org.jetbrains.idea.maven.dom.references;
	exports org.jetbrains.idea.maven.execution;
	exports org.jetbrains.idea.maven.execution.cmd;
	exports org.jetbrains.idea.maven.importing;
	exports org.jetbrains.idea.maven.importing.configurers;
	exports org.jetbrains.idea.maven.indices;
	exports org.jetbrains.idea.maven.localize;
	exports org.jetbrains.idea.maven.model.impl;
	exports org.jetbrains.idea.maven.navigator;
	exports org.jetbrains.idea.maven.navigator.actions;
	exports org.jetbrains.idea.maven.plugins.api;
	exports org.jetbrains.idea.maven.plugins.api.common;
	exports org.jetbrains.idea.maven.project;
	exports org.jetbrains.idea.maven.project.actions;
	exports org.jetbrains.idea.maven.server;
	exports org.jetbrains.idea.maven.services;
	exports org.jetbrains.idea.maven.services.artifactory;
	exports org.jetbrains.idea.maven.services.nexus;
	exports org.jetbrains.idea.maven.tasks;
	exports org.jetbrains.idea.maven.tasks.actions;
	exports org.jetbrains.idea.maven.tasks.compiler;
	exports org.jetbrains.idea.maven.utils;
	exports org.jetbrains.idea.maven.utils.actions;
	exports org.jetbrains.idea.maven.utils.library;
	exports org.jetbrains.idea.maven.vfs;
	exports org.jetbrains.idea.maven.wizards;
}