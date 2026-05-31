package jzelda.patterns;

/**
 * Observer used by the MVC view to react to changes in the game model without
 * owning gameplay logic.
 */
@FunctionalInterface
public interface ModelObserver {
    /**
     * Receives a model notification.
     *
     * @param event event data describing the model change
     */
    void onModelChanged(ModelEvent event);
}
