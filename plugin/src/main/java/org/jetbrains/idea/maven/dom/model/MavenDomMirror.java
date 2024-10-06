package org.jetbrains.idea.maven.dom.model;

import consulo.xml.util.xml.Convert;
import consulo.xml.util.xml.GenericDomValue;

import javax.annotation.Nonnull;

import org.jetbrains.idea.maven.dom.MavenDomElement;
import org.jetbrains.idea.maven.dom.converters.repositories.MavenRepositoryConverter;

/**
 * @author Sergey Evdokimov
 */
public interface MavenDomMirror extends MavenDomElement {
    @Nonnull
    @Convert(MavenRepositoryConverter.Url.class)
    GenericDomValue<String> getUrl();
}
