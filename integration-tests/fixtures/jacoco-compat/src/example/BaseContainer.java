package example;

/**
 * Generic base class. When {@link StringContainer} overrides {@link #get()}
 * with a covariant return type, javac generates a synthetic bridge method
 * {@code Object get()} in StringContainer — exercising the {@code bridge}
 * and {@code synthetic} flags in the rules engine.
 */
public abstract class BaseContainer<T> {
    public abstract T get();
}
