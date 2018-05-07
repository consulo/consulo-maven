package org.jetbrains.idea.maven.plugins.api;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.util.ProcessingContext;
import javax.annotation.Nonnull;

import org.jetbrains.idea.maven.dom.MavenPropertyResolver;
import org.jetbrains.idea.maven.dom.model.MavenDomConfiguration;

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
  public PsiReference[] getReferencesByElement(@Nonnull PsiElement element,
                                               @Nonnull MavenDomConfiguration domCfg,
                                               @Nonnull ProcessingContext context) {
    ElementManipulator<PsiElement> manipulator = ElementManipulators.getManipulator(element);
    TextRange range = manipulator.getRangeInElement(element);

    String text = range.substring(element.getText());
    Matcher matcher = MavenPropertyResolver.PATTERN.matcher(text);
    if (matcher.find()) {
      return PsiReference.EMPTY_ARRAY;
    }

    return new PsiReference[] {
      new PsiReferenceBase<PsiElement>(element, mySoft) {
        @javax.annotation.Nullable
        @Override
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
