package com.salvox.chargefx;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;

/**
 * Ejecuta comandos como root via el binario 'su' que expone Magisk.
 * La primera vez que se llama, Magisk muestra al usuario un dialogo
 * pidiendo conceder acceso root a esta app.
 */
public class RootShell {

    public interface ResultCallback {
        void onResult(String output);
    }

    /** Ejecuta un comando root y devuelve stdout+stderr combinados como texto. */
    public static String runBlocking(String command) {
        StringBuilder result = new StringBuilder();
        try {
            Process process = Runtime.getRuntime().exec("su");
            DataOutputStream out = new DataOutputStream(process.getOutputStream());
            out.writeBytes(command + "\n");
            out.writeBytes("exit\n");
            out.flush();

            BufferedReader stdout = new BufferedReader(new InputStreamReader(process.getInputStream()));
            BufferedReader stderr = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            String line;
            while ((line = stdout.readLine()) != null) {
                result.append(line).append("\n");
            }
            while ((line = stderr.readLine()) != null) {
                result.append(line).append("\n");
            }
            process.waitFor();
        } catch (Exception e) {
            result.append("Error ejecutando comando root: ").append(e.getMessage());
        }
        return result.toString();
    }

    /** Version asincrona para no bloquear el hilo principal de la UI. */
    public static void run(String command, ResultCallback callback) {
        new Thread(() -> {
            String output = runBlocking(command);
            new android.os.Handler(android.os.Looper.getMainLooper())
                    .post(() -> callback.onResult(output));
        }).start();
    }
}
