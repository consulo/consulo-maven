package consulo.maven.rt.server.common.util;

import jakarta.annotation.Nonnull;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import jakarta.annotation.Nullable;

/**
 * @author VISTALL
 * @since 2019-03-07
 */
public class MavenReflectionUtil
{
	@Nonnull
	public static List<Field> collectFields(@Nonnull Class clazz)
	{
		List<Field> result = new ArrayList<Field>();
		collectFields(clazz, result);
		return result;
	}

	private static void collectFields(Class clazz, List<? super Field> result)
	{
		result.addAll(Arrays.asList(clazz.getDeclaredFields()));

		Class superClass = clazz.getSuperclass();
		if(superClass != null)
		{
			collectFields(superClass, result);
		}

		for(Class each : clazz.getInterfaces())
		{
			collectFields(each, result);
		}
	}

	public static <T> T getField(@Nonnull Class objectClass,
								 @Nullable Object object,
								 @Nullable Class<T> fieldType,
								 @Nonnull String fieldName)
	{
		Field field = findField(objectClass, fieldName, fieldType);
		if(field != null)
		{
			try
			{
				@SuppressWarnings("unchecked") T t = (T) field.get(object);
				return t;
			}
			catch(IllegalAccessException ignored)
			{
			}
		}

		return null;
	}

	@Nullable
	private static Field findField(Class clazz, String fieldName, @Nullable Class<?> fieldType)
	{
		for(Field field : clazz.getDeclaredFields())
		{
			if(fieldName.equals(field.getName()) && (fieldType == null || fieldType.isAssignableFrom(field.getType())))
			{
				field.setAccessible(true);
				return field;
			}
		}

		Class superClass = clazz.getSuperclass();
		if(superClass != null)
		{
			Field result = findField(superClass, fieldName, fieldType);
			if(result != null)
			{
				return result;
			}
		}

		for(Class each : clazz.getInterfaces())
		{
			Field result = findField(each, fieldName, fieldType);
			if(result != null)
			{
				return result;
			}
		}

		return null;
	}
}
