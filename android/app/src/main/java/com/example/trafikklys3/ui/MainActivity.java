package com.example.trafikklys3.ui;

import static com.example.trafikklys3.model.Programs.PROGRAMS;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.trafikklys3.R;
import com.example.trafikklys3.controller.SetupController;
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
    private Button changeProgram, resetProgram, setupButton;
    private SeekBar tempoSlider;

    private ShowController showController;

    private SetupController setupController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mContainer = findViewById(R.id.traffic_light_container);

        serverService = new ServerService(this);
        showController = new ShowController(serverService, mContainer, serverService.getRegistry());
        serverService.getRegistry().setListener(showController);

        setupController = new SetupController(serverService.getRegistry(), mContainer, serverService, showController);
        mContainer.setSetupController(setupController);

        setupButton = findViewById(R.id.setupButton);
        changeProgram = findViewById(R.id.changeProgram);
        resetProgram = findViewById(R.id.resetProgram);
        tempoIndicator = findViewById(R.id.tempoIndicator);
        tempoPlusButton = findViewById(R.id.tempoPlusButton);
        tempoMinusButton = findViewById(R.id.tempoMinusButton);
        tempoSlider = findViewById(R.id.tempoSlider);

        updateTempoDisplay();

        setupButton.setOnClickListener(v -> {
            setupController.startSetup();
        });

        tempoPlusButton.setOnClickListener(v -> {
            checkTempo(tempo + 1);
        });

        tempoMinusButton.setOnClickListener(v -> {
            checkTempo(tempo - 1);
        });

        changeProgram.setOnClickListener(v -> {
            // TODO: Use programs from Animate
            program++;
            if (program == PROGRAMS.length) program = 0;
            int len = PROGRAMS[program].length;
            showController.transmitProgram(PROGRAMS[program]);
        });

        resetProgram.setOnClickListener(v -> {
            Log.v("JOAKIM", "Send reset");
            showController.resetProgram();
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

        showController.setTempo(tempo);
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
        showController.start();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (serverService != null) {
            serverService.stop();
            serverService = null;
        }
        showController.stop();
    }
}