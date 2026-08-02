package CampusConnect.app;

import CampusConnect.domain.Person;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Who is using the app right now.
 * <p>
 * V1 had no such notion — it was a graph editor, and a graph editor has no "you". Every
 * V2 feature does: recommendations are <em>for</em> someone, the similarity heatmap is
 * relative to someone, and a connection request comes <em>from</em> someone. Without a
 * current user none of them can even be phrased.
 * <p>
 * Deliberately observable. Switching user has to invalidate recommendation caches, repaint
 * the canvas and refresh the profile panel, and having each of those subscribe is far more
 * robust than remembering to call three methods at every call site.
 */
public final class AppSession {

    private Person currentUser;
    private final List<Consumer<Person>> listeners = new ArrayList<>();

    public Person getCurrentUser() { return currentUser; }

    public boolean hasCurrentUser() { return currentUser != null; }

    /** Sets the active user and notifies everyone. Passing null signs out. */
    public void setCurrentUser(Person person) {
        if (this.currentUser == person) return;
        this.currentUser = person;
        for (Consumer<Person> listener : new ArrayList<>(listeners)) {
            listener.accept(person);
        }
    }

    /** Subscribe to user changes. Fires immediately with the current value. */
    public void addListener(Consumer<Person> listener) {
        if (listener == null) return;
        listeners.add(listener);
        listener.accept(currentUser);
    }

    /** Clears the active user if it is the person being removed from the graph. */
    public void forget(Person person) {
        if (currentUser == person) setCurrentUser(null);
    }

    public String displayName() {
        return currentUser == null ? "(nobody)" : currentUser.getName();
    }
}
