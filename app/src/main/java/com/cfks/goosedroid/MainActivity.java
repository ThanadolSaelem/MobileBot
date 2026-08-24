package com.cfks.goosedroid;

import android.annotation.SuppressLint;
import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;

import androidx.appcompat.app.AppCompatActivity;

import com.cfks.goosedroid.GooseDesktop.TheGoose;
import com.cfks.goosedroid.GooseDesktop.PetBrain;
import com.cfks.goosedroid.GooseDesktop.HttpLlmBackend;
import com.cfks.goosedroid.GooseDesktop.LlmActionJson;

import java.util.*;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class MainActivity extends AppCompatActivity {
    private MaterialToolbar DefToolBar;
    private Switch GooseDroid;
    private Switch EnableMods;
    private Switch SilenceSounds;
    private Switch Task_CanAttackMouse;
    private Switch AttackRandomly;
    private Switch UseCustomColors;
    private Switch ShowShadow;
    private EditText GooseDefaultWhite;
    private EditText GooseDefaultOrange;
    private EditText GooseDefaultOutline;
    private EditText MinWanderingTimeSeconds;
    private EditText MaxWanderingTimeSeconds;
    private EditText FirstWanderTimeSeconds;
    private EditText DrawSize;
    private MaterialButton SaveConfig;
    private MaterialButton Update;
    // MobileBot Phase 1
    private MaterialButton BtnLlmHost;
    private MaterialButton BtnTalk;

    // Pet mode UI elements
    private Switch PetModeSwitch;
    private Switch TouchableSwitch;
    private ProgressBar HungerBar;
    private ProgressBar EnergyBar;
    private ProgressBar HappinessBar;
    private TextView PetStatusText;
    private MaterialButton FeedButton;
    private MaterialButton PlayButton;
    private MaterialButton SleepButton;
    private MaterialButton CustomizeButton;
    private Handler petStatusHandler;

    private String ConfigFilePath = "";
    private PermissionRequest permissionRequest;
    private WindowManager wm1;
    private WindowManager.LayoutParams wmlay1;
    private GooseView gooseView;
    private boolean isTouchable = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        DefToolBar = findViewById(R.id.DefToolBar);
        GooseDroid = findViewById(R.id.GooseDroid);
        EnableMods = findViewById(R.id.EnableMods);
        SilenceSounds = findViewById(R.id.SilenceSounds);
        Task_CanAttackMouse = findViewById(R.id.TaskCanAttackMouse);
        AttackRandomly = findViewById(R.id.AttackRandomly);
        UseCustomColors = findViewById(R.id.UseCustomColors);
        ShowShadow = findViewById(R.id.ShowShadow);
        GooseDefaultWhite = findViewById(R.id.GooseDefaultWhite);
        GooseDefaultOrange = findViewById(R.id.GooseDefaultOrange);
        GooseDefaultOutline = findViewById(R.id.GooseDefaultOutline);
        MinWanderingTimeSeconds = findViewById(R.id.MinWanderingTimeSeconds);
        MaxWanderingTimeSeconds = findViewById(R.id.MaxWanderingTimeSeconds);
        FirstWanderTimeSeconds = findViewById(R.id.FirstWanderTimeSeconds);
        DrawSize = findViewById(R.id.DrawSize);
        SaveConfig = findViewById(R.id.SaveConfig);
        Update = findViewById(R.id.Update);

        permissionRequest = new PermissionRequest(this);
        loadConfigFile();

        // Initialize notification manager
        PetNotificationManager.init(this);

        // Set up toolbar
        this.setSupportActionBar(DefToolBar);

        GooseDroid.setOnCheckedChangeListener(new Switch.OnCheckedChangeListener() {
            @SuppressLint("RtlHardcoded")
            @Override
            public void onCheckedChanged(CompoundButton cb, boolean isEnabled) {
                try {
                    if (isEnabled) {
                        if (!permissionRequest.hasOverlayPermission()) {
                            GooseDroid.setChecked(false);
                            Utils.showToast(MainActivity.this, getText(R.string.PleaseEnableFloatingWindowPermission));
                            permissionRequest.requestOverlayPermission();
                        } else {
                            ConfigureActivity ca = new ConfigureActivity(MainActivity.this);
                            ca.readFromSD(ConfigFilePath);
                            // Apply DrawSize from config
                            try {
                                String drawSizeStr = ca.getIniKey("DrawSize");
                                if (drawSizeStr != null) {
                                    GooseView.DrawSize = Float.parseFloat(drawSizeStr);
                                }
                            } catch (Exception ignored) {}
                            gooseView = new GooseView(MainActivity.this, ca);
                            wm1 = (WindowManager) getApplicationContext().getSystemService(Context.WINDOW_SERVICE);
                            wmlay1 = new WindowManager.LayoutParams();
                            // Set window type based on SDK version
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                wmlay1.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
                            } else {
                                wmlay1.type = WindowManager.LayoutParams.TYPE_PHONE;
                            }
                            wmlay1.format = PixelFormat.RGBA_8888; // Transparent background
                            wmlay1.gravity = Gravity.RIGHT | Gravity.TOP;
                            // Use FLAG_NOT_TOUCH_MODAL to allow touches to pass through
                            // The GooseView will decide which touches to handle (near goose) or pass through
                            if (isTouchable) {
                                wmlay1.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;
                            } else {
                                wmlay1.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                                        | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
                            }
                            wmlay1.x = 0;
                            wmlay1.y = 0;
                            wmlay1.width = Utils.getScreenWidth(MainActivity.this);
                            wmlay1.height = Utils.getScreenHeight(MainActivity.this);
                            wm1.addView(gooseView, wmlay1);
                            Utils.showToast(MainActivity.this, getText(R.string.GooseDroidEnable));
                        }
                    } else {
                        wm1.removeView(gooseView);
                        gooseView = null;
                        Utils.showToast(MainActivity.this, getText(R.string.GooseDroidDisable));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    showErrorAlert(e);
                }
            }
        });

        ShowShadow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                TheGoose.setShowShadow(ShowShadow.isChecked());
            }
        });

        // ===== MobileBot Phase 1: LLM brain wiring =====
        BtnLlmHost = findViewById(R.id.BtnLlmHost);
        BtnTalk = findViewById(R.id.BtnTalk);

        // ตั้งค่า host ของ llama-server (dev mode) เช่น http://192.168.1.5:8080
        final SharedPreferences mbPrefs = getSharedPreferences("mobilebot", MODE_PRIVATE);
        BtnLlmHost.setOnClickListener(v -> {
            EditText input = new EditText(this);
            input.setHint("http://192.168.1.x:8080");
            input.setText(mbPrefs.getString("llm_host", ""));
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.LlmHost)
                    .setView(input)
                    .setPositiveButton("บันทึก", (d, w) -> {
                        String host = input.getText().toString().trim();
                        mbPrefs.edit().putString("llm_host", host).apply();
                        if (!host.isEmpty()) {
                            PetBrain.configure(new HttpLlmBackend(host));
                            Utils.showToast(this, "LLM backend: " + host);
                        }
                    })
                    .setNegativeButton("ยกเลิก", null)
                    .show();
        });

        // กล่องพิมพ์สั่งน้องหมีภาษาไทย
        BtnTalk.setOnClickListener(v -> {
            if (!PetBrain.isReady()) {
                Utils.showToast(this, "LLM ยังไม่พร้อม — ตั้งค่า server ก่อน");
                return;
            }
            final EditText input = new EditText(this);
            input.setHint("เช่น แม่จะกลับบ้านเก็บ / เปิด YouTube");
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.TalkToPet)
                    .setView(input)
                    .setPositiveButton("ส่ง", (d, w) -> {
                        String text = input.getText().toString().trim();
                        if (!text.isEmpty()) {
                            PetBrain.act(text, action -> {
                                String msg;
                                if (action == null) msg = "(ไม่ตอบ)";
                                else if ("chat".equals(action.action)) msg = action.reply;
                                else if ("open_app".equals(action.action)) msg = "open_app → " + action.pkg;
                                else msg = action.action + " " + action.x + "," + action.y;
                                Utils.showToast(MainActivity.this, msg);
                            });
                        }
                    })
                    .setNegativeButton("ยกเลิก", null)
                    .show();
        });

        // auto-restore backend จาก prefs ตอนเปิดแอป
        String savedHost = mbPrefs.getString("llm_host", "");
        if (!savedHost.isEmpty()) PetBrain.configure(new HttpLlmBackend(savedHost));
        // ===== end MobileBot Phase 1 =====

        DrawSize.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                try {
                    float size = Float.parseFloat(DrawSize.getText().toString());
                    // Validate input range to prevent rendering issues
                    if (size < 0.1f) {
                        size = 0.1f;
                        DrawSize.setText(String.valueOf(size));
                        Utils.showToast(MainActivity.this, getText(R.string.DrawSizeTooSmall));
                    } else if (size > 10.0f) {
                        size = 10.0f;
                        DrawSize.setText(String.valueOf(size));
                        Utils.showToast(MainActivity.this, getText(R.string.DrawSizeTooLarge));
                    }
                    GooseView.DrawSize = size;
                } catch (NumberFormatException e) {
                    // Invalid number format - reset to default
                    GooseView.DrawSize = 1.0f;
                    DrawSize.setText("1.0");
                    Utils.showToast(MainActivity.this, getText(R.string.InvalidDrawSize));
                } catch (Exception e) {
                    e.printStackTrace();
                    showErrorAlert(e);
                }
                return false;
            }
        });

        SaveConfig.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                saveConfigFile();
                Utils.showToast(view.getContext(), getText(R.string.SaveSuccessfully));
            }
        });

        Update.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                updateConfigWithColor();
            }
        });

        // Initialize pet mode UI elements
        initPetModeUI();
    }

    /**
     * Initialize pet mode UI elements and listeners.
     */
    private void initPetModeUI() {
        PetModeSwitch = findViewById(R.id.PetModeSwitch);
        TouchableSwitch = findViewById(R.id.TouchableSwitch);
        HungerBar = findViewById(R.id.HungerBar);
        EnergyBar = findViewById(R.id.EnergyBar);
        HappinessBar = findViewById(R.id.HappinessBar);
        PetStatusText = findViewById(R.id.PetStatusText);
        FeedButton = findViewById(R.id.FeedButton);
        PlayButton = findViewById(R.id.PlayButton);
        SleepButton = findViewById(R.id.SleepButton);

        // Set up pet status update handler
        petStatusHandler = new Handler(Looper.getMainLooper());

        // Pet mode switch listener
        if (PetModeSwitch != null) {
            PetModeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                TheGoose.petModeEnabled = isChecked;
                updatePetModeUIVisibility(isChecked);
                savePetState();
            });
        }

        // Touchable switch listener
        if (TouchableSwitch != null) {
            TouchableSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                isTouchable = isChecked;
                updateWindowTouchability();
            });
        }

        // Feed button
        if (FeedButton != null) {
            FeedButton.setOnClickListener(v -> {
                TheGoose.startEating();
                Utils.showToast(this, getText(R.string.FeedingPet));
            });
        }

        // Play button
        if (PlayButton != null) {
            PlayButton.setOnClickListener(v -> {
                if (PetNeeds.get().energy > 20) {
                    TheGoose.startPlaying();
                    Utils.showToast(this, getText(R.string.PlayingWithPet));
                } else {
                    Utils.showToast(this, getText(R.string.PetTooTired));
                }
            });
        }

        // Sleep button
        if (SleepButton != null) {
            SleepButton.setOnClickListener(v -> {
                TheGoose.startSleeping();
                Utils.showToast(this, getText(R.string.PetSleeping));
            });
        }

        // Customize button
        CustomizeButton = findViewById(R.id.CustomizeButton);
        if (CustomizeButton != null) {
            CustomizeButton.setOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, CustomizeActivity.class);
                startActivity(intent);
            });
        }

        // Load saved pet state
        loadPetState();

        // Start periodic UI updates
        startPetStatusUpdates();
    }

    /**
     * Update pet status UI visibility based on pet mode.
     */
    private void updatePetModeUIVisibility(boolean visible) {
        int visibility = visible ? View.VISIBLE : View.GONE;
        if (HungerBar != null) HungerBar.setVisibility(visibility);
        if (EnergyBar != null) EnergyBar.setVisibility(visibility);
        if (HappinessBar != null) HappinessBar.setVisibility(visibility);
        if (PetStatusText != null) PetStatusText.setVisibility(visibility);
        if (FeedButton != null) FeedButton.setVisibility(visibility);
        if (PlayButton != null) PlayButton.setVisibility(visibility);
        if (SleepButton != null) SleepButton.setVisibility(visibility);
        if (CustomizeButton != null) CustomizeButton.setVisibility(visibility);
        if (TouchableSwitch != null) TouchableSwitch.setVisibility(visibility);

        // Also show/hide labels
        View hungerLabel = findViewById(R.id.HungerLabel);
        View energyLabel = findViewById(R.id.EnergyLabel);
        View happinessLabel = findViewById(R.id.HappinessLabel);
        if (hungerLabel != null) hungerLabel.setVisibility(visibility);
        if (energyLabel != null) energyLabel.setVisibility(visibility);
        if (happinessLabel != null) happinessLabel.setVisibility(visibility);
    }

    /**
     * Update window touchability at runtime.
     */
    private void updateWindowTouchability() {
        if (wm1 != null && gooseView != null && wmlay1 != null) {
            if (isTouchable) {
                wmlay1.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            } else {
                wmlay1.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            }
            wm1.updateViewLayout(gooseView, wmlay1);
        }
    }

    /**
     * Start periodic updates of pet status UI.
     */
    private void startPetStatusUpdates() {
        Runnable statusUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                updatePetStatusUI();
                petStatusHandler.postDelayed(this, 1000); // Update every second
            }
        };
        petStatusHandler.postDelayed(statusUpdateRunnable, 1000);
    }

    /**
     * Update pet status bars and text.
     */
    private void updatePetStatusUI() {
        if (!TheGoose.petModeEnabled) return;

        if (HungerBar != null) {
            // Invert hunger for display (100 = full, 0 = starving)
            HungerBar.setProgress((int) (100 - PetNeeds.get().hunger));
        }
        if (EnergyBar != null) {
            EnergyBar.setProgress((int) PetNeeds.get().energy);
        }
        if (HappinessBar != null) {
            HappinessBar.setProgress((int) PetNeeds.get().happiness);
        }
        if (PetStatusText != null) {
            String status = PetPersonality.get().getTitle() + " - " + PetNeeds.get().getMoodStateString();
            PetStatusText.setText(status);
        }
    }

    /**
     * Save pet state to config file.
     */
    private void savePetState() {
        try {
            ConfigureActivity ca = new ConfigureActivity(this);
            Properties prop = new Properties();

            // Existing config
            prop.put("EnableMods", capitalizeFirst(String.valueOf(EnableMods.isChecked())));
            prop.put("SilenceSounds", capitalizeFirst(String.valueOf(SilenceSounds.isChecked())));
            prop.put("Task_CanAttackMouse", capitalizeFirst(String.valueOf(Task_CanAttackMouse.isChecked())));
            prop.put("AttackRandomly", capitalizeFirst(String.valueOf(AttackRandomly.isChecked())));
            prop.put("UseCustomColors", capitalizeFirst(String.valueOf(UseCustomColors.isChecked())));
            prop.put("ShowShadow", capitalizeFirst(String.valueOf(ShowShadow.isChecked())));
            prop.put("GooseDefaultWhite", GooseDefaultWhite.getText().toString());
            prop.put("GooseDefaultOrange", GooseDefaultOrange.getText().toString());
            prop.put("GooseDefaultOutline", GooseDefaultOutline.getText().toString());
            prop.put("MinWanderingTimeSeconds", MinWanderingTimeSeconds.getText().toString());
            prop.put("MaxWanderingTimeSeconds", MaxWanderingTimeSeconds.getText().toString());
            prop.put("FirstWanderTimeSeconds", FirstWanderTimeSeconds.getText().toString());
            prop.put("DrawSize", DrawSize.getText().toString());

            // Pet mode config
            prop.put("PetModeEnabled", capitalizeFirst(String.valueOf(PetModeSwitch != null && PetModeSwitch.isChecked())));
            prop.put("TouchableEnabled", capitalizeFirst(String.valueOf(isTouchable)));

            // Pet needs
            prop.put("PetHunger", String.valueOf(PetNeeds.get().hunger));
            prop.put("PetEnergy", String.valueOf(PetNeeds.get().energy));
            prop.put("PetHappiness", String.valueOf(PetNeeds.get().happiness));
            prop.put("LastPlayedTimestamp", String.valueOf(System.currentTimeMillis()));

            // Pet personality
            prop.put("PersonalityPlayfulness", String.valueOf(PetPersonality.get().playfulness));
            prop.put("PersonalityAffection", String.valueOf(PetPersonality.get().affection));
            prop.put("PersonalityBravery", String.valueOf(PetPersonality.get().bravery));
            prop.put("PersonalityMischief", String.valueOf(PetPersonality.get().mischief));
            prop.put("TotalPets", String.valueOf(PetPersonality.get().getTotalPets()));
            prop.put("TotalPlays", String.valueOf(PetPersonality.get().getTotalPlays()));
            prop.put("TotalFeeds", String.valueOf(PetPersonality.get().getTotalFeeds()));

            // Pet appearance
            prop.put("PetBodyColor", PetAppearance.get().colorToHex(PetAppearance.get().bodyColor));
            prop.put("PetAccentColor", PetAppearance.get().colorToHex(PetAppearance.get().accentColor));
            prop.put("PetOutlineColor", PetAppearance.get().colorToHex(PetAppearance.get().outlineColor));
            prop.put("PetEyeColor", PetAppearance.get().colorToHex(PetAppearance.get().eyeColor));
            prop.put("PetHatId", String.valueOf(PetAppearance.get().hatId));
            prop.put("PetAccessoryId", String.valueOf(PetAppearance.get().accessoryId));
            prop.put("PetCreatureType", String.valueOf(PetAppearance.get().creatureType));
            prop.put("PetName", PetAppearance.get().petName);

            ca.saveFiletoSD(ConfigFilePath, prop);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Load pet state from config file.
     */
    private void loadPetState() {
        try {
            ConfigureActivity ca = new ConfigureActivity(this);
            ca.readFromSD(ConfigFilePath);

            // Load pet mode setting
            String petModeStr = ca.getIniKey("PetModeEnabled");
            boolean petModeEnabled = petModeStr != null && string2boolean(petModeStr);
            if (PetModeSwitch != null) {
                PetModeSwitch.setChecked(petModeEnabled);
            }
            TheGoose.petModeEnabled = petModeEnabled;
            updatePetModeUIVisibility(petModeEnabled);

            // Load touchable setting
            String touchableStr = ca.getIniKey("TouchableEnabled");
            isTouchable = touchableStr == null || string2boolean(touchableStr);
            if (TouchableSwitch != null) {
                TouchableSwitch.setChecked(isTouchable);
            }

            // Load pet needs
            float savedHunger = parseFloatSafe(ca.getIniKey("PetHunger"), 50f);
            float savedEnergy = parseFloatSafe(ca.getIniKey("PetEnergy"), 100f);
            float savedHappiness = parseFloatSafe(ca.getIniKey("PetHappiness"), 75f);
            long savedTimestamp = parseLongSafe(ca.getIniKey("LastPlayedTimestamp"), 0);
            PetNeeds.get().loadState(savedHunger, savedEnergy, savedHappiness, savedTimestamp);

            // Load pet personality
            float playfulness = parseFloatSafe(ca.getIniKey("PersonalityPlayfulness"), 0f);
            float affection = parseFloatSafe(ca.getIniKey("PersonalityAffection"), 0f);
            float bravery = parseFloatSafe(ca.getIniKey("PersonalityBravery"), 0f);
            float mischief = parseFloatSafe(ca.getIniKey("PersonalityMischief"), 50f);
            int totalPets = parseIntSafe(ca.getIniKey("TotalPets"), 0);
            int totalPlays = parseIntSafe(ca.getIniKey("TotalPlays"), 0);
            int totalFeeds = parseIntSafe(ca.getIniKey("TotalFeeds"), 0);
            long lastInteraction = parseLongSafe(ca.getIniKey("LastPlayedTimestamp"), System.currentTimeMillis());
            PetPersonality.get().loadState(playfulness, affection, bravery, mischief,
                    totalPets, totalPlays, totalFeeds, lastInteraction);

            // Load pet appearance
            int bodyColor = PetAppearance.get().hexToColor(ca.getIniKey("PetBodyColor"));
            int accentColor = PetAppearance.get().hexToColor(ca.getIniKey("PetAccentColor"));
            int outlineColor = PetAppearance.get().hexToColor(ca.getIniKey("PetOutlineColor"));
            int eyeColor = PetAppearance.get().hexToColor(ca.getIniKey("PetEyeColor"));
            int hatId = parseIntSafe(ca.getIniKey("PetHatId"), 0);
            int accessoryId = parseIntSafe(ca.getIniKey("PetAccessoryId"), 0);
            int creatureType = parseIntSafe(ca.getIniKey("PetCreatureType"), 0);
            String petName = ca.getIniKey("PetName");
            if (petName == null) petName = "Goose";

            // Only load appearance if pet mode is enabled
            if (petModeEnabled) {
                PetAppearance.get().loadState(bodyColor, accentColor, outlineColor, eyeColor,
                        hatId, accessoryId, creatureType, petName);
            }

        } catch (Exception e) {
            e.printStackTrace();
            // Use defaults on error
            PetNeeds.get().reset();
            PetPersonality.get().reset();
            PetAppearance.get().reset();
        }
    }

    private float parseFloatSafe(String value, float defaultValue) {
        if (value == null || value.isEmpty()) return defaultValue;
        try {
            return Float.parseFloat(value);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private int parseIntSafe(String value, int defaultValue) {
        if (value == null || value.isEmpty()) return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private long parseLongSafe(String value, long defaultValue) {
        if (value == null || value.isEmpty()) return defaultValue;
        try {
            return Long.parseLong(value);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private void showErrorAlert(Exception e) {
        Utils.showDialog(MainActivity.this, MainActivity.class.getName() + " - Error", e.toString());
    }

    private void loadConfigFile() {
        try {
            ConfigFilePath = Utils.getPrivateDir(this) + "/config.ini";

            if (!Utils.fileExists(ConfigFilePath)) {
                Utils.copyAssetFile(this, "config.ini", ConfigFilePath);
            }
            ConfigureActivity ca = new ConfigureActivity(MainActivity.this);
            ca.readFromSD(ConfigFilePath);
            EnableMods.setChecked(string2boolean(ca.getIniKey("EnableMods")));
            SilenceSounds.setChecked(string2boolean(ca.getIniKey("SilenceSounds")));
            Task_CanAttackMouse.setChecked(string2boolean(ca.getIniKey("Task_CanAttackMouse")));
            AttackRandomly.setChecked(string2boolean(ca.getIniKey("AttackRandomly")));
            UseCustomColors.setChecked(string2boolean(ca.getIniKey("UseCustomColors")));
            ShowShadow.setChecked(string2boolean(ca.getIniKey("ShowShadow")));
            TheGoose.setShowShadow(ShowShadow.isChecked());
            // Set EditText contents
            setEditTextContent(GooseDefaultWhite, ca.getIniKey("GooseDefaultWhite"));
            setEditTextContent(GooseDefaultOrange, ca.getIniKey("GooseDefaultOrange"));
            setEditTextContent(GooseDefaultOutline, ca.getIniKey("GooseDefaultOutline"));
            setEditTextContent(MinWanderingTimeSeconds, ca.getIniKey("MinWanderingTimeSeconds"));
            setEditTextContent(MaxWanderingTimeSeconds, ca.getIniKey("MaxWanderingTimeSeconds"));
            setEditTextContent(FirstWanderTimeSeconds, ca.getIniKey("FirstWanderTimeSeconds"));
            setEditTextContent(DrawSize, ca.getIniKey("DrawSize"));

            updateConfigWithColor();

            Utils.showToast(MainActivity.this, "Configuration loaded successfully.");
        } catch (Exception e) {
            e.printStackTrace();
            showErrorAlert(e);
        }
    }

    public void updateConfigWithColor() {
        if (UseCustomColors.isChecked()) {
            TheGoose.BodyColor = Utils.parseColor(GooseDefaultWhite.getText().toString());
            TheGoose.FootColor = Utils.parseColor(GooseDefaultOrange.getText().toString());
            TheGoose.MouthColor = Utils.parseColor(GooseDefaultOrange.getText().toString());
            TheGoose.OutLineColor = Utils.parseColor(GooseDefaultOutline.getText().toString());
        }
    }

    public static boolean string2boolean(String str) {
        return Boolean.parseBoolean(str.toLowerCase());
    }

    private void setEditTextContent(EditText editText, String content) {
        if (content != null) {
            editText.setText(content.toCharArray(), 0, content.length());
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Pause goose rendering to save battery
        if (gooseView != null) {
            gooseView.pauseRendering();
        }

        // Save pet state when app is paused
        if (TheGoose.petModeEnabled) {
            savePetState();
            // Start notification checks when app goes to background
            PetNotificationManager.startPeriodicCheck();
            // Schedule a reminder for 4 hours later
            PetNotificationManager.scheduleReminder(4 * 60 * 60 * 1000);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Resume goose rendering
        if (gooseView != null) {
            gooseView.resumeRendering();
        }

        // Stop notification checks when app is in foreground
        PetNotificationManager.stopPeriodicCheck();
        PetNotificationManager.cancelScheduledReminder();
        PetNotificationManager.cancelAllNotifications();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Stop status updates
        if (petStatusHandler != null) {
            petStatusHandler.removeCallbacksAndMessages(null);
            petStatusHandler = null;
        }

        // Save final state
        if (TheGoose.petModeEnabled) {
            savePetState();
        }

        // Clean up goose view properly
        if (gooseView != null) {
            gooseView.cleanup();
            if (GooseDroid.isChecked() && wm1 != null) {
                try {
                    wm1.removeView(gooseView);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            gooseView = null;
        }

        // Clean up window manager reference
        wm1 = null;
        wmlay1 = null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.clear();
        menu.add(1, 1, 1, getText(R.string.ConfigFilePath));
        menu.add(1, 2, 2, getText(R.string.ResetDefaultConfig));
        menu.add(1, 3, 3, getText(R.string.SaveConfig));
        menu.add(1, 4, 4, getText(R.string.About));
        return true;
    }

    private void restartActivity() {
        Intent intent = getIntent();
        finish();
        startActivity(intent);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case 1:
                showMessageDialog("Config File Path", ConfigFilePath);
                break;
            case 2:
                if (Utils.fileExists(ConfigFilePath)) {
                    Utils.deleteFile(ConfigFilePath);
                }
                Utils.copyAssetFile(this, "config.ini", ConfigFilePath);
                Utils.showToast(this, getText(R.string.ResetSuccessfully));
                restartActivity();
                break;
            case 3:
                saveConfigFile();
                Utils.showToast(this, getText(R.string.SaveSuccessfully));
                break;
            case 4:
                String about = "by:\n" +
                        "1.caofangkuai\n" +
                        " YouTube:@caofangkuai\n" +
                        " BiliBili:space.bilibili.com/3546724471671510\n" +
                        "2.CookieBox\n" +
                        " Youtube:@CookieBoxCHN\n" +
                        " BiliBili:space.bilibili.com/648318676\n\n" +
                        "Thank you for liking this app!";
                MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                        .setTitle(getText(R.string.About))
                        .setMessage(about)
                        .setPositiveButton(getText(R.string.Confirm), (dialog, which) -> {
                            // OK button click
                        })
                        .setNegativeButton(R.string.Copy, (dialog, which) -> {
                            // Copy button click
                            Utils.copyToClipboard(this, about);
                        });
                builder.show();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    public void showMessageDialog(String title, String message) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(getText(R.string.Confirm), (dialog, which) -> {
                    // OK button click
                })
                .setNegativeButton(R.string.Cancel, (dialog, which) -> {
                    // Cancel button click
                });
        builder.show();
    }

    public void saveConfigFile() {
        try {
            ConfigureActivity ca = new ConfigureActivity(this);
            Properties prop = new Properties();
            prop.put("EnableMods", capitalizeFirst(String.valueOf(EnableMods.isChecked())));
            prop.put("SilenceSounds", capitalizeFirst(String.valueOf(SilenceSounds.isChecked())));
            prop.put("Task_CanAttackMouse", capitalizeFirst(String.valueOf(Task_CanAttackMouse.isChecked())));
            prop.put("AttackRandomly", capitalizeFirst(String.valueOf(AttackRandomly.isChecked())));
            prop.put("UseCustomColors", capitalizeFirst(String.valueOf(UseCustomColors.isChecked())));
            prop.put("ShowShadow", capitalizeFirst(String.valueOf(ShowShadow.isChecked())));
            prop.put("GooseDefaultWhite", GooseDefaultWhite.getText().toString());
            prop.put("GooseDefaultOrange", GooseDefaultOrange.getText().toString());
            prop.put("GooseDefaultOutline", GooseDefaultOutline.getText().toString());
            prop.put("MinWanderingTimeSeconds", MinWanderingTimeSeconds.getText().toString());
            prop.put("MaxWanderingTimeSeconds", MaxWanderingTimeSeconds.getText().toString());
            prop.put("FirstWanderTimeSeconds", FirstWanderTimeSeconds.getText().toString());
            prop.put("DrawSize", DrawSize.getText().toString());
            ca.saveFiletoSD(ConfigFilePath, prop);
        } catch (Exception e) {
            e.printStackTrace();
            showErrorAlert(e);
        }
    }

    private String capitalizeFirst(String name) {
        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }
}
