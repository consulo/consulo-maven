package org.jetbrains.idea.maven.dom.model;

import javax.annotation.Nonnull;

import org.jetbrains.idea.maven.dom.MavenDomElement;

import java.util.List;

/**
 * @author Sergey Evdokimov
 */
public interface MavenDomMirrors extends MavenDomElement {
    @Nonnull
    List<MavenDomMirror> getMirrors();
}
