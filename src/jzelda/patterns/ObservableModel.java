package jzelda.patterns;

/**
 * Observable side of the Observer pattern used by the game model.
 */
public interface ObservableModel {
    /**
     * Registers an observer if it is not already registered.
     *
     * @param observer observer to register
     */
    void addObserver(ModelObserver observer);

    /**
     * Removes a previously registered observer.
     *
     * @param observer observer to remove
     */
    void removeObserver(ModelObserver observer);

    /**
     * Notifies all observers of a model change.
     *
     * @param type event type
     * @param message optional event message
     */
    void notifyObservers(String type, String message);
}
