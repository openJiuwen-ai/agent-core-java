/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.common.async;

import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Spliterator;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

/**
 * Typed compatibility bridge for APIs that historically behaved like a list
 * while newer SDK code consumes a {@link CompletableFuture} of that list.
 *
 * @param <T> element type
 */
public class FutureList<T> extends CompletableFuture<List<T>> implements List<T> {

    public FutureList(CompletableFuture<? extends List<T>> delegate) {
        delegate.whenComplete((value, error) -> {
            if (error != null) {
                completeExceptionally(error);
                return;
            }
            complete(value == null ? List.of() : value);
        });
    }

    public static <T> FutureList<T> completed(List<T> value) {
        return new FutureList<>(CompletableFuture.completedFuture(value == null ? List.of() : value));
    }

    public static <T> FutureList<T> fromFuture(CompletableFuture<? extends List<T>> future) {
        return new FutureList<>(future == null ? CompletableFuture.completedFuture(List.of()) : future);
    }

    private List<T> value() {
        return join();
    }

    @Override
    public List<T> join() {
        return super.join();
    }

    public CompletableFuture<List<T>> toCompletableFuture() {
        return this;
    }

    @Override
    public int size() {
        return value().size();
    }

    @Override
    public boolean isEmpty() {
        return value().isEmpty();
    }

    @Override
    public boolean contains(Object item) {
        return value().contains(item);
    }

    @Override
    public Iterator<T> iterator() {
        return value().iterator();
    }

    @Override
    public Object[] toArray() {
        return value().toArray();
    }

    @Override
    public <E> E[] toArray(E[] array) {
        return value().toArray(array);
    }

    @Override
    public boolean add(T item) {
        return value().add(item);
    }

    @Override
    public boolean remove(Object item) {
        return value().remove(item);
    }

    @Override
    public boolean containsAll(Collection<?> collection) {
        return value().containsAll(collection);
    }

    @Override
    public boolean addAll(Collection<? extends T> collection) {
        return value().addAll(collection);
    }

    @Override
    public boolean addAll(int index, Collection<? extends T> collection) {
        return value().addAll(index, collection);
    }

    @Override
    public boolean removeAll(Collection<?> collection) {
        return value().removeAll(collection);
    }

    @Override
    public boolean retainAll(Collection<?> collection) {
        return value().retainAll(collection);
    }

    @Override
    public void replaceAll(UnaryOperator<T> operator) {
        value().replaceAll(operator);
    }

    @Override
    public void sort(Comparator<? super T> comparator) {
        value().sort(comparator);
    }

    @Override
    public void clear() {
        value().clear();
    }

    @Override
    public T get(int index) {
        return value().get(index);
    }

    @Override
    public T set(int index, T element) {
        return value().set(index, element);
    }

    @Override
    public void add(int index, T element) {
        value().add(index, element);
    }

    @Override
    public T remove(int index) {
        return value().remove(index);
    }

    @Override
    public int indexOf(Object item) {
        return value().indexOf(item);
    }

    @Override
    public int lastIndexOf(Object item) {
        return value().lastIndexOf(item);
    }

    @Override
    public ListIterator<T> listIterator() {
        return value().listIterator();
    }

    @Override
    public ListIterator<T> listIterator(int index) {
        return value().listIterator(index);
    }

    @Override
    public List<T> subList(int fromIndex, int toIndex) {
        return value().subList(fromIndex, toIndex);
    }

    @Override
    public Spliterator<T> spliterator() {
        return value().spliterator();
    }

    @Override
    public Stream<T> stream() {
        return value().stream();
    }

    @Override
    public Stream<T> parallelStream() {
        return value().parallelStream();
    }

    @Override
    public boolean removeIf(Predicate<? super T> filter) {
        return value().removeIf(filter);
    }

    @Override
    public void forEach(Consumer<? super T> action) {
        value().forEach(action);
    }
}
