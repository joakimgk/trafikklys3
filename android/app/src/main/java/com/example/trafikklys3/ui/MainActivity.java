package com.example.trafikklys3.ui;

import android.os.Bundle;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.trafikklys3.R;
import com.example.trafikklys3.controller.ShowController;
import com.example.trafikklys3.network.ServerService;

import java.net.SocketException;

public class MainActivity extends AppCompatActivity {

    private ServerService serverService;

    private static final int TEMPO_MIN = 1;
    private static final int TEMPO_MAX = 255;

    private int tempo = 60;

    private TrafficLightContainer mContainer;
    private TextView tempoIndicator;
    private Button tempoPlusButton;
    private Button tempoMinusButton;
    private SeekBar tempoSlider;

    private ShowController controller;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mContainer = findViewById(R.id.traffic_light_container);
        controller = new ShowController(mContainer);


        tempoIndicator = findViewById(R.id.tempoIndicator);
        tempoPlusButton = findViewById(R.id.tempoPlusButton);
        tempoMinusButton = findViewById(R.id.tempoMinusButton);
        tempoSlider = findViewById(R.id.tempoSlider);

        updateTempoDisplay();

        tempoPlusButton.setOnClickListener(v -> {
            checkTempo(tempo + 1);
        });

        tempoMinusButton.setOnClickListener(v -> {
            checkTempo(tempo - 1);
        });

        // perform seek bar change listener event used for getting the progress value
        tempoSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) return;

                tempo = Math.max(1, progress);
                tempoIndicator.setText("tempo: " + tempo);

                checkTempo(tempo);
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

    }

    private void checkTempo(int newTempo) {
        if (newTempo < TEMPO_MIN) newTempo = TEMPO_MIN;
        if (newTempo > TEMPO_MAX) newTempo = TEMPO_MAX;

        if (newTempo == tempo) return;

        tempo = newTempo;
        updateTempoDisplay();

        controller.setTempo(tempo);
    }

    private void updateTempoDisplay() {
        tempoIndicator.setText("tempo: " + tempo);
    }

    @Override
    protected void onStart() {
        super.onStart();
        serverService = new ServerService(this, controller);
        try {
            serverService.start();
        } catch (SocketException e) {
            throw new RuntimeException(e);
        }
        controller.start();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (serverService != null) {
            serverService.stop();
            serverService = null;
        }
        controller.stop();
    }
}