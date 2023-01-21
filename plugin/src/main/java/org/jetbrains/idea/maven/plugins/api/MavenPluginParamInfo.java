package org.jetbrains.idea.maven.plugins.api;

import consulo.annotation.access.RequiredReadAction;
import consulo.language.impl.psi.LeafPsiElement;
import consulo.language.psi.PsiElement;
import consulo.maven.internal.plugin.MavenPluginDescriptorCache;
import consulo.maven.plugin.MavenPluginDescriptor;
import consulo.maven.plugin.MavenPluginDescriptorParam;
import consulo.maven.rt.server.common.model.MavenId;
import consulo.xml.psi.xml.XmlTag;
import consulo.xml.psi.xml.XmlText;
import consulo.xml.psi.xml.XmlTokenType;
import consulo.xml.util.xml.DomElement;
import consulo.xml.util.xml.DomManager;
import org.jetbrains.idea.maven.dom.model.*;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.function.BiPredicate;

/**
 * @author Sergey Evdokimov
 */
public class MavenPluginParamInfo
{
	@RequiredReadAction
	public static boolean isSimpleText(@Nonnull XmlText paramValue)
	{
		PsiElement prevSibling = paramValue.getPrevSibling();
		if(!(prevSibling instanceof LeafPsiElement) || ((LeafPsiElement) prevSibling).getElementType() != XmlTokenType.XML_TAG_END)
		{
			return false;
		}

		PsiElement nextSibling = paramValue.getNextSibling();
		if(!(nextSibling instanceof LeafPsiElement) || ((LeafPsiElement) nextSibling).getElementType() != XmlTokenType.XML_END_TAG_START)
		{
			return false;
		}

		return true;
	}

	public static void processParamInfo(@Nonnull XmlText paramValue, @Nonnull BiPredicate<MavenPluginDescriptorParam, MavenDomConfiguration> processor)
	{
		XmlTag paramTag = paramValue.getParentTag();
		if(paramTag == null)
		{
			return;
		}

		XmlTag configurationTag = paramTag;
		DomElement domElement;

		while(true)
		{
			configurationTag = configurationTag.getParentTag();
			if(configurationTag == null)
			{
				return;
			}

			String tagName = configurationTag.getName();
			if("configuration".equals(tagName))
			{
				domElement = DomManager.getDomManager(configurationTag.getProject()).getDomElement(configurationTag);
				if(domElement instanceof MavenDomConfiguration)
				{
					break;
				}

				if(domElement != null)
				{
					return;
				}
			}
		}

		MavenDomConfiguration domCfg = (MavenDomConfiguration) domElement;

		MavenDomPlugin domPlugin = domCfg.getParentOfType(MavenDomPlugin.class, true);
		if(domPlugin == null)
		{
			return;
		}

		Map<MavenId, MavenPluginDescriptor> descriptors = MavenPluginDescriptorCache.getDescriptors();

		String pluginGroupId = domPlugin.getGroupId().getStringValue();
		String pluginArtifactId = domPlugin.getArtifactId().getStringValue();

		MavenPluginDescriptor descriptor;

		if(pluginGroupId == null)
		{
			descriptor = descriptors.get(new MavenId("org.apache.maven.plugins", pluginArtifactId));
			if(descriptor == null)
			{
				descriptor = descriptors.get(new MavenId("org.codehaus.mojo", pluginArtifactId));
			}
		}
		else
		{
			descriptor = descriptors.get(new MavenId(pluginGroupId, pluginArtifactId));
		}

		if(descriptor == null)
		{
			return;
		}

		DomElement parent = domCfg.getParent();
		if(parent instanceof MavenDomPluginExecution)
		{
			MavenDomGoals goals = ((MavenDomPluginExecution) parent).getGoals();
			for(MavenDomGoal goal : goals.getGoals())
			{
				MavenPluginDescriptorParam info = descriptor.getParam(goal.getStringValue());
				if(info != null && !processor.test(info, domCfg))
				{
					return;
				}
			}

			MavenPluginDescriptorParam param = descriptor.getParam(paramTag.getName());
			if(param != null && !processor.test(param, domCfg))
			{
				return;
			}
		}

//		ParamInfo defaultInfo = goalsMap.get(null);
//		if(defaultInfo != null)
//		{
//			if(!processor.test(defaultInfo, domCfg))
//			{
//				return;
//			}
//		}
	}
}
