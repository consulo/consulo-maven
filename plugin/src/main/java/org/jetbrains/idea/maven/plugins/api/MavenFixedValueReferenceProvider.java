package org.jetbrains.idea.maven.plugins.api;

import consulo.annotation.access.RequiredReadAction;
import consulo.document.util.TextRange;
import consulo.language.psi.*;
import consulo.language.util.ProcessingContext;
import org.jetbrains.idea.maven.dom.MavenPropertyResolver;
import org.jetbrains.idea.maven.dom.model.MavenDomConfiguration;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.regex.Matcher;

/**
 * @author Sergey Evdokimov
 */
public class MavenFixedValueReferenceProvider implements MavenParamReferenceProvider, MavenSoftAwareReferenceProvider {
    private final String[] myValues;

    private boolean mySoft = false;

    public MavenFixedValueReferenceProvider(String[] values) {
        myValues = values;
    }

    @Override
    @RequiredReadAction
    public PsiReference[] getReferencesByElement(
        @Nonnull PsiElement element,
        @Nonnull MavenDomConfiguration domCfg,
        @Nonnull ProcessingContext context
    ) {
        ElementManipulator<PsiElement> manipulator = ElementManipulators.getManipulator(element);
        TextRange range = manipulator.getRangeInElement(element);

        String text = range.substring(element.getText());
        Matcher matcher = MavenPropertyResolver.PATTERN.matcher(text);
        if (matcher.find()) {
            return PsiReference.EMPTY_ARRAY;
        }

        return new PsiReference[]{
            new PsiReferenceBase<>(element, mySoft) {
                @Nullable
                @Override
                @RequiredReadAction
                public PsiElement resolve() {
                    if (mySoft) {
                        return null;
                    }

                    if (Arrays.asList(myValues).contains(getValue())) {
                        return getElement();
                    }

                    return null;
                }

                @Nonnull
                @Override
                @RequiredReadAction
                public Object[] getVariants() {
                    return myValues;
                }
            }
        };
    }

    @Override
    public void setSoft(boolean soft) {
        mySoft = soft;
    }
}
