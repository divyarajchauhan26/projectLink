package CampusConnect.dev;

import CampusConnect.app.AppSession;
import CampusConnect.domain.InterestCatalog;
import CampusConnect.domain.InterestTag;
import CampusConnect.domain.Person;
import CampusConnect.persist.CampusSeed;
import CampusConnect.service.NetworkService;
import CampusConnect.ui.InterestChipPicker;
import CampusConnect.ui.MainFrame;
import CampusConnect.ui.OnboardingWizard;
import CampusConnect.ui.ProfileCard;

import javax.swing.*;
import java.awt.GraphicsEnvironment;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Construction and wiring checks for the UI layer.
 * <p>
 * Swing cannot be meaningfully unit-tested without a robot, but the failures that actually
 * happen here are not subtle interaction bugs — they are null layouts, missing card names,
 * and NPEs in a constructor, all of which surface the moment a component is built. So this
 * builds every panel and dialog for real, drives the parts with logic in them, and fails
 * loudly if anything throws.
 * <p>
 * Skips cleanly on a headless machine rather than failing, so it can live in the same
 * suite as the other harnesses.
 *
 * <pre>java -cp "out;lib/*" CampusConnect.dev.UiHarness</pre>
 */
public class UiHarness {

    private static int checks = 0, failures = 0;

    private static void check(String label, boolean ok) {
        checks++;
        if (!ok) failures++;
        System.out.println("  " + (ok ? "PASS  " : "FAIL  ") + label);
    }

    private static void checkNoThrow(String label, Runnable body) {
        try {
            body.run();
            check(label, true);
        } catch (Throwable t) {
            check(label + "  [" + t.getClass().getSimpleName() + ": " + t.getMessage() + "]", false);
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("\n=== SESSION (headless-safe) ===");
        sessionLogic();

        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("\n  (no display — skipping component construction)");
        } else {
            System.out.println("\n=== COMPONENTS ===");
            components();
        }

        System.out.println();
        System.out.println(failures == 0
                ? "=== ALL " + checks + " CHECKS PASSED ==="
                : "=== " + failures + " of " + checks + " CHECKS FAILED ===");
        System.exit(failures == 0 ? 0 : 1);
    }

    // ---------- pure logic, no display needed ----------

    private static void sessionLogic() {
        NetworkService svc = new NetworkService();
        CampusSeed.load(svc, 1100, 700);
        Person kabir = svc.findUserByName("Kabir Khan");
        Person aarav = svc.findUserByName("Aarav Jain");

        AppSession session = new AppSession();
        AtomicInteger notifications = new AtomicInteger();
        AtomicReference<Person> lastSeen = new AtomicReference<>();

        session.addListener(p -> { notifications.incrementAndGet(); lastSeen.set(p); });

        // Subscribing fires immediately, so a listener never has to be primed by hand.
        check("listener fires on subscribe", notifications.get() == 1);
        check("initial user is null", lastSeen.get() == null);

        session.setCurrentUser(kabir);
        check("listener fires on change", notifications.get() == 2);
        check("current user is set", session.getCurrentUser() == kabir);
        check("hasCurrentUser reports true", session.hasCurrentUser());
        check("displayName reflects the user", session.displayName().equals("Kabir Khan"));

        // Re-setting the same person must not re-notify: switching user rebuilds the
        // recommender, and a redundant rebuild on every canvas click would be costly.
        session.setCurrentUser(kabir);
        check("setting the same user does not re-notify", notifications.get() == 2);

        session.setCurrentUser(aarav);
        check("switching user notifies", notifications.get() == 3);

        // Deleting the signed-in person must clear the session, not leave it dangling.
        session.forget(aarav);
        check("forget() clears the active user", session.getCurrentUser() == null);
        session.setCurrentUser(kabir);
        session.forget(aarav);
        check("forget() ignores a different person", session.getCurrentUser() == kabir);
    }

    // ---------- real components ----------

    private static void components() throws Exception {
        NetworkService svc = new NetworkService();
        CampusSeed.load(svc, 1100, 700);
        Person kabir = svc.findUserByName("Kabir Khan");
        Person aarav = svc.findUserByName("Aarav Jain");

        SwingUtilities.invokeAndWait(() -> {
            checkNoThrow("MainFrame constructs", () -> {
                MainFrame frame = new MainFrame();
                frame.pack();
                frame.dispose();
            });

            checkNoThrow("ProfileCard renders a full profile", () -> {
                ProfileCard card = new ProfileCard(svc);
                card.showPerson(kabir, "Suggested because you're both into guitar.");
            });

            checkNoThrow("ProfileCard handles a null person", () -> {
                new ProfileCard(svc).showPerson(null, null);
            });

            checkNoThrow("ProfileCard escapes HTML in free text", () -> {
                Person p = new Person("Test <b>User</b>", 0, 0);
                p.setBio("I like <script>alert(1)</script> & ampersands");
                new ProfileCard(svc).showPerson(p, null);
            });

            checkNoThrow("OnboardingWizard constructs for a new person", () -> {
                new OnboardingWizard(null, svc, null).dispose();
            });

            checkNoThrow("OnboardingWizard prefills an existing profile", () -> {
                new OnboardingWizard(null, svc, kabir).dispose();
            });

            interestPicker(aarav);
        });
    }

    private static void interestPicker(Person aarav) {
        InterestChipPicker picker = new InterestChipPicker();
        check("picker starts empty", picker.getSelectedCount() == 0);

        InterestCatalog catalog = InterestCatalog.getDefault();
        InterestTag guitar = catalog.byId("guitar");
        InterestTag chess = catalog.byId("chess");

        Map<InterestTag, Integer> preload = new LinkedHashMap<>();
        preload.put(guitar, 5);
        preload.put(chess, 2);
        picker.setSelected(preload);
        check("setSelected loads two tags", picker.getSelectedCount() == 2);
        check("intensities survive the round trip",
                picker.getSelected().get(guitar) == 5 && picker.getSelected().get(chess) == 2);

        // applyTo must fully replace, not merge — editing a profile and removing an
        // interest has to actually remove it.
        Person target = new Person("Applied", 0, 0);
        target.addInterest(catalog.byId("cricket"), 4);
        picker.applyTo(target);
        check("applyTo replaces rather than merges", target.getInterests().size() == 2);
        check("applyTo drops the previous interest", !target.hasInterest(catalog.byId("cricket")));
        check("applyTo carries intensity across", target.getIntensity(guitar) == 5);

        // A real profile should survive load -> apply unchanged.
        picker.setSelected(aarav.getInterestIntensities());
        Person copy = new Person("Copy", 0, 0);
        picker.applyTo(copy);
        check("a seeded profile round-trips through the picker",
                copy.getInterestIntensities().equals(aarav.getInterestIntensities()));
    }
}
