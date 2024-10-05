// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.dom.references;

import consulo.document.util.TextRange;
import consulo.ide.impl.idea.openapi.vfs.VfsUtil;
import consulo.language.psi.*;
import consulo.language.psi.path.FileReference;
import consulo.language.psi.path.FileReferenceSet;
import consulo.platform.Platform;
import consulo.util.lang.function.Condition;
import consulo.util.lang.function.Conditions;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import consulo.xml.psi.impl.source.xml.XmlFileImpl;
import consulo.xml.util.xml.ConvertContext;
import consulo.xml.util.xml.DomElement;
import consulo.xml.util.xml.DomUtil;
import consulo.xml.util.xml.GenericDomValue;
import consulo.xml.util.xml.converters.PathReferenceConverter;
import org.jetbrains.idea.maven.dom.MavenPropertyResolver;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

/**
 * @author Sergey Evdokimov
 */
public class MavenPathReferenceConverter extends PathReferenceConverter {
    private final Condition<PsiFileSystemItem> myCondition;

    public MavenPathReferenceConverter() {
        this(Conditions.alwaysTrue());
    }

    public MavenPathReferenceConverter(@Nonnull Condition<PsiFileSystemItem> condition) {
        myCondition = condition;
    }

    public static PsiReference[] createReferences(
        final DomElement genericDomValue,
        PsiElement element,
        @Nonnull final Condition<PsiFileSystemItem> fileFilter
    ) {
        return createReferences(genericDomValue, element, fileFilter, false);
    }

    public static PsiReference[] createReferences(
        final DomElement genericDomValue,
        PsiElement element,
        @Nonnull final Condition<PsiFileSystemItem> fileFilter, boolean isAbsolutePath
    ) {
        TextRange range = ElementManipulators.getValueTextRange(element);
        String text = range.substring(element.getText());

        FileReferenceSet set =
            new FileReferenceSet(text, element, range.getStartOffset(), null, Platform.current().fs().isCaseSensitive(), false) {

                private MavenDomProjectModel model;

                @Override
                public Condition<PsiFileSystemItem> getReferenceCompletionFilter() {
                    return fileFilter;
                }

                @Override
                protected boolean isSoft() {
                    return true;
                }

                @Override
                public FileReference createFileReference(TextRange range, int index, String text) {
                    return new FileReference(this, range, index, text) {
                        @Override
                        protected void innerResolveInContext(
                            @Nonnull String text,
                            @Nonnull PsiFileSystemItem context,
                            Collection<ResolveResult> result,
                            boolean caseSensitive
                        ) {
                            if (model == null) {
                                DomElement rootElement = DomUtil.getFileElement(genericDomValue).getRootElement();
                                if (rootElement instanceof MavenDomProjectModel) {
                                    model = (MavenDomProjectModel)rootElement;
                                }
                            }

                            String resolvedText = model == null ? text : MavenPropertyResolver.resolve(text, model);

                            if (resolvedText.equals(text)) {
                                if (getIndex() == 0 && resolvedText.length() == 2 && resolvedText.charAt(1) == ':') {
                                    // it's root on windows, e.g. "C:"
                                    VirtualFile file = LocalFileSystem.getInstance().findFileByPath(resolvedText + '/');
                                    if (file != null) {
                                        PsiDirectory psiDirectory = context.getManager().findDirectory(file);
                                        if (psiDirectory != null) {
                                            result.add(new PsiElementResolveResult(psiDirectory));
                                        }
                                    }
                                }
                                else if (getIndex() == getAllReferences().length - 1 &&
                                    Objects.equals("relativePath", genericDomValue.getXmlElementName()) &&
                                    context.getVirtualFile() != null) {
                                    // it is a last context and should be resolved to pom.xml

                                    VirtualFile parentFile = context.getVirtualFile().findChild(text);
                                    if (parentFile != null) {
                                        VirtualFile parentPom = parentFile.isDirectory() ? parentFile.findChild("pom.xml") : parentFile;
                                        if (parentPom != null) {
                                            PsiFile psiFile = context.getManager().findFile(parentPom);
                                            if (psiFile != null) {
                                                result.add(new PsiElementResolveResult(psiFile));
                                            }
                                        }
                                    }
                                }
                                else if ("..".equals(resolvedText)) {
                                    PsiFileSystemItem resolved = context.getParent();
                                    if (resolved != null) {
                                        if (context instanceof XmlFileImpl) {
                                            resolved = resolved.getParent();  // calculated regarding parent directory, not the pom itself
                                        }
                                        if (resolved != null) {
                                            result.add(new PsiElementResolveResult(resolved));
                                        }
                                    }
                                }
                                else {
                                    super.innerResolveInContext(resolvedText, context, result, caseSensitive);
                                }
                            }
                            else {
                                VirtualFile contextFile = context.getVirtualFile();
                                if (contextFile == null) {
                                    return;
                                }

                                VirtualFile file = null;

                                if (getIndex() == 0) {
                                    file = LocalFileSystem.getInstance().findFileByPath(resolvedText);
                                }

                                if (file == null) {
                                    file = LocalFileSystem.getInstance().findFileByPath(contextFile.getPath() + '/' + resolvedText);
                                }

                                if (file != null) {
                                    PsiFileSystemItem res =
                                        file.isDirectory() ? context.getManager().findDirectory(file) : context.getManager().findFile(file);

                                    if (res != null) {
                                        result.add(new PsiElementResolveResult(res));
                                    }
                                }
                            }
                        }
                    };
                }
            };

        if (isAbsolutePath) {
            set.addCustomization(FileReferenceSet.DEFAULT_PATH_EVALUATOR_OPTION, file -> {
                VirtualFile virtualFile = file.getVirtualFile();

                if (virtualFile == null) {
                    return FileReferenceSet.ABSOLUTE_TOP_LEVEL.apply(file);
                }

                virtualFile = VfsUtil.getRootFile(virtualFile);
                PsiDirectory root = file.getManager().findDirectory(virtualFile);

                if (root == null) {
                    return FileReferenceSet.ABSOLUTE_TOP_LEVEL.apply(file);
                }

                return Collections.singletonList(root);
            });
        }

        return set.getAllReferences();
    }

    @Nonnull
    @Override
    public PsiReference[] createReferences(final GenericDomValue genericDomValue, PsiElement element, ConvertContext context) {
        return createReferences(genericDomValue, element, myCondition);
    }

    @Nonnull
    @Override
    public PsiReference[] createReferences(@Nonnull PsiElement psiElement, boolean soft) {
        throw new UnsupportedOperationException();
    }
}