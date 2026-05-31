package jzelda.patterns;

import jzelda.model.GameModel;

/**
 * Immutable notification sent by the observable model to its observers.
 */
public final class ModelEvent {
    private final String type;
    private final String message;
    private final GameModel model;

    /**
     * Creates a new model event.
     *
     * @param type textual event type, for example {@code STATE_CHANGED}
     * @param message optional human-readable message shown by the view
     * @param model the model that produced the event
     */
    public ModelEvent(String type, String message, GameModel model) {
        this.type = type;
        this.message = message;
        this.model = model;
    }

    /**
     * @return event type
     */
    public String getType() {
        return type;
    }

    /**
     * @return optional message
     */
    public String getMessage() {
        return message;
    }

    /**
     * @return model that produced the event
     */
    public GameModel getModel() {
        return model;
    }
}
