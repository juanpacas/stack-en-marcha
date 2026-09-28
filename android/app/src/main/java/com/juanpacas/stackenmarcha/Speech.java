package com.juanpacas.stackenmarcha;

import android.content.Context;
import android.media.AudioAttributes;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

/**
 * Voz del curso. Vive fuera de la pantalla para que una lista de frases y pausas
 * siga sonando con el celular bloqueado mientras PlaybackService está en primer plano.
 */
final class Speech {

    interface Events {
        void onSegment(int index);   // empezó el segmento index de la cola
        void onQueueDone();          // terminó el último segmento
        void onQueueStopped();       // se detuvo desde la notificación
        void onSingleDone(String id);
    }

    private static Speech instance;

    static synchronized Speech get(Context c) {
        if (instance == null) instance = new Speech(c.getApplicationContext());
        return instance;
    }

    private final Context app;
    private final TextToSpeech tts;
    private volatile boolean ready = false;
    volatile Events events;

    // Cada cola nueva cambia de generación: así se ignoran avisos de colas anteriores
    private volatile int generation = 0;
    private volatile int queueSize = 0;
    private volatile boolean queueActive = false;
    private volatile String queueTitle = "";

    /** Vuelve a mostrar la notificación si hay una cola sonando (p. ej. tras aceptar el permiso). */
    void refreshNotification() {
        if (queueActive) PlaybackService.start(app, queueTitle);
    }

    private Speech(Context app) {
        this.app = app;
        tts = new TextToSpeech(app, status -> {
            if (status != TextToSpeech.SUCCESS) return;
            Locale[] prefs = { new Locale("es", "US"), new Locale("es", "MX"), new Locale("es", "CO"),
                    new Locale("es", "ES"), new Locale("es") };
            for (Locale l : prefs) {
                if (getTts().setLanguage(l) >= TextToSpeech.LANG_AVAILABLE) break;
            }
            getTts().setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build());
            getTts().setOnUtteranceProgressListener(new Listener());
            ready = true;
        });
    }

    private TextToSpeech getTts() { return tts; }

    boolean isReady() { return ready; }

    /** Una frase suelta (botón Escuchar). Detiene cualquier cola. */
    boolean speakOne(String text, float rate, String id) {
        if (!ready) return false;
        endQueue();
        tts.setSpeechRate(rate);
        return tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "s:" + id) == TextToSpeech.SUCCESS;
    }

    /** Encola todos los segmentos: {"say": texto} o {"pause": milisegundos}. */
    boolean playQueue(String json, float rate, String title) {
        if (!ready) return false;
        try {
            JSONArray segs = new JSONArray(json);
            if (segs.length() == 0) return false;
            tts.stop();
            int g = ++generation;
            queueSize = segs.length();
            queueActive = true;
            tts.setSpeechRate(rate);
            for (int i = 0; i < segs.length(); i++) {
                JSONObject s = segs.getJSONObject(i);
                String id = "q:" + g + ":" + i;
                if (s.has("pause")) tts.playSilentUtterance(Math.max(1, s.getLong("pause")), TextToSpeech.QUEUE_ADD, id);
                else tts.speak(s.optString("say", ""), TextToSpeech.QUEUE_ADD, null, id);
            }
            queueTitle = title;
            PlaybackService.start(app, title);
            return true;
        } catch (Exception e) {
            queueActive = false;
            return false;
        }
    }

    /** Detiene todo sin avisar a la pantalla (la pantalla lo pidió). */
    void stop() {
        endQueue();
        if (ready) tts.stop();
    }

    /** Detiene todo y avisa a la pantalla (botón Detener de la notificación). */
    void stopFromUser() {
        boolean wasActive = queueActive;
        stop();
        Events e = events;
        if (wasActive && e != null) e.onQueueStopped();
    }

    private void endQueue() {
        if (queueActive) {
            queueActive = false;
            generation++;
            PlaybackService.stop(app);
        }
    }

    private class Listener extends UtteranceProgressListener {
        @Override
        public void onStart(String id) {
            int[] q = parse(id);
            Events e = events;
            if (q != null && e != null) e.onSegment(q[1]);
        }

        @Override
        public void onDone(String id) { finished(id); }

        @Override
        public void onError(String id) { finished(id); }

        private void finished(String id) {
            Events e = events;
            if (id.startsWith("s:")) {
                if (e != null) e.onSingleDone(id.substring(2));
                return;
            }
            int[] q = parse(id);
            if (q != null && q[1] == queueSize - 1) {
                queueActive = false;
                PlaybackService.stop(app);
                if (e != null) e.onQueueDone();
            }
        }

        /** "q:gen:index" → {gen, index} si es de la cola actual; si no, null. */
        private int[] parse(String id) {
            if (!id.startsWith("q:")) return null;
            String[] p = id.split(":");
            int g = Integer.parseInt(p[1]);
            if (g != generation || !queueActive) return null;
            return new int[] { g, Integer.parseInt(p[2]) };
        }
    }
}
