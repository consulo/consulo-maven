package consulo.maven.importing;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * @author VISTALL
 * @since 2018-08-18
 */
public class MavenImportSession
{
	private final Map<Object, Object> myCacheValues = new HashMap<>();

	@SuppressWarnings("unchecked")
	public <K, V> V getOrCalculate(K key, Function<K, V> function)
	{
		return (V) myCacheValues.computeIfAbsent(key, o -> function.apply((K) o));
	}
}
