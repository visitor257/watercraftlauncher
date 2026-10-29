package cpw.mods.fml.relauncher;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Injected into the Forge 1.7.2 universal jar by Water Craft Launcher.
 *
 * <p>FML 7.2 (Forge 1.7.2) sorts the launchwrapper tweaker list in place with
 * {@code Collections.sort(List, Comparator)}. Since Java 8 that delegates to
 * {@code List.sort}, and {@code ArrayList.sort} bumps {@code modCount} at the end
 * unconditionally -- so the iterator launchwrapper is holding becomes invalid and the
 * next {@code Iterator.remove()} throws {@link java.util.ConcurrentModificationException}.
 *
 * <p>FML 7.10 fixed this by sorting an array copy and writing it back with {@code set()}.
 * We do the same, which leaves {@code modCount} alone and keeps the ordering FML wants.
 */
public final class McverSortFix {
    private McverSortFix() {
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void sort(List list, Object comparator) {
        Object[] copy = list.toArray();
        Arrays.sort(copy, (Comparator) comparator);
        for (int i = 0; i < copy.length; i++) {
            list.set(i, copy[i]);
        }
    }
}
