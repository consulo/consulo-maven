package org.jetbrains.idea.maven.util;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Random;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.jetbrains.annotations.NonNls;

/**
 * @author VISTALL
 * @since 2019-03-07
 */
public class MavenFileUtil
{
	private static String ourCanonicalTempPathCache;

	@Nonnull
	public static String toSystemIndependentName(@NonNls @Nonnull String fileName)
	{
		return fileName.replace('\\', '/');
	}

	@Nonnull
	public static File createTempFile(@Nonnull @NonNls String prefix, @Nullable @NonNls String suffix) throws IOException
	{
		return createTempFile(prefix, suffix, false); //false until TeamCity fixes its plugin
	}

	@Nonnull
	public static File createTempFile(@Nonnull @NonNls String prefix, @Nullable @NonNls String suffix,
									  boolean deleteOnExit) throws IOException
	{
		final File dir = new File(getTempDirectory());
		return createTempFile(dir, prefix, suffix, true, deleteOnExit);
	}

	@Nonnull
	public static File createTempFile(@NonNls File dir,
									  @Nonnull @NonNls String prefix, @Nullable @NonNls String suffix) throws IOException
	{
		return createTempFile(dir, prefix, suffix, true, true);
	}

	@Nonnull
	public static File createTempFile(@NonNls File dir,
									  @Nonnull @NonNls String prefix, @Nullable @NonNls String suffix,
									  boolean create) throws IOException
	{
		return createTempFile(dir, prefix, suffix, create, true);
	}

	@Nonnull
	public static File createTempFile(@NonNls File dir,
									  @Nonnull @NonNls String prefix, @Nullable @NonNls String suffix,
									  boolean create, boolean deleteOnExit) throws IOException
	{
		File file = doCreateTempFile(dir, prefix, suffix, false);
		if(deleteOnExit)
		{
			//noinspection SSBasedInspection
			file.deleteOnExit();
		}
		if(!create)
		{
			if(!file.delete() && file.exists())
			{
				throw new IOException("Cannot delete file: " + file);
			}
		}
		return file;
	}

	private static final Random RANDOM = new Random();

	@Nonnull
	private static File doCreateTempFile(@Nonnull File dir,
										 @Nonnull @NonNls String prefix,
										 @Nullable @NonNls String suffix,
										 boolean isDirectory) throws IOException
	{
		//noinspection ResultOfMethodCallIgnored
		dir.mkdirs();

		if(prefix.length() < 3)
		{
			prefix = (prefix + "___").substring(0, 3);
		}
		if(suffix == null)
		{
			suffix = "";
		}
		// normalize and use only the file name from the prefix
		prefix = new File(prefix).getName();

		int attempts = 0;
		int i = 0;
		int maxFileNumber = 10;
		IOException exception = null;
		while(true)
		{
			File f = null;
			try
			{
				f = calcName(dir, prefix, suffix, i);

				boolean success = isDirectory ? f.mkdir() : f.createNewFile();
				if(success)
				{
					return normalizeFile(f);
				}
			}
			catch(IOException e)
			{ // Win32 createFileExclusively access denied
				exception = e;
			}
			attempts++;
			int MAX_ATTEMPTS = 100;
			if(attempts > maxFileNumber / 2 || attempts > MAX_ATTEMPTS)
			{
				String[] children = dir.list();
				int size = children == null ? 0 : children.length;
				maxFileNumber = Math.max(10, size * 10); // if too many files are in tmp dir, we need a bigger random range than meager 10
				if(attempts > MAX_ATTEMPTS)
				{
					throw exception != null ? exception : new IOException("Unable to create temporary file " + f + "\nDirectory '" + dir +
							"' list (" + size + " children): " + Arrays.toString(children));
				}
			}

			i++; // for some reason the file1 can't be created (previous file1 was deleted but got locked by anti-virus?). try file2.
			if(i > 2)
			{
				i = 2 + RANDOM.nextInt(maxFileNumber); // generate random suffix if too many failures
			}
		}
	}

	@Nonnull
	private static File normalizeFile(@Nonnull File temp) throws IOException
	{
		final File canonical = temp.getCanonicalFile();
		return MavenSystemInfo.isWindows && canonical.getAbsolutePath().contains(" ") ? temp.getAbsoluteFile() : canonical;
	}

	@Nonnull
	public static String getTempDirectory()
	{
		if(ourCanonicalTempPathCache == null)
		{
			ourCanonicalTempPathCache = calcCanonicalTempPath();
		}
		return ourCanonicalTempPathCache;
	}

	@Nonnull
	private static File calcName(@Nonnull File dir, @Nonnull String prefix, @Nonnull String suffix, int i) throws IOException
	{
		prefix += i == 0 ? "" : i;
		if(prefix.endsWith(".") && suffix.startsWith("."))
		{
			prefix = prefix.substring(0, prefix.length() - 1);
		}
		String name = prefix + suffix;
		File f = new File(dir, name);
		if(!name.equals(f.getName()))
		{
			throw new IOException("Generated name is malformed. name='" + name + "'; new File(dir, name)='" + f + "'");
		}
		return f;
	}

	@Nonnull
	private static String calcCanonicalTempPath()
	{
		final File file = new File(System.getProperty("java.io.tmpdir"));
		try
		{
			final String canonical = file.getCanonicalPath();
			if(!MavenSystemInfo.isWindows || !canonical.contains(" "))
			{
				return canonical;
			}
		}
		catch(IOException ignore)
		{
		}
		return file.getAbsolutePath();
	}
}
