package com.example.trafikklys3.ui;

import static com.example.trafikklys3.model.Programs.PROGRAMS;

import android.os.Bundle;
import android.util.Log;
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

    private int program = 0;

    private TrafficLightContainer mContainer;
    private TextView tempoIndicator;
    private Button tempoPlusButton, tempoMinusButton;
    private Button changeProgram, resetProgram;
    private SeekBar tempoSlider;

    private ShowController controller;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mContainer = findViewById(R.id.traffic_light_container);
        serverService = new ServerService(this);
        controller = new ShowController(serverService, mContainer);
        serverService.getRegistry().setListener(controller);

        changeProgram = findViewById(R.id.changeProgram);
        resetProgram = findViewById(R.id.resetProgram);
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

        changeProgram.setOnClickListener(v -> {
            program++;
            if (program == PROGRAMS.length) program = 0;
            int len = PROGRAMS[program].length;
            Log.v("JOAKIM", "Send program #" + program + " (" + len + " bytes)");
            //if (Utility.clients.size() < 1) {
            //    Log.e("MainActivity", "No clients");
            //} else {
            controller.changeProgram(PROGRAMS[program]);
            // TODO: Send different parts of program to different clients...
            //clients.get(0).transmit(payload);
            //}
        });

        resetProgram.setOnClickListener(v -> {
            Log.v("JOAKIM", "Send reset");
            controller.resetProgram();
        });

        // perform seek bar change listener event used for getting the progress value
        tempoSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {}

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                checkTempo(Math.max(1, seekBar.getProgress()));
            }
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