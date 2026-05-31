package jzelda.patterns;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import jzelda.model.GameModel;

/**
 * Base implementation of an observable model. A copy-on-write list is used so
 * that observers can safely be added or removed while notifications are being
 * delivered on the Swing event thread.
 */
public abstract class AbstractObservableModel implements ObservableModel {
    private final List<ModelObserver> observers = new CopyOnWriteArrayList<>();

    @Override
    public void addObserver(ModelObserver observer) {
        if (observer != null && !observers.contains(observer)) {
            observers.add(observer);
        }
    }

    @Override
    public void removeObserver(ModelObserver observer) {
        observers.remove(observer);
    }

    @Override
    public void notifyObservers(String type, String message) {
        ModelEvent event = new ModelEvent(type, message, (GameModel) this);
        observers.forEach(observer -> observer.onModelChanged(event));
    }
}
