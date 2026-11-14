package com.reynaldo.optimizador;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;

import rikka.shizuku.Shizuku;

/** Ejecuta comandos de shell con los permisos de Shizuku. */
final class Sh {
    private Sh() {}

    static String run(String cmd) {
        try {
            Method m = Shizuku.class.getDeclaredMethod("newProcess",
                    String[].class, String[].class, String.class);
            m.setAccessible(true);
            Process p = (Process) m.invoke(null, new Object[]{
                    new String[]{"sh", "-c", "(" + cmd + ") 2>&1"}, null, null});
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String l;
                while ((l = r.readLine()) != null) sb.append(l).append('\n');
            }
            p.waitFor();
            return sb.toString();
        } catch (Throwable t) {
            return "Error: " + t;
        }
    }
}
