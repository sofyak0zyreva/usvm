package org.usvm.samples.nonAliasing;

import java.util.HashSet;
import java.util.Set;

public class TestRefSet<E> {
    private final Set<E> data = new HashSet<E>();

    public int size() {
        return data.size();
    }

    boolean contains(final E elem) {
        return data.contains(elem);
    }

    boolean add(final E elem) { return data.add(elem); }

    boolean remove(final E elem) { return data.remove(elem); }


    @Override
    public String toString() {
        return data.toString();
    }
}
