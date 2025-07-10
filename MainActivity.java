package com.example.spotifyautoskipper;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import okhttp3.*;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private final String clientId = "8fd0a9e5e8264c5da83c0982055a249c";
    private final String redirectUri = "spotifyautoskipper://callback";
    private final String authUrl = "https://accounts.spotify.com/authorize" +
            "?client_id=" + clientId +
            "&response_type=token" +
            "&redirect_uri=" + redirectUri +
            "&scope=user-modify-playback-state user-read-playback-state";

    private String accessToken = null;
    private TextView statusTextView;
    private TextView timerTextView;
    private Button skipButton;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable skipRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

		statusTextView = findViewById(R.id.statusTextView);
        timerTextView = findViewById(R.id.timerTextView);
        skipButton = findViewById(R.id.skipButton);

        skipButton.setOnClickListener(v -> skipTrack());

        // OAuth login or resume from redirect
        Intent intent = getIntent();
        Uri data = intent.getData();
        if (data != null && data.toString().startsWith(redirectUri)) {
            // Parse access token from redirect URL fragment
            String fragment = data.getFragment();
            if (fragment != null && fragment.contains("access_token=")) {
                accessToken = extractAccessToken(fragment);
                statusTextView.setText("Logged in");
                startAutoSkip();
            } else {
                statusTextView.setText("Login failed");
            }
        } else {
            // Start OAuth flow
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(authUrl));
            startActivity(browserIntent);
        }
    }

    private String extractAccessToken(String fragment) {
        String[] parts = fragment.split("&");
        for (String part : parts) {
            if (part.startsWith("access_token=")) {
                return part.substring("access_token=".length());
            }
        }
        return null;
    }

    private void startAutoSkip() {
        skipRunnable = new Runnable() {
            long secondsRemaining = 180;

            @Override
            public void run() {
                if (secondsRemaining <= 0) {
                    skipTrack();
                    secondsRemaining = 180;
                } else {
                    timerTextView.setText("Nächster Skip in: " + secondsRemaining + " s");
                    secondsRemaining--;
                }
                handler.postDelayed(this, 1000);
            }
        };
        handler.post(skipRunnable);
    }

    private void skipTrack() {
        if (accessToken == null) {
            statusTextView.setText("Nicht eingeloggt");
            return;
        }

        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url("https://api.spotify.com/v1/me/player/next")
                .addHeader("Authorization", "Bearer " + accessToken)
                .post(RequestBody.create(new byte[0]))
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> statusTextView.setText("Fehler beim Skippen"));
            }

            @Override
            public void onResponse(Call call, Response response) {
                if (response.isSuccessful()) {
                    runOnUiThread(() -> statusTextView.setText("Track geskippt"));
                } else {
                    runOnUiThread(() -> statusTextView.setText("Skip fehlgeschlagen: " + response.code()));
                }
            }
        });
    }
}
