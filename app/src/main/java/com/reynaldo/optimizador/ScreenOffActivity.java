package com.reynaldo.optimizador;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Toast;

import rikka.shizuku.Shizuku;

/**
 * Atajo del icono ("Apagar pantalla"): apaga la pantalla con Shizuku y se cierra sola.
 * No muestra nada en pantalla.
 */
public class ScreenOffActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        new Thread(() -> {
            final String error = turnOff();
            runOnUiThread(() -> {
                if (error != null) {
                    Toast.makeText(getApplicationContext(), error, Toast.LENGTH_LONG).show();
                }
                finish();
            });
        }).start();
    }

    /** Devuelve null si se apagó la pantalla, o el mensaje de error. */
    private String turnOff() {
        try {
            // Si la app acaba de arrancar, Shizuku puede tardar un instante en responder.
            for (int i = 0; i < 15 && !Shizuku.pingBinder(); i++) Thread.sleep(100);
            if (!Shizuku.pingBinder()) {
                return "Shizuku no está activo. Ábrelo e inícialo.";
            }
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                return "Abre Optimizador y concede el permiso de Shizuku.";
            }
            Sh.run("input keyevent 223");
            return null;
        } catch (Throwable t) {
            return "No se pudo apagar la pantalla.";
        }
    }
}
