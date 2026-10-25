package dev.mcver.probe;
import dev.mcver.core.JavaFinder;
public final class ProbeJava {
    public static void main(String[] a) {
        for (JavaFinder.Found f : JavaFinder.all()) System.out.println(f.label());
    }
}
