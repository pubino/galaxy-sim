package astro.galaxy;

import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.lang.reflect.Field;
import java.util.Random;
import java.util.StringJoiner;
import javax.swing.SwingUtilities;

/** Exports original presets and samples the unchanged desktop integrator. */
public final class OrbitReference extends GalaxyCanvas {
    @Override public void paint(Graphics g) {}

    private static Galaxy load(String name, String method, String count, String rotation, Random random) {
        random.setSeed(524L);
        Galaxy app = new Galaxy();
        app.init();
        app.gc = new OrbitReference();
        app.SELECTION.setSelectedItem(name);
        app.INTEGRATOR.setSelectedItem(method);
        // The desktop label says RungeKutta4, while its dispatch compares RungeKutta.
        if (method.equals("RungeKutta4")) app.integrator = app.gc.RUNGEKUTTA;
        app.ROTRATE.setText(rotation);
        if (!count.isEmpty()) app.N.setText(count);
        Data.M = Data.N = Data.K = 0;
        app.actionPerformed(new ActionEvent(app.START, ActionEvent.ACTION_PERFORMED, "Start"));
        app.gc.timer.stop();
        return app;
    }

    private static String bodies(GalaxyCanvas canvas) {
        StringJoiner data = new StringJoiner(",", "[", "]");
        for (var body : canvas.p) {
            data.add("[" + body.m + "," + body.rad + "," + (body.col.getRGB() & 0xFFFFFF)
                + "," + body.x + "," + body.y
                + "," + body.vx + "," + body.vy  + "]");
        }
        return data.toString();
    }

    private static String paths(GalaxyCanvas canvas) {
        StringJoiner result = new StringJoiner(",", "[", "]");
        if (canvas.pathx == null || Data.as == null) return result.toString();
        for (int i = 0; i < canvas.pathx.length; i++) {
            StringJoiner track = new StringJoiner(",", "[", "]");
            boolean visible = false;
            for (int j = 0; j < canvas.pathx[i].length; j++) {
                double x = canvas.pathx[i][j], y = canvas.pathy[i][j];

                visible |= x != 0 || y != 0 ;
                track.add("[" + x + "," + y  + "]");
            }
            if (visible) result.add(track.toString());
        }
        return result.toString();
    }

    private static void catalog(Random random) {
        Galaxy choices = new Galaxy();
        choices.init();
        StringJoiner scenarios = new StringJoiner(",", "[", "]");
        for (int i = 1; i < choices.SELECTION.getItemCount(); i++) {
            String name = (String) choices.SELECTION.getItemAt(i);
            Galaxy app = load(name, "Animate Only", "", "0", random);
            GalaxyCanvas canvas = app.gc;
            scenarios.add("{\"name\":\"" + name + "\",\"dt\":" + canvas.dt
                + ",\"warp\":" + canvas.warp + ",\"variable\":" + app.N.isEnabled()
                + ",\"bodies\":" + bodies(canvas) + ",\"paths\":" + paths(canvas) + "}");
        }
        System.out.println(scenarios);
    }

    private static void trace(String[] args, Random random) {
        GalaxyCanvas canvas = load(args[1], args[2], args[3], args[4], random).gc;
        StringJoiner trace = new StringJoiner(",", "[", "]");
        int previous = 0;
        for (int k = 5; k < args.length; k++) {
            int step = Integer.parseInt(args[k]);
            if (step > previous) {
                canvas.warp = step - previous;
                canvas.actionPerformed(null);
            }
            StringJoiner derivatives = new StringJoiner(",", "[", "]");
            for (var body : canvas.p) {
                derivatives.add("[" + body.ax + "," + body.ay + "," + body.jx + "," + body.jy + "]");
            }
            trace.add("[" + step + "," + canvas.t + "," + canvas.theta
                + "," + bodies(canvas) + "," + derivatives + "]");
            previous = step;
        }
        System.out.println(trace);
    }

    public static void main(String[] args) throws ReflectiveOperationException, InterruptedException {
        Field field = Class.forName("java.lang.Math$RandomNumberGeneratorHolder")
            .getDeclaredField("randomNumberGenerator");
        field.setAccessible(true);
        Random random = (Random) field.get(null);
        SwingUtilities.invokeAndWait(() -> {
            if (args[0].equals("catalog")) catalog(random);
            else trace(args, random);
        });
        System.exit(0);
    }
}
