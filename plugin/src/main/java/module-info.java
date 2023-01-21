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
	requires org.jetbrains.idea.maven.artifact.resolver.m2;
	requires org.jetbrains.idea.maven.artifact.resolver.m3;
	requires org.jetbrains.idea.maven.server.m3.common;
	requires org.jetbrains.idea.maven.server.m30;
	requires org.jetbrains.idea.maven.server.m32;
	requires org.jetbrains.idea.maven.server.m2;

	// TODO remove in future
	requires consulo.ide.impl;
	requires java.desktop;
	requires forms.rt;
}