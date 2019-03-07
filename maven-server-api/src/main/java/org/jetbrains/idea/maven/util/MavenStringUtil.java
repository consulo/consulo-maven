package org.jetbrains.idea.maven.util;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.jetbrains.annotations.Contract;

/**
 * @author VISTALL
 * @since 2019-03-07
 */
public class MavenStringUtil
{
	public static int compareVersionNumbers(@Nullable String v1, @Nullable String v2)
	{
		// todo duplicates com.intellij.util.text.VersionComparatorUtil.compare
		// todo please refactor next time you make changes here
		if(v1 == null && v2 == null)
		{
			return 0;
		}
		if(v1 == null)
		{
			return -1;
		}
		if(v2 == null)
		{
			return 1;
		}

		String[] part1 = v1.split("[._\\-]");
		String[] part2 = v2.split("[._\\-]");

		int idx = 0;
		for(; idx < part1.length && idx < part2.length; idx++)
		{
			String p1 = part1[idx];
			String p2 = part2[idx];

			int cmp;
			if(p1.matches("\\d+") && p2.matches("\\d+"))
			{
				cmp = new Integer(p1).compareTo(new Integer(p2));
			}
			else
			{
				cmp = part1[idx].compareTo(part2[idx]);
			}
			if(cmp != 0)
			{
				return cmp;
			}
		}

		if(part1.length != part2.length)
		{
			boolean left = part1.length > idx;
			String[] parts = left ? part1 : part2;

			for(; idx < parts.length; idx++)
			{
				String p = parts[idx];
				int cmp;
				if(p.matches("\\d+"))
				{
					cmp = new Integer(p).compareTo(0);
				}
				else
				{
					cmp = 1;
				}
				if(cmp != 0)
				{
					return left ? cmp : -cmp;
				}
			}
		}
		return 0;
	}

	@Nonnull
	@Contract(pure = true)
	public static List<String> splitHonorQuotes(@Nonnull String s, char separator)
	{
		List<String> result = new ArrayList<String>();
		StringBuilder builder = new StringBuilder(s.length());
		boolean inQuotes = false;
		for(int i = 0; i < s.length(); i++)
		{
			char c = s.charAt(i);
			if(c == separator && !inQuotes)
			{
				if(builder.length() > 0)
				{
					result.add(builder.toString());
					builder.setLength(0);
				}
				continue;
			}
			if((c == '"' || c == '\'') && !(i > 0 && s.charAt(i - 1) == '\\'))
			{
				inQuotes = !inQuotes;
			}
			builder.append(c);
		}
		if(builder.length() > 0)
		{
			result.add(builder.toString());
		}
		return result;
	}

	public static boolean equal(@Nullable String arg1, @Nullable String arg2)
	{
		return arg1 == null ? arg2 == null : arg1.equals(arg2);
	}

	@Contract(value = "null -> true", pure = true)
	public static boolean isEmpty(@Nullable CharSequence cs)
	{
		return cs == null || cs.length() == 0;
	}

	@Contract(value = "null -> true", pure = true)
	public static boolean isEmptyOrSpaces(@Nullable CharSequence s)
	{
		if(isEmpty(s))
		{
			return true;
		}
		for(int i = 0; i < s.length(); i++)
		{
			if(s.charAt(i) > ' ')
			{
				return false;
			}
		}
		return true;
	}

	@Nonnull
	@Contract(pure = true)
	public static String formatFileSize(long fileSize)
	{
		return formatFileSize(fileSize, " ");
	}

	@Nonnull
	@Contract(pure = true)
	public static String formatFileSize(long fileSize, @Nonnull String unitSeparator)
	{
		if(fileSize < 0)
		{
			throw new IllegalArgumentException("Invalid value: " + fileSize);
		}
		if(fileSize == 0)
		{
			return '0' + unitSeparator + 'B';
		}
		int rank = (int) ((Math.log10(fileSize) + 0.0000021714778384307465) / 3);  // (3 - Math.log10(999.995))
		double value = fileSize / Math.pow(1000, rank);
		String[] units = {
				"B",
				"kB",
				"MB",
				"GB",
				"TB",
				"PB",
				"EB"
		};
		return new DecimalFormat("0.##").format(value) + unitSeparator + units[rank];
	}
}
