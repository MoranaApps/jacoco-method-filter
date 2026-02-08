package example;

/**
 * Concrete container whose covariant {@link #get()} causes javac to emit
 * a synthetic bridge method ({@code Object get()}).
 *
 * Also provides helper methods used to exercise predicates:
 * <ul>
 *   <li>{@code initData()} — void return, name starts with "init" → matched by {@code ret:V} + {@code name-starts:init}</li>
 *   <li>{@code processData()} — returns String, contains "Data" → matched by {@code name-contains:Data}</li>
 *   <li>{@code getData()} — kept method, rescued by include rule</li>
 * </ul>
 */
public class StringContainer extends BaseContainer<String> {

    private final String value;

    public StringContainer(String value) {
        this.value = value;
    }

    /** Covariant override — javac also emits a bridge {@code Object get()}. */
    @Override
    public String get() {
        return value;
    }

    /** Void-returning init helper — targeted by {@code ret:V name-starts:init}. */
    public void initData() {
        // no-op for test purposes
    }

    /** Returns String, name contains "Data" — targeted by {@code name-contains:Data}. */
    public String processData() {
        return value.toUpperCase();
    }

    /** Also contains "Data" but rescued by an include rule. */
    public String getData() {
        return value;
    }
}
