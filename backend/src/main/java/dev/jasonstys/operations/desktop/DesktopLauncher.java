/*
 * File: DesktopLauncher.java
 * Purpose: Start the genuine bundled Java/local-web application and own its complete lifecycle.
 * State: one profile, Spring context and native window. Symbol lines: docs/code-index.md.
 */
package dev.jasonstys.operations.desktop;

import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.GraphicsEnvironment;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.URI;
import java.nio.file.Path;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.server.context.WebServerApplicationContext;

import dev.jasonstys.operations.OperationsConsoleApplication;

/** jpackage starts this mode through the ordinary Boot executable jar; development mode is unchanged. */
public final class DesktopLauncher {
    private DesktopLauncher() {
    }

    /** @param args packaged launches accept no arbitrary Spring/server overrides */
    public static void main(String[] args) {
        DesktopState state = null;
        try {
            if (args.length != 0) {
                throw new IllegalArgumentException("Use the packaged application without command-line arguments");
            }
            boolean test = "1".equals(System.getenv("IOC_DESKTOP_TEST"));
            Path location = DesktopProfile.location(System.getenv(), System.getProperty("os.name"),
                    Path.of(System.getProperty("user.home")));
            DesktopProfile profile = DesktopProfile.open(location);
            state = new DesktopState(profile);
            DesktopState owned = state;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    owned.close();
                } catch (Exception failure) {
                    System.err.println("Local workspace close requires review");
                }
            }, "ioc-owned-shutdown"));
            SpringApplication application = new SpringApplication(OperationsConsoleApplication.class);
            application.setHeadless(false);
            application.setRegisterShutdownHook(false);
            application.setAdditionalProfiles("desktop");
            application.setDefaultProperties(Map.of("ioc.desktop.enabled", "true",
                    "ioc.desktop.database-url", profile.databaseUrl(),
                    "ioc.desktop.database-username", profile.username(),
                    "ioc.desktop.database-password", profile.password()));
            application.addInitializers(context -> context.getBeanFactory()
                    .registerSingleton("desktopState", owned));
            var context = application.run("--server.address=127.0.0.1", "--server.port=0",
                    "--ioc.desktop.enabled=true", "--server.forward-headers-strategy=NONE",
                    "--server.shutdown=graceful", "--spring.lifecycle.timeout-per-shutdown-phase=10s",
                    "--management.server.port=-1",
                    "--server.servlet.context-path=");
            if (!(context instanceof WebServerApplicationContext web)) {
                context.close();
                throw new IllegalStateException("The local web service did not start");
            }
            state.attach(context, web.getWebServer().getPort());
            if (!(test && "1".equals(System.getenv("IOC_DESKTOP_NO_WINDOW")))) {
                SwingUtilities.invokeAndWait(() -> showWindow(owned));
            }
            state.ready(test);
            if (!(test && "1".equals(System.getenv("IOC_DESKTOP_NO_BROWSER")))) {
                browse(state);
            }
        } catch (Exception failure) {
            if (state != null) {
                try {
                    state.close();
                } catch (Exception closeFailure) {
                    System.err.println("Preserve the existing workspace and seek support");
                }
            }
            String message = "Integration Operations Console could not start. Existing workspace data and "
                    + "settings were not reset. Close another running copy or review the private configuration.";
            System.err.println(message);
            if (!GraphicsEnvironment.isHeadless() && !"1".equals(System.getenv("IOC_DESKTOP_TEST"))) {
                JOptionPane.showMessageDialog(null, message, "Integration Operations Console",
                        JOptionPane.ERROR_MESSAGE);
            }
            System.exit(2);
        }
    }

    private static void browse(DesktopState state) {
        try {
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                throw new IllegalStateException("System browser is unavailable");
            }
            Desktop.getDesktop().browse(URI.create(state.launchUrl()));
        } catch (Exception failure) {
            JOptionPane.showMessageDialog(null, "A browser could not open. Use Open console again. "
                    + "The local application remains available.", "Open console", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private static void showWindow(DesktopState state) {
        JFrame frame = new JFrame("Integration Operations Console — Jason Stys");
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        JPanel content = new JPanel(new BorderLayout(12, 16));
        content.setBorder(BorderFactory.createEmptyBorder(22, 22, 22, 22));
        content.add(new JLabel("<html><b>Integration Operations Console " + DesktopState.VERSION
                + "</b><br><br>Local synthetic integrations, reliable jobs and reviewable history."
                + "<br>Workspace data persists outside the installation."
                + "<br>Open the console for a guided first workflow and help."
                + "<br><br>Close this window or choose Quit to stop the local service safely."
                + "<br>Created by Jason Stys · github.com/JasonStys</html>"), BorderLayout.CENTER);
        JPanel buttons = new JPanel();
        JButton open = new JButton("Open console");
        open.addActionListener(event -> browse(state));
        JButton quit = new JButton("Quit application");
        quit.addActionListener(event -> state.requestClose());
        buttons.add(open);
        buttons.add(quit);
        content.add(buttons, BorderLayout.SOUTH);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                state.requestClose();
            }
        });
        frame.setContentPane(content);
        frame.pack();
        frame.setLocationByPlatform(true);
        state.window(frame);
        frame.setVisible(true);
    }
}
