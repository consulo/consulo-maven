package org.jetbrains.idea.maven.util;

/**
 * @author VISTALL
 * @since 2019-03-07
 */
public interface MavenFunction<PARAM, RESULT>
{
	RESULT invoke(PARAM param);
}
