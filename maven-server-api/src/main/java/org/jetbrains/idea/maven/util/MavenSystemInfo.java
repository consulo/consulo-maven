package org.jetbrains.idea.maven.util;

import java.util.Locale;

/**
 * @author VISTALL
 * @since 2019-03-07
 */
public class MavenSystemInfo
{
	public static final boolean isWindows = isWindows();

	private static boolean isWindows()
	{
		String osName = System.getProperty("os.name");
		osName = osName.toLowerCase(Locale.US);
		return osName.startsWith("windows");
	}
}
