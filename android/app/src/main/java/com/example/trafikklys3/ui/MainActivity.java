package com.example.trafikklys3.ui;

import android.content.Context;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.PopupMenu;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.trafikklys3.R;
import com.example.trafikklys3.controller.ShowClock;
import com.example.trafikklys3.controller.ShowController;
import com.example.trafikklys3.controller.TrafficLightListener;
import com.example.trafikklys3.model.Client;
import com.example.trafikklys3.model.ClientRegistry;
import com.example.trafikklys3.model.LightStore;
import com.example.trafikklys3.network.ServerService;
import com.example.trafikklys3.network.SetupService;

import java.net.SocketException;

public class MainActivity extends AppCompatActivity implements TrafficLightListener {

    private ServerService serverService;

    private static final int TEMPO_MIN = 1;
    private static final int TEMPO_MAX = 255;

    private int tempo = 60;

    private TrafficLightContainer mContainer;
    private TextView tempoIndicator;
    private Button changeProgram, resetProgram, stopButton, setupButton;
    private SeekBar tempoSlider;

    private View detailsPanel;
    private TextView detailsText;
    private Button rotateButton, removeButton;

    private ShowController showController;

    private SetupService setupService;

    private ClientRegistry registry;

    private LightStore lightStore;

    private TrafficLight selected;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mContainer = findViewById(R.id.traffic_light_container);

        registry = new ClientRegistry();
        Context context = getApplicationContext();
        ShowClock clock = new ShowClock();
        serverService = new ServerService(context, registry, clock);
        lightStore = new LightStore(context);
        showController = new ShowController(serverService, mContainer, registry, clock);
        registry.setListener(showController);
        tempo = showController.getTempo();

        mContainer.setListener(this);
        loadLights();

        setupService = new SetupService(registry, mContainer, serverService, showController, lightStore);
        setupService.setSelectionAccess(() -> selected, this::select);

        setupButton = findViewById(R.id.setupButton);
        changeProgram = findViewById(R.id.changeProgram);
        resetProgram = findViewById(R.id.resetProgram);
        stopButton = findViewById(R.id.stopButton);
        tempoIndicator = findViewById(R.id.tempoIndicator);
        tempoSlider = findViewById(R.id.tempoSlider);
        detailsPanel = findViewById(R.id.details_panel);
        detailsText = findViewById(R.id.details_text);
        rotateButton = findViewById(R.id.rotate_button);
        removeButton = findViewById(R.id.remove_button);

        updateTempoDisplay();
        tempoSlider.setProgress(tempo);

        // Setup needs at least one unit to talk to
        setupButton.setEnabled(false);
        showController.setPresenceListener(() -> {
            setupButton.setEnabled(setupService.isAssigning() || showController.hasOnlineUnit());
            updateDetails();
        });

        setupButton.setOnClickListener(v -> {
            String problem = setupService.isAssigning()
                    ? setupService.confirmCurrent()
                    : setupService.startSetup();
            if (problem != null) Toast.makeText(this, problem, Toast.LENGTH_LONG).show();
            setupButton.setText(setupService.getButtonLabel());
            updateDetails();
        });

        changeProgram.setOnClickListener(v -> {
            if (setupService.isAssigning()) return;  // units are busy identifying themselves
            showController.nextShow();
            showShowName();
        });

        changeProgram.setOnLongClickListener(v -> {
            if (setupService.isAssigning()) return true;
            showProgramMenu(v);
            return true;
        });

        resetProgram.setOnClickListener(v -> {
            if (setupService.isAssigning()) return;
            showController.resetProgram();
        });

        stopButton.setOnClickListener(v -> {
            if (setupService.isAssigning()) return;
            showController.stopShow();
        });

        rotateButton.setOnClickListener(v -> {
            if (selected == null) return;
            selected.rotate();
            lightStore.save(mContainer.getLights());
        });

        removeButton.setOnClickListener(v -> removeSelected());

        // perform seek bar change listener event used for getting the progress value
        tempoSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) tempoIndicator.setText("tempo: " + Math.max(1, progress));
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                checkTempo(Math.max(1, seekBar.getProgress()));
            }
        });

    }

    // Lights saved from an earlier session come back in place; the ones with a unit start offline
    private void loadLights() {
        boolean anyAssigned = false;
        for (LightStore.Record r : lightStore.load()) {
            TrafficLight light = mContainer.addLight(r.id, r.x, r.y, r.orientation);
            if (r.clientId != null) {
                Client client = registry.addKnown(r.clientId);
                client.setTrafficLight(light);
                light.setClient(client);
                anyAssigned = true;
            }
        }
        if (anyAssigned) showController.scheduleAutoStart();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_add_light) {
            TrafficLight light = mContainer.addNewLight();
            lightStore.save(mContainer.getLights());
            select(light);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ---- lights on screen ----

    @Override
    public void onTrafficLightSelected(TrafficLight light) {
        if (setupService.isAssigning() && !setupService.onLightSelected(light)) {
            Toast.makeText(this, "That light already has a unit. Pick a free light.",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        select(light);
    }

    @Override
    public void onTrafficLightChanged(TrafficLight light) {
        lightStore.save(mContainer.getLights());
    }

    private void select(TrafficLight light) {
        if (selected != null) selected.setHighlighted(false);
        selected = light;
        if (light != null) light.setHighlighted(true);
        updateDetails();
    }

    private void updateDetails() {
        if (selected == null) {
            detailsPanel.setVisibility(View.INVISIBLE);
            return;
        }

        String text = "Light " + selected.getLightId() + "  ·  ";
        Client client = selected.getClient();
        if (client == null) {
            text += "no unit assigned";
        } else {
            text += "ESP " + client.clientID + "  ·  " + (client.isOnline() ? "online" : "offline");
        }
        detailsText.setText(text);
        detailsPanel.setVisibility(View.VISIBLE);
    }

    private void removeSelected() {
        if (selected == null) return;
        if (setupService.isAssigning()) {
            Toast.makeText(this, "Finish setup first", Toast.LENGTH_SHORT).show();
            return;
        }

        TrafficLight light = selected;
        if (light.getClient() == null) {
            removeLight(light);
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Remove light " + light.getLightId() + "?")
                .setMessage("Its unit (ESP " + light.getClient().clientID + ") becomes free and can be set up again.")
                .setPositiveButton("Remove", (d, w) -> removeLight(light))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void removeLight(TrafficLight light) {
        Client client = light.getClient();
        if (client != null) {
            showController.stopPreview(client);
            serverService.stopIdentify(client);  // the unit stops showing its old program
            client.setTrafficLight(null);
            light.setClient(null);
            if (client.getAddress() == null) registry.remove(client.clientID);  // remembered, never seen
        }

        mContainer.removeLight(light);
        if (selected == light) select(null);
        lightStore.save(mContainer.getLights());
    }

    // ---- programs ----

    private void showShowName() {
        Toast.makeText(this, "Show: " + showController.getShowName(), Toast.LENGTH_SHORT).show();
    }

    private void showProgramMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        String[] names = showController.getShowNames();
        for (int i = 0; i < names.length; i++) {
            MenuItem item = menu.getMenu().add(Menu.NONE, i, i, names[i]);
            item.setCheckable(true);
            item.setChecked(i == showController.getShowIndex());
        }
        menu.getMenu().setGroupCheckable(Menu.NONE, true, true);
        menu.setOnMenuItemClickListener(item -> {
            showController.selectShow(item.getItemId());
            showShowName();
            return true;
        });
        menu.show();
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
        if (!setupService.isAssigning()) showController.resumeAutoStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (serverService != null) {
            serverService.stop();
        }
        showController.stop();
    }
}
