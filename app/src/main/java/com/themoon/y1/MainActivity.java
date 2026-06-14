package com.themoon.y1;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.audiofx.Equalizer;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.os.Vibrator;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;

public class MainActivity extends Activity {
    // 💡 [추가] 퀵 스크롤 (알파벳 인덱스) 관련 변수들
    private TextView tvFastScrollLetter;
    private Handler fastScrollHandler = new Handler();
    private Runnable hideFastScrollTask = new Runnable() {
        @Override
        public void run() {
            if (tvFastScrollLetter != null) {
                tvFastScrollLetter.setVisibility(View.GONE);
            }
        }
    };
    public static MainActivity instance;
    private long lastTrackChangeTime = 0; // 🚀 기기의 중복 키 신호를 막아줄 방어막 변수
    // 💡 [추가] 오디오 스펙트럼 관련 변수들
    private android.media.audiofx.Visualizer audioVisualizer;
    private AudioVisualizerView visualizerView;
    private boolean isVisualizerShowing = false;
    private int currentAlbumColor = 0xFFFFFFFF; // 스펙트럼 바의 색상
    private static final int STATE_MENU = 1;
    private static final int STATE_BROWSER = 2;
    private static final int STATE_PLAYER = 3;
    private static final int STATE_SETTINGS = 4;
    private static final int STATE_BLUETOOTH = 5;
    private static final int STATE_WIFI = 6;
    private static final int STATE_WIFI_KEYBOARD = 7;
    private static final int STATE_BRIGHTNESS = 8;
    private static final int STATE_STORAGE = 9;
    private static final int STATE_WEBSERVER = 10;
    // 💡 미디어 라이브러리 브라우저 상태 관리 변수들
    private static final int BROWSER_ROOT = 0;
    private static final int BROWSER_FOLDER = 1;
    private static final int BROWSER_ARTISTS = 2;
    private static final int BROWSER_ALBUMS = 3;
    private static final int BROWSER_VIRTUAL_SONGS = 4;
    // 💡 [추가] 손상되어 앱을 터뜨린 '독약 파일'들을 기억하는 블랙리스트
    private java.util.Set<String> blacklist = new java.util.HashSet<>();
    private int currentBrowserMode = BROWSER_ROOT;
    private String virtualQueryType = "";
    private String virtualQueryValue = "";
    private List<File> virtualSongList = new ArrayList<>();
    // 💡 백그라운드 미디어 제어권(스크린 오프) 변수
    private MediaSession mediaSession;
    // 💡 [추가] OS 스캐너를 대체할 '자체 미디어 라이브러리 엔진' 변수들
    private static class SongItem {
        File file;
        String title;
        String artist;
        String album;

        public SongItem(File f, String t, String a, String al) {
            file = f;
            title = t;
            artist = a;
            album = al;
        }
    }
    private boolean isScreenOffControlEnabled = false;
    private boolean isAutoFetchEnabled = true; // 🚀 [추가] 인터넷 자동 검색 스위치 기본값
    private List<SongItem> customLibrary = new ArrayList<>();
    private boolean isCustomScanning = false;
    private int currentScreenState = STATE_MENU;
    // 💡 자체 날짜/시간 설정용 임시 변수
    private int dtYear = 2026, dtMonth = 1, dtDay = 1, dtHour = 12, dtMinute = 0;
    private View layoutMainMenu, layoutBrowserMode, layoutSettingsMode;
    private View layoutBluetoothMode, layoutWifiMode, layoutWifiKeyboard;
    private View layoutPlayerMode, layoutVolumeOverlay;
    private View layoutBrightnessMode, layoutStorageMode, layoutWebServerMode;

    private LinearLayout containerBrowserItems, containerSettingsItems;
    private LinearLayout containerBtItems, containerWifiItems;

    private TextView tvStatusClock, tvStatusBattery;
    private ImageView ivStatusBluetooth, ivStatusWifi, ivStatusHeadphone, ivMainBg;

    private TextView tvBrowserPath, tvPlayerTitle, tvPlayerArtist, tvPlayerTimeCurrent, tvPlayerTimeTotal;
    private TextView tvPlayerTrackCount;
    private ImageView ivPlayerShuffleStatus, ivPlayerRepeatStatus; // 💡 텍스트뷰에서 이미지뷰로 변경!
    private ProgressBar playerProgress, volumeProgress, pbBrightness, pbStorage;
    private TextView tvBrightnessVal, tvStorageDetails;

    private TextView tvServerStatus, tvServerIp;
    private Button btnServerToggle;

    private ImageView ivMenuPreview, ivAlbumArt, ivPlayerBgBlur, ivPauseOverlay;

    private Button btnNowPlaying, btnPlay, btnSettings, btnBluetooth, btnRadio;
    private Button btnScanBt, btnScanWifi;

    private TextView tvKeyboardSsid, tvKeyboardInput;
    private TextView tvKeyPprev, tvKeyPrev, tvKeyCurrent, tvKeyNext, tvKeyNnext;

    private final String[] KEYBOARD_CHARS = {
            "a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m", "n", "o", "p", "q", "r", "s", "t", "u",
            "v", "w", "x", "y", "z",
            "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T", "U",
            "V", "W", "X", "Y", "Z",
            "0", "1", "2", "3", "4", "5", "6", "7", "8", "9",
            "!", "@", "#", "$", "%", "^", "&", "*", "-", "_", "+", "=", ".", "?",
            "[DEL]", "[CONN]"
    };
    private int keyboardIndex = 0;
    private String targetWifiSsid = "";
    private String typedPassword = "";
    private boolean isTargetWifiOpen = false;
    // 💡 미디어 스캐너가 현재 작업 중인지 추적하는 변수
    private boolean isMediaScanning = false;
    private MediaPlayer mediaPlayer;
    private AudioManager audioManager;
    private File rootFolder = new File("/storage/sdcard0/Music");
    private File currentFolder = rootFolder;
    private List<File> originalPlaylist = new ArrayList<File>();
    private List<File> currentPlaylist = new ArrayList<File>();
    private int currentIndex = 0;
    private boolean isPausedByHand = true;

    private java.io.FileInputStream currentFileInputStream = null;
    private TextView tvMenuPreviewTitle, tvMenuPreviewArtist;
    private SharedPreferences prefs;
    private boolean isShuffleMode = false;
    private int repeatMode = 0; // 0: OFF, 1: ONE (Repeat One), 2: ALL (Repeat Folder/All)
    private boolean isSoundEffectEnabled = true;
    private boolean isVibrationEnabled = true;
    private boolean isPickingBackground = false;

    // 💡 마지막으로 재생된 앨범 아트를 기억하는 변수
    private byte[] lastAlbumArtBytes = null;
    // 💡 이퀄라이저 관련 변수 추가
    private Equalizer equalizer;
    private List<String> eqPresetNames = new ArrayList<String>();
    private int currentEqPresetIndex = 0;

    private int lastSettingsFocusIndex = 1;

    private boolean isScreenSleeping = false;
    private long lastScreenOnTime = 0;

    private int currentTimeoutIndex = 1;
    private final int[] TIMEOUT_VALUES = { 15000, 30000, 60000, 300000 };
    private final String[] TIMEOUT_NAMES = { "15 Sec", "30 Sec", "1 Min", "5 Min" };

    private int currentSystemBrightness = 255;
    private Random random = new Random();

    private List<String> foundBtDevices = new ArrayList<String>();
    private List<String> foundWifiNetworks = new ArrayList<String>();

    private Y1WebServer webServer;
    private boolean isServerRunning = false;

    private Handler clockHandler = new Handler();
    private Runnable clockTask = new Runnable() {
        @Override
        public void run() {
            SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a", Locale.US);
            tvStatusClock.setText(sdf.format(new Date()));
            clockHandler.postDelayed(this, 1000);
        }
    };

    private Handler progressHandler = new Handler();
    private Runnable updateProgressTask = new Runnable() {
        @Override
        public void run() {
            try {
                if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                    int current = mediaPlayer.getCurrentPosition();
                    int duration = mediaPlayer.getDuration();
                    int progress = (int) (((float) current / duration) * 100);
                    playerProgress.setProgress(progress);
                    tvPlayerTimeCurrent.setText(formatTime(current));
                    tvPlayerTimeTotal.setText(formatTime(duration));
                }
            } catch (Exception e) {
            }
            progressHandler.postDelayed(this, 500);
        }
    };

    private Handler volumeHandler = new Handler();
    private Runnable hideVolumeTask = new Runnable() {
        @Override
        public void run() {
            layoutVolumeOverlay.setVisibility(View.GONE);
        }
    };

    private BroadcastReceiver systemStatusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                isScreenSleeping = true;
            } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                isScreenSleeping = false;
                lastScreenOnTime = System.currentTimeMillis();
            } else if (Intent.ACTION_BATTERY_CHANGED.equals(action)) {
                int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                int batteryPct = (int) ((level / (float) scale) * 100);
                tvStatusBattery.setText(batteryPct + "%");
            } else if (Intent.ACTION_HEADSET_PLUG.equals(action)) {
                int state = intent.getIntExtra("state", -1);
                if (state == 1) {
                    ivStatusHeadphone.setVisibility(View.VISIBLE);
                    ivStatusHeadphone.setColorFilter(0xFF00FFFF);
                } else {
                    ivStatusHeadphone.setVisibility(View.GONE);
                }
            } else if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                if (state == BluetoothAdapter.STATE_ON) {
                    ivStatusBluetooth.setVisibility(View.VISIBLE);
                    ivStatusBluetooth.setColorFilter(0xFF5555FF);
                } else {
                    ivStatusBluetooth.setVisibility(View.GONE);
                }
                if (currentScreenState == STATE_SETTINGS)
                    buildSettingsUI();
                else if (currentScreenState == STATE_BLUETOOTH)
                    startBluetoothScan();
            } else if (WifiManager.WIFI_STATE_CHANGED_ACTION.equals(action)) {
                int state = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN);
                if (state == WifiManager.WIFI_STATE_ENABLED) {
                    ivStatusWifi.setVisibility(View.VISIBLE);
                    ivStatusWifi.setColorFilter(0xFFFFBB00);
                } else {
                    ivStatusWifi.setVisibility(View.GONE);
                }
                if (currentScreenState == STATE_SETTINGS)
                    buildSettingsUI();
                else if (currentScreenState == STATE_WIFI)
                    startWifiScan();
            } else if (WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(action)) {
                NetworkInfo networkInfo = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                if (networkInfo != null && networkInfo.isConnected()) {
                    ivStatusWifi.setColorFilter(0xFF00FF00);
                    if (currentScreenState == STATE_WIFI)
                        startWifiScan();
                } else {
                    ivStatusWifi.setColorFilter(0xFFFFBB00);
                }
            } else if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                String deviceName = device.getName();
                String deviceAddress = device.getAddress();
                if (deviceName != null && !foundBtDevices.contains(deviceAddress)) {
                    foundBtDevices.add(deviceAddress);
                    // 💡 새로 발견된 낯선 기기는 isPaired = false 로 보냅니다.
                    addBluetoothItemToUI(deviceName, device, false);
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                btnScanBt.setText("Scan Complete (Retry)");
            } else if (WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(action)) {
                WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                if (wm != null) {
                    List<ScanResult> results = wm.getScanResults();
                    btnScanWifi.setText("Scan Complete (Retry)");
                    updateWifiUI(results);
                }
            }
            // 🚀 [여기에 추가!] 시스템 미디어 스캐너 감지 센서
            else if (Intent.ACTION_MEDIA_SCANNER_STARTED.equals(action)) {
                isMediaScanning = true;
            } else if (Intent.ACTION_MEDIA_SCANNER_FINISHED.equals(action)) {
                isMediaScanning = false;
                Toast.makeText(context, "Media Scan Complete! Library Updated.", Toast.LENGTH_SHORT).show();

                // 🚀 시스템 스캔이 끝났을 때 우리 자체 라이브러리도 다시 갱신해 줍니다!
                if (!isCustomScanning) {
                    isCustomScanning = true;
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            customLibrary.clear();
                            buildCustomLibrary(rootFolder);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    isCustomScanning = false;
                                    if (currentScreenState == STATE_BROWSER) {
                                        if (currentBrowserMode == BROWSER_ROOT) {
                                            buildFileBrowserUI();
                                        } else if (currentBrowserMode == BROWSER_ARTISTS) {
                                            buildVirtualCategories("ARTIST");
                                        } else if (currentBrowserMode == BROWSER_ALBUMS) {
                                            buildVirtualCategories("ALBUM");
                                        } else if (currentBrowserMode == BROWSER_VIRTUAL_SONGS) {
                                            buildVirtualSongs();
                                        }
                                    }
                                }
                            });
                        }
                    }).start();
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
// 🚀 앱이 켜지면 자기 자신을 변수에 등록합니다.
        instance = this;
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, Throwable e) {
                try {
                    java.io.StringWriter sw = new java.io.StringWriter();
                    java.io.PrintWriter pw = new java.io.PrintWriter(sw);
                    e.printStackTrace(pw);
                    java.io.File logFile = new java.io.File("/storage/sdcard0/y1_crash_log.txt");
                    java.io.FileOutputStream fos = new java.io.FileOutputStream(logFile, true);
                    fos.write(("\n\n--- 💥 CRASH REPORT (" + new java.util.Date().toString() + ") ---\n").getBytes());
                    fos.write(sw.toString().getBytes());
                    fos.close();
                } catch (Exception ex) {
                }
                System.exit(1);
            }
        });

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);

        // 🚀 [여기에 새로 추가!] 퀵 스크롤 알파벳을 띄워줄 플로팅 UI 생성
        android.view.ViewGroup root = findViewById(android.R.id.content);
        tvFastScrollLetter = new TextView(this);
        tvFastScrollLetter.setTextSize(50); // 글자 크기를 아주 큼직하게!
        tvFastScrollLetter.setGravity(android.view.Gravity.CENTER);
        tvFastScrollLetter.setVisibility(View.GONE);

        android.widget.FrameLayout.LayoutParams flp = new android.widget.FrameLayout.LayoutParams(
                (int)(80 * getResources().getDisplayMetrics().density), // 가로 80dp
                (int)(80 * getResources().getDisplayMetrics().density)  // 세로 80dp
        );
        flp.gravity = android.view.Gravity.CENTER_VERTICAL | android.view.Gravity.RIGHT; // 오른쪽 가운데 정렬
        flp.rightMargin = (int)(30 * getResources().getDisplayMetrics().density); // 오른쪽에서 30dp 띄움
        root.addView(tvFastScrollLetter, flp);

        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        // 🚀 [시스템 공식 등록] 화면이 꺼져도 버튼 신호를 받을 수 있도록 수신기를 장착합니다!
        ComponentName componentName = new ComponentName(getPackageName(), MediaBtnReceiver.class.getName());
        audioManager.registerMediaButtonEventReceiver(componentName);
// 🚀 [스크린 오프 완벽 제어 1단계] 시스템의 미디어/버튼 제어권을 앱이 뺏어옵니다!
        try {
            if (android.os.Build.VERSION.SDK_INT >= 21) {
                mediaSession = new MediaSession(this, "Y1_MEDIA_SESSION");
                mediaSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
                mediaSession.setCallback(new MediaSession.Callback() {
                    @Override
                    public boolean onMediaButtonEvent(Intent mediaButtonIntent) {
                        KeyEvent event = mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                        if (event != null && event.getAction() == KeyEvent.ACTION_DOWN) {

                            // 💡 설정에서 스크린 오프 컨트롤이 꺼져(OFF) 있으면 무시합니다!
                            if (!isScreenOffControlEnabled) return super.onMediaButtonEvent(mediaButtonIntent);

                            int keyCode = event.getKeyCode();

                            // 🔊 휠 왼쪽 (볼륨 다운)
                            if (keyCode == 21) { adjustVolume(false); clickFeedback(); return true; }
                            // 🔊 휠 오른쪽 (볼륨 업)
                            if (keyCode == 22) { adjustVolume(true); clickFeedback(); return true; }
                            // ⏮ 이전 곡 버튼
                            if (keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS || keyCode == 88) { prevTrack(); clickFeedback(); return true; }
                            // ⏭ 다음 곡 버튼
                            if (keyCode == KeyEvent.KEYCODE_MEDIA_NEXT || keyCode == 87) { nextTrack(); clickFeedback(); return true; }
                            // ⏯ 재생/일시정지 버튼
                            if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || keyCode == 85 || keyCode == 86) { playOrPauseMusic(); clickFeedback(); return true; }
                        }
                        return super.onMediaButtonEvent(mediaButtonIntent);
                    }
                });
                mediaSession.setActive(true);
            }
        } catch (Exception e) {}

        prefs = getSharedPreferences("Y1_SETTINGS", MODE_PRIVATE);

        // 🚀 [테마 파일 동적 로드] 기기 내부의 폴더에서 테마 파일들을 읽어옵니다!
        File themeFolder = new File("/storage/sdcard0/Y1_Themes");
        ThemeManager.loadThemesFromStorage(themeFolder);

        try {
            // 저장된 인덱스 번호를 불러옵니다. (파일이 지워졌을 수도 있으니 안전하게 처리됨)
            int savedThemeIndex = prefs.getInt("app_theme_index", 0);
            ThemeManager.setThemeIndex(savedThemeIndex);
        } catch (Exception e) {}

        // (이하 블랙리스트 및 다른 설정 불러오기 코드 유지)
        // 💡 1. 블랙리스트 (안드로이드 내부 버그 방지를 위해 HashSet을 새로 감싸서 안전하게 로드)
        try {
            java.util.Set<String> savedBlacklist = prefs.getStringSet("blacklist", new java.util.HashSet<String>());
            blacklist = new java.util.HashSet<>(savedBlacklist);

            String poisonFile = prefs.getString("last_attempted_file", null);
            if (poisonFile != null) {
                blacklist.add(poisonFile);
                prefs.edit().putStringSet("blacklist", blacklist).remove("last_attempted_file").commit();
            }
        } catch (Exception e) {}

        // 💡 2. 설정값들을 각각 독립적으로 불러오기 (어떤 상황에서도 절대 스킵되지 않습니다!)
        try { isShuffleMode = prefs.getBoolean("shuffle", false); } catch (Exception e) {}

        try {
            if (prefs.contains("repeat_mode")) {
                repeatMode = prefs.getInt("repeat_mode", 0);
            } else {
                repeatMode = prefs.getBoolean("repeat", false) ? 1 : 0;
            }
        } catch (Exception e) {}

        try {
            isSoundEffectEnabled = prefs.getBoolean("sound", true);
            applySoundSetting();
        } catch (Exception e) {}

        try { isVibrationEnabled = prefs.getBoolean("vibrate", true); } catch (Exception e) {}
        try { isScreenOffControlEnabled = prefs.getBoolean("screen_off_control", false); } catch (Exception e) {}

        try { isAutoFetchEnabled = prefs.getBoolean("auto_fetch", true); } catch (Exception e) {} // 🚀 [추가]
        try { currentTimeoutIndex = prefs.getInt("timeout_idx", 1); } catch (Exception e) {}

        try {
            currentSystemBrightness = Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, 255);
        } catch (Exception e) {}

        // 💡 [EQ 프리셋 목록 자동 로드] 기기가 지원하는 이퀄라이저 리스트를 가져옵니다.
        try {
            MediaPlayer dummyMp = new MediaPlayer();
            Equalizer dummyEq = new Equalizer(0, dummyMp.getAudioSessionId());
            short presets = dummyEq.getNumberOfPresets();
            for (short i = 0; i < presets; i++) {
                eqPresetNames.add(dummyEq.getPresetName(i));
            }
            dummyEq.release();
            dummyMp.release();
        } catch (Exception e) {
            eqPresetNames.add("Normal (Default)");
        }

        currentEqPresetIndex = prefs.getInt("eq_preset", 0);
        if (currentEqPresetIndex >= eqPresetNames.size())
            currentEqPresetIndex = 0;

        if (!rootFolder.exists())
            rootFolder.mkdirs();

        // 🚀 [추가된 부분] 앱이 켜질 때(혹은 튕기고 재시작될 때) 조용히 자동 스캔을 돌려 리스트를 복구합니다!
        if (customLibrary.isEmpty() && !isCustomScanning) {
            isCustomScanning = true;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    customLibrary.clear();
                    buildCustomLibrary(rootFolder);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            isCustomScanning = false;
                            // 스캔이 끝났을 때 사용자가 이미 브라우저 화면에 진입해 있다면 화면을 새로고침해 줍니다.
                            if (currentScreenState == STATE_BROWSER) {
                                if (currentBrowserMode == BROWSER_ROOT) {
                                    buildFileBrowserUI();
                                } else if (currentBrowserMode == BROWSER_ARTISTS) {
                                    buildVirtualCategories("ARTIST");
                                } else if (currentBrowserMode == BROWSER_ALBUMS) {
                                    buildVirtualCategories("ALBUM");
                                } else if (currentBrowserMode == BROWSER_VIRTUAL_SONGS) {
                                    buildVirtualSongs();
                                }
                            }
                        }
                    });
                }
            }).start();
        }
        layoutMainMenu = findViewById(R.id.layout_main_menu);
        ivMainBg = findViewById(R.id.iv_main_bg);
        ivMenuPreview = findViewById(R.id.iv_menu_preview);
        tvMenuPreviewTitle = findViewById(R.id.tv_menu_preview_title);
        tvMenuPreviewArtist = findViewById(R.id.tv_menu_preview_artist);
        // 🚀 핵심: 안드로이드는 이 코드가 있어야만 흐르는 글씨(Marquee) 애니메이션을 작동시킵니다!
        tvMenuPreviewTitle.setSelected(true);
        updateMainMenuBackground(); // 💡 앱을 켜면 저장된 상태에 맞춰 배경 자동 적용

        layoutBrowserMode = findViewById(R.id.layout_browser_mode);
        layoutPlayerMode = findViewById(R.id.layout_player_mode);
        containerBrowserItems = findViewById(R.id.container_browser_items);
        layoutVolumeOverlay = findViewById(R.id.layout_volume_overlay);
        volumeProgress = findViewById(R.id.volume_progress);
        volumeProgress.setMax(audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC));

        layoutSettingsMode = findViewById(R.id.layout_settings_mode);
        containerSettingsItems = findViewById(R.id.container_settings_items);
        layoutBluetoothMode = findViewById(R.id.layout_bluetooth_mode);
        containerBtItems = findViewById(R.id.container_bt_items);
        btnScanBt = findViewById(R.id.btn_scan_bt);
        layoutWifiMode = findViewById(R.id.layout_wifi_mode);
        containerWifiItems = findViewById(R.id.container_wifi_items);
        btnScanWifi = findViewById(R.id.btn_scan_wifi);
        // 💡 [추가] 스캔 버튼에 휠 포커스가 닿았을 때 색상 변화 및 중복 소리 차단
        View.OnFocusChangeListener scanFocusListener = new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                Button btn = (Button) v;
                if (hasFocus) {
                    btn.setBackgroundColor(0x88FFFFFF); // 휠이 닿으면 반투명 흰색 배경
                    btn.setTextColor(0xFF000000); // 글자는 검은색으로 반전!
                } else {
                    btn.setBackgroundColor(0x00000000); // 휠이 벗어나면 다시 투명 배경
                    btn.setTextColor(0xFFFFFFFF); // 글자는 원래대로 흰색!
                }
            }
        };
        btnScanBt.setOnFocusChangeListener(scanFocusListener);
        btnScanWifi.setOnFocusChangeListener(scanFocusListener);
        btnScanBt.setSoundEffectsEnabled(false);
        btnScanWifi.setSoundEffectsEnabled(false);
        layoutWifiKeyboard = findViewById(R.id.layout_wifi_keyboard);
        tvKeyboardSsid = findViewById(R.id.tv_keyboard_ssid);
        tvKeyboardInput = findViewById(R.id.tv_keyboard_input);
        tvKeyPprev = findViewById(R.id.tv_key_pprev);
        tvKeyPrev = findViewById(R.id.tv_key_prev);
        tvKeyCurrent = findViewById(R.id.tv_key_current);
        tvKeyNext = findViewById(R.id.tv_key_next);
        tvKeyNnext = findViewById(R.id.tv_key_nnext);

        layoutBrightnessMode = findViewById(R.id.layout_brightness_mode);
        pbBrightness = findViewById(R.id.pb_brightness);
        tvBrightnessVal = findViewById(R.id.tv_brightness_val);

        layoutStorageMode = findViewById(R.id.layout_storage_mode);
        pbStorage = findViewById(R.id.pb_storage);
        tvStorageDetails = findViewById(R.id.tv_storage_details);

        layoutWebServerMode = findViewById(R.id.layout_webserver_mode);
        // (기존 코드)
        tvServerStatus = findViewById(R.id.tv_server_status);
        tvServerIp = findViewById(R.id.tv_server_ip);
        btnServerToggle = findViewById(R.id.btn_server_toggle);

        // 🚀 2. 테마 매니저를 통해 각 화면의 반투명 덮개 색상을 한 번에 갈아입힙니다!
        int overlayColor = ThemeManager.getOverlayBackgroundColor();
        layoutBrowserMode.setBackgroundColor(overlayColor);
        layoutSettingsMode.setBackgroundColor(overlayColor);
        layoutBluetoothMode.setBackgroundColor(overlayColor);
        layoutWifiMode.setBackgroundColor(overlayColor);
        layoutWifiKeyboard.setBackgroundColor(overlayColor);
        layoutBrightnessMode.setBackgroundColor(overlayColor);
        layoutStorageMode.setBackgroundColor(overlayColor);
        layoutWebServerMode.setBackgroundColor(overlayColor);

        // 브라우저 텍스트 등 주요 고정 텍스트도 테마에 맞게 변경


        // 💡 평상시에도 옅은 유리 질감을 주어 버튼 영역이 어디인지 시각적으로 보여줍니다.
        btnServerToggle.setBackgroundColor(0x15FFFFFF);

        btnServerToggle.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    // 🚀 휠이 올라갔을 때: 확실한 우유빛 배경과 검은색 굵은(Bold) 글씨로 반전!
                    btnServerToggle.setBackgroundColor(0xDDFFFFFF);
                    btnServerToggle.setTextColor(0xFF000000);
                    btnServerToggle.setTypeface(null, android.graphics.Typeface.BOLD);
                } else {
                    // 🚀 휠이 벗어났을 때: 다시 은은한 반투명 유리창과 얇은 흰색 글씨로 복귀!
                    btnServerToggle.setBackgroundColor(0x15FFFFFF);
                    btnServerToggle.setTextColor(0xFFFFFFFF);
                    btnServerToggle.setTypeface(null, android.graphics.Typeface.NORMAL);
                }
            }
        });

        btnServerToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickFeedback();
                toggleWebServer();
                updateWebServerUI();
            }
        });

        tvStatusClock = findViewById(R.id.tv_status_clock);
        tvStatusBattery = findViewById(R.id.tv_status_battery);
        ivStatusBluetooth = findViewById(R.id.iv_status_bluetooth);
        ivStatusWifi = findViewById(R.id.iv_status_wifi);
        ivStatusHeadphone = findViewById(R.id.iv_status_headphone);

        tvBrowserPath = findViewById(R.id.tv_browser_path);
        tvBrowserPath.setTextColor(ThemeManager.getTextColorPrimary()); // 🚀 고정된 흰색을 테마 색상으로 변경!

        btnNowPlaying = findViewById(R.id.btn_now_playing);
        btnPlay = findViewById(R.id.btn_play);
        btnSettings = findViewById(R.id.btn_settings);
        btnBluetooth = findViewById(R.id.btn_bluetooth);
        btnRadio = findViewById(R.id.btn_radio);
        Button btnWebServer = findViewById(R.id.btn_webserver);
        tvPlayerTitle = findViewById(R.id.tv_player_title);
        tvPlayerArtist = findViewById(R.id.tv_player_artist);
        tvPlayerTimeCurrent = findViewById(R.id.tv_player_time_current);
        tvPlayerTimeTotal = findViewById(R.id.tv_player_time_total);
        ivAlbumArt = findViewById(R.id.iv_album_art);

        // 🚀 [수정] 좁은 정사각형 액자(FrameLayout)가 아니라, 화면 전체 넓이를 쓰는 부모(LinearLayout)에게 붙입니다!
        android.widget.FrameLayout albumContainer = (android.widget.FrameLayout) ivAlbumArt.getParent();
        android.widget.LinearLayout playerInnerLayout = (android.widget.LinearLayout) albumContainer.getParent();

        visualizerView = new AudioVisualizerView(this);
        visualizerView.setVisibility(View.GONE);

        int height190 = (int)(190 * getResources().getDisplayMetrics().density);
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, height190);
        lp.setMargins(0, 0, 0, (int)(8 * getResources().getDisplayMetrics().density));

        playerInnerLayout.addView(visualizerView, 0, lp); // 곡 제목 바로 위에 끼워 넣습니다.

        ivPlayerBgBlur = findViewById(R.id.iv_player_bg_blur);
        ivPauseOverlay = findViewById(R.id.iv_pause_overlay);
        playerProgress = findViewById(R.id.player_progress);
        tvPlayerTrackCount = findViewById(R.id.tv_player_track_count);

        // 🚀 [수정 후]
        ivPlayerShuffleStatus = findViewById(R.id.iv_player_shuffle_status);
        ivPlayerRepeatStatus = findViewById(R.id.iv_player_repeat_status);
        updatePlayerStatusIndicators();

        // 🚀 [수정] 아이콘 파일명(.png)을 매개변수로 던져줍니다.
        setupMenuButton(btnNowPlaying, R.drawable.music_circle, "icon_now_playing.png");
        setupMenuButton(btnPlay, R.drawable.music_list, "icon_music.png");
        setupMenuButton(btnBluetooth, R.drawable.bluetooth_circle, "icon_bluetooth.png");
        setupMenuButton(btnSettings, R.drawable.setting_circle, "icon_setting.png");
        setupMenuButton(btnRadio, R.drawable.radio_circle, "icon_radio.png");
        setupMenuButton(btnWebServer, R.drawable.file_sync, "icon_server.png");

        // [클릭 리스너 부분에 추가]
        btnWebServer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                changeScreen(STATE_WEBSERVER); // 서버 화면으로 바로 이동!
            }
        });
        btnNowPlaying.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentPlaylist.isEmpty()) {
                    Toast.makeText(MainActivity.this, "No music is currently playing.", Toast.LENGTH_SHORT).show();
                } else {
                    changeScreen(STATE_PLAYER);
                }
            }
        });

        btnPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                currentBrowserMode = BROWSER_ROOT; // 💡 뮤직 진입 시 라이브러리 최상단으로!

                // 🚀 재부팅 직후 SD 카드가 늦게 인식되어 초기 스캔이 실패했을 경우를 대비해,
                // 뮤직 메뉴 진입 시 라이브러리가 비어있다면 다시 한번 스캔을 자동으로 돌립니다.
                if (customLibrary.isEmpty() && !isCustomScanning) {
                    isCustomScanning = true;
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            customLibrary.clear();
                            buildCustomLibrary(rootFolder);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    isCustomScanning = false;
                                    if (currentScreenState == STATE_BROWSER) {
                                        if (currentBrowserMode == BROWSER_ROOT) {
                                            buildFileBrowserUI();
                                        } else if (currentBrowserMode == BROWSER_ARTISTS) {
                                            buildVirtualCategories("ARTIST");
                                        } else if (currentBrowserMode == BROWSER_ALBUMS) {
                                            buildVirtualCategories("ALBUM");
                                        } else if (currentBrowserMode == BROWSER_VIRTUAL_SONGS) {
                                            buildVirtualSongs();
                                        }
                                    }
                                }
                            });
                        }
                    }).start();
                }

                changeScreen(STATE_BROWSER);
            }
        });
        btnSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                changeScreen(STATE_SETTINGS);
            }
        });
        btnBluetooth.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                changeScreen(STATE_BLUETOOTH);
            }
        });
        btnRadio.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    startActivity(
                            new Intent().setClassName("com.mediatek.FMRadio", "com.mediatek.FMRadio.FMRadioActivity"));
                } catch (Exception e) {
                    Intent b = getPackageManager().getLaunchIntentForPackage("com.mediatek.FMRadio");
                    if (b != null)
                        startActivity(b);
                }
            }
        });

        btnScanBt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startBluetoothScan();
            }
        });
        btnScanWifi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startWifiScan();
            }
        });

        clockHandler.post(clockTask);

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_MEDIA_SCANNER_STARTED);
        filter.addAction(Intent.ACTION_MEDIA_SCANNER_FINISHED);
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        filter.addAction(Intent.ACTION_HEADSET_PLUG);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        filter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiver(systemStatusReceiver, filter);

        try {
            if (audioManager.isWiredHeadsetOn()) {
                ivStatusHeadphone.setVisibility(View.VISIBLE);
                ivStatusHeadphone.setColorFilter(0xFF00FFFF);
            }
            BluetoothAdapter ba = BluetoothAdapter.getDefaultAdapter();
            if (ba != null && ba.isEnabled()) {
                ivStatusBluetooth.setVisibility(View.VISIBLE);
                ivStatusBluetooth.setColorFilter(0xFF5555FF);
            }
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm != null && wm.isWifiEnabled()) {
                ivStatusWifi.setVisibility(View.VISIBLE);
                WifiInfo info = wm.getConnectionInfo();
                if (info != null && info.getNetworkId() != -1)
                    ivStatusWifi.setColorFilter(0xFF00FF00);
                else
                    ivStatusWifi.setColorFilter(0xFFFFBB00);
            }
        } catch (Exception e) {
        }

        btnNowPlaying.requestFocus();


        // 🚀 1. 메인 화면의 배경과 글자색도 테마 매니저에 맞춰 갈아입힙니다!
        applyThemeToMainMenu();

        triggerAutoReconnect();

        // 🚀 2. 테마를 바꾸고 화면이 새로고침(recreate)되었을 때, 메인 화면이 아닌 '테마 선택 리스트'로 돌아오게 만듭니다!
        boolean rebootToTheme = prefs.getBoolean("reboot_to_theme", false);
        if (rebootToTheme) {
            prefs.edit().remove("reboot_to_theme").commit(); // 기억을 사용했으니 지웁니다.
            changeScreen(STATE_SETTINGS);
            buildThemeSelectorUI(); // 테마 리스트 화면을 바로 띄워줍니다.
        } else {
            btnNowPlaying.requestFocus(); // 평소 앱을 켤 때는 원래대로 메인 메뉴 포커스
        }
    }
    // 💡 [추가] 문자열에서 첫 글자를 뽑아내어 화면에 띄워주는 함수
    private void showFastScrollLetter(String rawText) {
        // 브라우저 모드(리스트 화면)가 아니면 띄우지 않습니다.
        if (tvFastScrollLetter == null || currentScreenState != STATE_BROWSER) return;

        // 버튼 텍스트 앞에 붙어있는 꾸밈용 이모지들을 싹 지우고 순수 제목만 남깁니다.
        String clean = rawText.replace("📁 ", "").replace("👤 ", "")
                .replace("💿 ", "").replace("🎵 ", "")
                .replace("📦 [INSTALL] ", "").trim();

        if (clean.isEmpty()) return;

        // 첫 글자 1개만 추출 (무조건 대문자로 변환)
        String firstChar = clean.substring(0, 1).toUpperCase();
        tvFastScrollLetter.setText(firstChar);

        // 🚀 현재 적용된 테마의 강조 색상으로 박스를 예쁘게 색칠합니다!
        tvFastScrollLetter.setTextColor(ThemeManager.getTextColorPrimary());
        tvFastScrollLetter.setTypeface(ThemeManager.getCustomFont(), android.graphics.Typeface.BOLD);
        android.graphics.drawable.GradientDrawable letterBg = new android.graphics.drawable.GradientDrawable();
        letterBg.setColor(ThemeManager.getListButtonFocusedBg() | 0xDD000000); // 살짝 반투명하게 덮기
        letterBg.setCornerRadius(15 * getResources().getDisplayMetrics().density); // 둥근 모서리
        tvFastScrollLetter.setBackground(letterBg);

        tvFastScrollLetter.setVisibility(View.VISIBLE);

        // 0.8초 동안 휠 조작이 없으면 글자가 자동으로 스르륵 사라지도록 타이머 리셋
        fastScrollHandler.removeCallbacks(hideFastScrollTask);
        fastScrollHandler.postDelayed(hideFastScrollTask, 800);
    }

    // 💡 [추가] XML의 고정값을 무시하고, 메인 화면 전체를 테마에 맞게 강제로 칠해주는 함수
    private void applyThemeToMainMenu() {
        try {
            // 🚀 1. 가장 핵심! 전체 배경 이미지(ivMainBg) 위에 테마 색상을 셀로판지처럼 덮어씌웁니다.
            if (ivMainBg != null) {
                int themeColor = ThemeManager.getOverlayBackgroundColor();
                // 색상 정보(RGB)만 뽑아낸 뒤, 앞에 0x66(투명도 40%)을 강제로 결합합니다.
                int softTint = (themeColor & 0x00FFFFFF) | 0x66000000;
                ivMainBg.setColorFilter(softTint, android.graphics.PorterDuff.Mode.SRC_ATOP);
            }

            // 2. 좌측 반투명 메뉴 배경판 색칠하기
            LinearLayout leftPane = (LinearLayout) ((LinearLayout) layoutMainMenu).getChildAt(0);
            leftPane.setBackgroundColor(ThemeManager.getOverlayBackgroundColor());
            View statusBar = findViewById(R.id.layout_status_bar);
            if (statusBar != null) {
                // 🚀 [수정] 방금 만든 상태바 전용 색상 Getter를 불러옵니다!
                statusBar.setBackgroundColor(ThemeManager.getStatusBarBackgroundColor());
            }
            int primary = ThemeManager.getTextColorPrimary();
            int secondary = ThemeManager.getTextColorSecondary();

            // 3. 메인 메뉴 버튼들의 기본 글자색 변경
            btnNowPlaying.setTextColor(primary);
            btnPlay.setTextColor(primary);
            btnBluetooth.setTextColor(primary);
            btnSettings.setTextColor(primary);
            btnRadio.setTextColor(primary);
            Button btnWebServer = findViewById(R.id.btn_webserver);
            if (btnWebServer != null) btnWebServer.setTextColor(primary);

            // 4. 버튼 옆에 있는 〉(화살표) 들의 색상 변경
            for (int i = 0; i < leftPane.getChildCount(); i++) {
                View child = leftPane.getChildAt(i);
                if (child instanceof android.widget.FrameLayout) {
                    android.widget.FrameLayout fl = (android.widget.FrameLayout) child;
                    for (int j = 0; j < fl.getChildCount(); j++) {
                        View flChild = fl.getChildAt(j);
                        if (flChild instanceof TextView && !(flChild instanceof Button)) {
                            ((TextView) flChild).setTextColor(secondary);
                        }
                    }
                }
            }

            // 5. 우측 빈 공간의 곡 제목/가수 및 상단 상태바(시계, 배터리) 글자색 덮어쓰기!
            if (tvMenuPreviewTitle != null) tvMenuPreviewTitle.setTextColor(primary);
            if (tvMenuPreviewArtist != null) tvMenuPreviewArtist.setTextColor(secondary);
            if (tvStatusClock != null) tvStatusClock.setTextColor(primary);
            if (tvStatusBattery != null) tvStatusBattery.setTextColor(primary);
            // 🚀 [수정 완료] 앱 내의 모든 막대바를 '테마 버튼 포커스 색상(강조색)'으로 진하게 물들입니다!
            int themeColor = ThemeManager.getListButtonFocusedBg() | 0xFF000000;

            // 1. 플레이어 화면의 음악 재생 바
            if (playerProgress != null) {
                try {
                    android.graphics.drawable.LayerDrawable layer = (android.graphics.drawable.LayerDrawable) playerProgress.getProgressDrawable();
                    android.graphics.drawable.Drawable progress = layer.findDrawableByLayerId(android.R.id.progress);
                    if (progress != null) progress.setColorFilter(themeColor, android.graphics.PorterDuff.Mode.SRC_IN);
                } catch (Exception e) {
                    playerProgress.getProgressDrawable().setColorFilter(themeColor, android.graphics.PorterDuff.Mode.SRC_IN);
                }
            }

            // 2. 휠 돌릴 때 나오는 볼륨 조절 바
            if (volumeProgress != null) {
                try {
                    android.graphics.drawable.LayerDrawable layer = (android.graphics.drawable.LayerDrawable) volumeProgress.getProgressDrawable();
                    android.graphics.drawable.Drawable progress = layer.findDrawableByLayerId(android.R.id.progress);
                    if (progress != null) progress.setColorFilter(themeColor, android.graphics.PorterDuff.Mode.SRC_IN);
                } catch (Exception e) {
                    volumeProgress.getProgressDrawable().setColorFilter(themeColor, android.graphics.PorterDuff.Mode.SRC_IN);
                }
            }

            // 3. 설정 화면의 화면 밝기 조절 바
            if (pbBrightness != null) {
                try {
                    android.graphics.drawable.LayerDrawable layer = (android.graphics.drawable.LayerDrawable) pbBrightness.getProgressDrawable();
                    android.graphics.drawable.Drawable progress = layer.findDrawableByLayerId(android.R.id.progress);
                    if (progress != null) progress.setColorFilter(themeColor, android.graphics.PorterDuff.Mode.SRC_IN);
                } catch (Exception e) {
                    pbBrightness.getProgressDrawable().setColorFilter(themeColor, android.graphics.PorterDuff.Mode.SRC_IN);
                }
            }
            android.view.ViewGroup root = findViewById(android.R.id.content);
            applyFontToAllViews(root, ThemeManager.getCustomFont());
        } catch (Exception e) {}
    }
    // 💡 [추가] 테마 리스트를 쫙 보여주고 사용자가 고를 수 있게 하는 전용 화면
    private void buildThemeSelectorUI() {
        File themeFolder = new File("/storage/sdcard0/Y1_Themes");
        ThemeManager.loadThemesFromStorage(themeFolder);

        containerSettingsItems.removeAllViews();
        createCategoryHeader("━ SELECT APP THEME ━");

        Button btnBack = createListButton("〈 CANCEL & BACK");
        btnBack.setTextColor(ThemeManager.getTextColorSecondary());
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickFeedback();
                buildSettingsUI(); // 다시 설정 메인으로 돌아갑니다.
            }
        });
        containerSettingsItems.addView(btnBack);

        // SD카드 폴더에서 읽어온 테마들을 하나씩 버튼으로 만듭니다.
        for (int i = 0; i < ThemeManager.availableThemes.size(); i++) {
            final int index = i;
            ThemeManager.ThemeData theme = ThemeManager.availableThemes.get(i);

            String prefix = (ThemeManager.getCurrentThemeIndex() == i) ? "✔ " : "   ";
            Button btn = createListButton(prefix + theme.name);

            if (ThemeManager.getCurrentThemeIndex() == i) {
                btn.setTypeface(null, android.graphics.Typeface.BOLD);
                btn.setTextColor(0xFF00FF00); // 현재 사용 중인 테마는 초록색으로 굵게 강조!
            }

            btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    clickFeedback();
                    ThemeManager.setThemeIndex(index);
                    try {
                        // 🚀 테마를 바꾸고 새로고침될 때 다시 이 리스트 화면으로 돌아오도록 '티켓'을 발급해 둡니다.
                        prefs.edit().putInt("app_theme_index", index).putBoolean("reboot_to_theme", true).commit();
                    } catch (Exception e) {}

                    recreate(); // 화면 새로고침! (이제 메인으로 튕기지 않고 리스트 화면으로 복귀합니다)
                }
            });
            containerSettingsItems.addView(btn);
        }

        // 화면이 열리면 맨 처음 테마 버튼에 휠 포커스를 맞춰줍니다.
        if (containerSettingsItems.getChildCount() > 1) {
            containerSettingsItems.getChildAt(1).requestFocus();
        }
    }
    private void triggerAutoReconnect() {
        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm != null && wm.isWifiEnabled()) {
                wm.reconnect();
            }
            BluetoothAdapter ba = BluetoothAdapter.getDefaultAdapter();
            if (ba != null && ba.isEnabled()) {
                java.util.Set<BluetoothDevice> pairedDevices = ba.getBondedDevices();
            }
        } catch (Exception e) {
        }
    }

    private void toggleWebServer() {
        if (isServerRunning) {
            if (webServer != null)
                webServer.stopServer();
            isServerRunning = false;
        } else {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            if (wm == null || !wm.isWifiEnabled()) {
                Toast.makeText(this, "Please turn ON Wi-Fi first", Toast.LENGTH_SHORT).show();
                return;
            }
            webServer = new Y1WebServer(getApplicationContext(), rootFolder);
            webServer.start();
            isServerRunning = true;
        }
    }

    private void updateWebServerUI() {
        if (isServerRunning) {
            // 💡 애플 스타일: 이모지를 빼고 깔끔한 흰색으로!
            tvServerStatus.setText("SERVER RUNNING");
            tvServerStatus.setTextColor(0xFFFFFFFF);
            tvServerIp.setText("http://" + webServer.getLocalIpAddress() + ":8080");
            tvServerIp.setTextColor(0xFFFFFFFF);
            btnServerToggle.setText("STOP SERVER");
        } else {
            // 💡 애플 스타일: 튀지 않는 은은한 회색으로!
            tvServerStatus.setText("SERVER STOPPED");
            tvServerStatus.setTextColor(0xFF888888);
            tvServerIp.setText("http://---.---.---.---:8080");
            tvServerIp.setTextColor(0xFF888888);
            btnServerToggle.setText("START SERVER");
        }
    }

    // 💡 메인 화면 배경 자동 업데이트 (고화질 가우시안 블러 적용)
    // 💡 [수정] 메인 화면 배경 자동 업데이트 (커스텀 배경 우선 & 블러 제거)
    private void updateMainMenuBackground() {
        try {
            String savedBgPath = prefs.getString("bg_path", null);

            // 🚀 1. 사용자가 직접 지정한 커스텀 배경이 있는지 가장 먼저 확인합니다!
            if (savedBgPath != null && !savedBgPath.isEmpty()) {
                File bgFile = new File(savedBgPath);
                if (bgFile.exists()) {
                    BitmapFactory.Options opts = new BitmapFactory.Options();
                    opts.inJustDecodeBounds = true;
                    BitmapFactory.decodeFile(savedBgPath, opts);

                    // 블러를 안 먹일 것이므로, 화질이 깨지지 않게 해상도를 넉넉하게(800) 잡아줍니다.
                    int scale = 1;
                    int maxDim = Math.max(opts.outWidth, opts.outHeight);
                    while (maxDim / scale > 800) {
                        scale *= 2;
                    }

                    opts.inJustDecodeBounds = false;
                    opts.inSampleSize = scale;
                    Bitmap customBg = BitmapFactory.decodeFile(savedBgPath, opts);

                    // 🚀 커스텀 배경은 블러 처리 없이 쨍한 원본 그대로 띄웁니다!
                    ivMainBg.setImageBitmap(customBg);

                    // 🚀 여기서 함수를 강제 종료하여, 재생 중인 앨범 아트가 배경을 덮어쓰지 못하게 철벽 방어합니다!
                    return;
                }
            }

            // 🚀 2. 커스텀 배경이 없다면(해제했다면), 기존처럼 앨범 아트나 기본 이미지에 '블러'를 먹여서 출력합니다.
            Bitmap sourceBitmap = null;
            if (lastAlbumArtBytes != null && lastAlbumArtBytes.length > 0) {
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inSampleSize = 4;
                sourceBitmap = BitmapFactory.decodeByteArray(lastAlbumArtBytes, 0, lastAlbumArtBytes.length, opts);
            } else {
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inSampleSize = 2;
                sourceBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.default_back, opts);
            }

            if (sourceBitmap != null) {
                Bitmap blurredBitmap = applyGaussianBlur(sourceBitmap);
                ivMainBg.setImageBitmap(blurredBitmap);
                if (sourceBitmap != blurredBitmap) {
                    sourceBitmap.recycle();
                }
            } else {
                ivMainBg.setImageResource(R.drawable.default_back);
            }
        } catch (Throwable t) {
            ivMainBg.setImageResource(R.drawable.default_back);
        }
    }
    // 💡 [추가] 테마 색상과 '둥글기(Radius)'를 혼합해서 버튼의 배경 디자인을 찍어내는 도구
    private android.graphics.drawable.GradientDrawable createButtonBackground(int color) {
        android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
        shape.setColor(color); // 테마 색상 주입
        // 테마에 설정된 둥글기(Radius) 값 주입 (dp 단위를 픽셀로 변환하여 적용)
        float radius = ThemeManager.getButtonRadius() * getResources().getDisplayMetrics().density;
        shape.setCornerRadius(radius);
        return shape;
    }
    // 💡 [수정] 메인 화면의 버튼들에 휠이 닿았을 때의 색상을 테마 엔진과 연결합니다!
    private void setupMenuButton(final Button btn, final int imageResId, final String iconFileName) {
        btn.setSoundEffectsEnabled(false);
        btn.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    btn.setBackground(createButtonBackground(ThemeManager.getListButtonFocusedBg())); // 🚀 둥글기 적용
                    btn.setTextColor(ThemeManager.getListButtonFocusedTextColor());
                    // 🚀 [수정] 재생 상태를 확인하여 초기 아이콘(원형)과 빈 앨범 아이콘(사각형)을 완벽하게 구분합니다!
                    if (btn.getId() == R.id.btn_now_playing) {

                        // 1. 노래가 아예 재생된 적이 없는 '초기 상태'일 때 -> 둥근 음표 아이콘(music_circle) 유지
                        if (currentPlaylist.isEmpty()) {
                            ivMenuPreview.setImageBitmap(ThemeManager.getCustomIcon(iconFileName, MainActivity.this, imageResId));
                            ivMenuPreview.setImageResource(imageResId);
                            if (tvMenuPreviewTitle != null && tvMenuPreviewArtist != null) {
                                tvMenuPreviewTitle.setVisibility(View.GONE);
                                tvMenuPreviewArtist.setVisibility(View.GONE);
                            }
                        }
                        // 2. 노래가 재생 중일 때
                        else {
                            if (lastAlbumArtBytes != null && lastAlbumArtBytes.length > 0) {
                                try {
                                    android.graphics.BitmapFactory.Options opts = new android.graphics.BitmapFactory.Options();
                                    opts.inSampleSize = 2;
                                    android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeByteArray(lastAlbumArtBytes, 0, lastAlbumArtBytes.length, opts);
                                    ivMenuPreview.setImageBitmap(bmp);
                                } catch (Exception e) {
                                    ivMenuPreview.setImageBitmap(ThemeManager.getCustomIcon("icon_default_album.png", MainActivity.this, R.drawable.default_album));
                                    ivMenuPreview.setImageResource(R.drawable.default_album); // 에러 시 사각형 앨범
                                }
                            } else {
                                ivMenuPreview.setImageBitmap(ThemeManager.getCustomIcon("icon_default_album.png", MainActivity.this, R.drawable.default_album));
                                ivMenuPreview.setImageResource(R.drawable.default_album); // 이미지가 없으면 사각형 앨범
                            }

                            // 재생 중이라면 정보 텍스트는 무조건 띄워줍니다.
                            if (tvMenuPreviewTitle != null && tvMenuPreviewArtist != null) {
                                tvMenuPreviewTitle.setVisibility(View.VISIBLE);
                                tvMenuPreviewArtist.setVisibility(View.VISIBLE);
                                tvMenuPreviewTitle.setText(tvPlayerTitle.getText());
                                tvMenuPreviewArtist.setText(tvPlayerArtist.getText());
                            }
                        }

                    } else {
                        // 다른 메뉴(Settings, Bluetooth 등)에 닿았을 때는 원래 아이콘만 보여주고 텍스트를 숨깁니다.
                        ivMenuPreview.setImageBitmap(ThemeManager.getCustomIcon(iconFileName, MainActivity.this, imageResId));
                        ivMenuPreview.setImageResource(imageResId);
                        if (tvMenuPreviewTitle != null && tvMenuPreviewArtist != null) {
                            tvMenuPreviewTitle.setVisibility(View.GONE);
                            tvMenuPreviewArtist.setVisibility(View.GONE);
                        }
                    }

                } else {
                    btn.setBackgroundColor(0x00000000);
                    btn.setTextColor(ThemeManager.getTextColorPrimary());
                }
            }
        });
    }
    private void changeScreen(int state) {

        int safeFocusIndex = lastSettingsFocusIndex;
        currentScreenState = state;
        layoutMainMenu.setVisibility(state == STATE_MENU ? View.VISIBLE : View.GONE);
        layoutBrowserMode.setVisibility(state == STATE_BROWSER ? View.VISIBLE : View.GONE);
        layoutPlayerMode.setVisibility(state == STATE_PLAYER ? View.VISIBLE : View.GONE);
        layoutSettingsMode.setVisibility(state == STATE_SETTINGS ? View.VISIBLE : View.GONE);
        layoutBluetoothMode.setVisibility(state == STATE_BLUETOOTH ? View.VISIBLE : View.GONE);
        layoutWifiMode.setVisibility(state == STATE_WIFI ? View.VISIBLE : View.GONE);
        layoutWifiKeyboard.setVisibility(state == STATE_WIFI_KEYBOARD ? View.VISIBLE : View.GONE);
        layoutBrightnessMode.setVisibility(state == STATE_BRIGHTNESS ? View.VISIBLE : View.GONE);
        layoutStorageMode.setVisibility(state == STATE_STORAGE ? View.VISIBLE : View.GONE);
        layoutWebServerMode.setVisibility(state == STATE_WEBSERVER ? View.VISIBLE : View.GONE);
        layoutVolumeOverlay.setVisibility(View.GONE);
        View statusBar = findViewById(R.id.layout_status_bar);
        if (statusBar != null) {
            if (state == STATE_PLAYER) {
                // 플레이어 화면(음악 재생 화면)일 때는 무조건 완전 투명하게!
                statusBar.setBackgroundColor(0x00000000);
            } else {
                // 그 외의 다른 화면(메뉴, 설정, 파일 탐색기 등)일 때는 테마에 지정된 상태바 색상으로 복구!
                statusBar.setBackgroundColor(ThemeManager.getStatusBarBackgroundColor());
            }
        }
        if (state == STATE_MENU) {
            isPickingBackground = false;
            View c = getCurrentFocus();
            if (c == null)
                btnNowPlaying.requestFocus();
            refreshNowPlayingPreview();
        } else if (state == STATE_BROWSER) {
            if (currentBrowserMode == BROWSER_ROOT || currentBrowserMode == BROWSER_FOLDER) {
                buildFileBrowserUI();
            } else if (currentBrowserMode == BROWSER_ARTISTS) {
                buildVirtualCategories("ARTIST");
            } else if (currentBrowserMode == BROWSER_ALBUMS) {
                buildVirtualCategories("ALBUM");
            } else if (currentBrowserMode == BROWSER_VIRTUAL_SONGS) {
                buildVirtualSongs();
            }
        } else if (state == STATE_SETTINGS) {
            // 🚀 시스템에 의해 오염된 인덱스를 버리고, 금고에 백업해둔 진짜 위치로 복구한 뒤 화면을 그립니다!
            lastSettingsFocusIndex = safeFocusIndex;
            buildSettingsUI();

        } else if (state == STATE_BLUETOOTH) {
            startBluetoothScan();
        } else if (state == STATE_WIFI) {
            startWifiScan();
        } else if (state == STATE_WIFI_KEYBOARD) {
            openKeyboard();
        } else if (state == STATE_BRIGHTNESS) {
            loadBrightnessUI();
        } else if (state == STATE_STORAGE) {
            loadStorageUI();
        } else if (state == STATE_WEBSERVER) {
            updateWebServerUI();
            btnServerToggle.requestFocus();
        }
    }

    private void loadBrightnessUI() {
        try {
            currentSystemBrightness = Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS,
                    255);
        } catch (Exception e) {
        }
        pbBrightness.setProgress(currentSystemBrightness);
        int percent = (int) (((float) currentSystemBrightness / 255.0f) * 100);
        tvBrightnessVal.setText(percent + "%");
    }

    private void updateBrightness(int newBrightness) {
        currentSystemBrightness = newBrightness;
        pbBrightness.setProgress(currentSystemBrightness);
        int percent = (int) (((float) currentSystemBrightness / 255.0f) * 100);
        tvBrightnessVal.setText(percent + "%");

        try {
            Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
            Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, currentSystemBrightness);
            WindowManager.LayoutParams layoutParams = getWindow().getAttributes();
            layoutParams.screenBrightness = currentSystemBrightness / 255.0f;
            getWindow().setAttributes(layoutParams);
        } catch (Exception e) {
        }
    }

    // 💡 [수정] 기존 막대바를 숨기고 우리가 만든 원형 차트를 동적으로 띄워주는 로직
    // 💡 [수정] 스토리지 상세 정보 텍스트 적용
    // 💡 [완벽 수정] 스토리지 용량 계산 에러(오버플로우) 방지 및 진짜 테마 색상 적용
    private void loadStorageUI() {
        try {
            android.os.StatFs stat = new android.os.StatFs("/storage/sdcard0");

            // 🚀 [버그 1 해결] 기기 용량이 클 때 숫자가 폭발(오버플로우)해서 에러가 나는 것을 막기 위해 (long)으로 강제 변환하여 계산합니다!
            long blockSize = (long) stat.getBlockSize();
            long total = ((long) stat.getBlockCount() * blockSize) / (1024 * 1024);
            long free = ((long) stat.getAvailableBlocks() * blockSize) / (1024 * 1024);
            long used = total - free;

            if (pbStorage != null) pbStorage.setVisibility(View.GONE);

            LinearLayout storageLayout = findViewById(R.id.layout_storage_mode);
            PieChartView pieChart = (PieChartView) storageLayout.findViewWithTag("pie_chart");

            if (pieChart == null) {
                pieChart = new PieChartView(this);
                pieChart.setTag("pie_chart");

                // 🚀 [버그 2 해결] 차트가 너무 커서 아래 글씨를 화면 밖으로 밀어내지 않도록 크기를 140dp로 최적화합니다.
                int size = (int)(140 * getResources().getDisplayMetrics().density);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
                lp.setMargins(0, 0, 0, 30);
                pieChart.setLayoutParams(lp);

                storageLayout.addView(pieChart, 1);
            }

            // 🚀 [버그 3 해결] 밋밋한 흰색(글자색) 대신, 테마의 진짜 강조 색상(버튼 포커스 색상)을 뽑아와서 투명도를 뺀 원색으로 칠합니다!
            int themeColor = ThemeManager.getListButtonFocusedBg() | 0xFF000000;
            pieChart.setStorageData(used, total, themeColor);

            // 텍스트 정보 세팅 및 화면 강제 노출
            tvStorageDetails.setText("Total Capacity :  " + total + " MB\nUsed Space :  " + used + " MB\nFree Space :  " + free + " MB");
            tvStorageDetails.setGravity(android.view.Gravity.CENTER);
            tvStorageDetails.setLineSpacing(15f, 1f);
            tvStorageDetails.setVisibility(View.VISIBLE);

        } catch (Exception e) {
            tvStorageDetails.setText("Storage Error: Failed to calculate space.");
            tvStorageDetails.setVisibility(View.VISIBLE);
        }
    }
    private void handleCenterShortClick() {
        if (currentScreenState == STATE_PLAYER) {
            toggleVisualizer();
            clickFeedback();
        }
        else if (currentScreenState == STATE_WIFI_KEYBOARD) {
            handleKeyboardInput();
        } else if (currentScreenState != STATE_BRIGHTNESS && currentScreenState != STATE_STORAGE
                && currentScreenState != STATE_PLAYER) {
            View c = getCurrentFocus();
            if (c != null)
                c.performClick();
        }
    }

    // 💡 앱 자체의 억지 소리 발생 코드를 완전히 삭제합니다! (기기 하드웨어 소리만 사용)
    private void clickFeedback() {
        try {
            if (isVibrationEnabled) {
                Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                if (v != null)
                    v.vibrate(30);
            }
        } catch (Exception e) {
        }
    }

    private void openKeyboard() {
        typedPassword = "";
        keyboardIndex = 0;
        tvKeyboardSsid.setText("Target: " + targetWifiSsid);
        updateKeyboardUI();
    }

    private void updateKeyboardUI() {
        int len = KEYBOARD_CHARS.length;
        int idxPprev = (keyboardIndex - 2 + len) % len;
        int idxPrev = (keyboardIndex - 1 + len) % len;
        int idxNext = (keyboardIndex + 1) % len;
        int idxNnext = (keyboardIndex + 2) % len;
        tvKeyPprev.setText(KEYBOARD_CHARS[idxPprev]);
        tvKeyPrev.setText(KEYBOARD_CHARS[idxPrev]);
        tvKeyCurrent.setText(KEYBOARD_CHARS[keyboardIndex]);
        tvKeyNext.setText(KEYBOARD_CHARS[idxNext]);
        tvKeyNnext.setText(KEYBOARD_CHARS[idxNnext]);
        if (isTargetWifiOpen) {
            tvKeyboardInput.setText("Open Network (Direct Connect)");
            keyboardIndex = len - 1;
            tvKeyCurrent.setText(KEYBOARD_CHARS[keyboardIndex]);
        } else {
            tvKeyboardInput.setText(typedPassword.length() == 0 ? "Enter Password..." : typedPassword);
        }
    }

    private void handleKeyboardInput() {
        String selectedChar = KEYBOARD_CHARS[keyboardIndex];
        clickFeedback();
        if (selectedChar.equals("[DEL]")) {
            if (typedPassword.length() > 0)
                typedPassword = typedPassword.substring(0, typedPassword.length() - 1);
        } else if (selectedChar.equals("[CONN]")) {
            connectToWifi();
        } else {
            typedPassword += selectedChar;
        }
        updateKeyboardUI();
    }

    private void connectToWifi() {
        Toast.makeText(this, "Connecting to " + targetWifiSsid + "...", Toast.LENGTH_SHORT).show();
        WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wm != null) {
            WifiConfiguration conf = new WifiConfiguration();
            conf.SSID = "\"" + targetWifiSsid + "\"";
            if (isTargetWifiOpen)
                conf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
            else
                conf.preSharedKey = "\"" + typedPassword + "\"";
            int netId = wm.addNetwork(conf);
            wm.disconnect();
            wm.enableNetwork(netId, true);
            wm.reconnect();
            wm.saveConfiguration();
        }
        changeScreen(STATE_WIFI);
    }

    @android.annotation.SuppressLint("MissingPermission")
    private void startBluetoothScan() {
        final BluetoothAdapter ba = BluetoothAdapter.getDefaultAdapter();
        boolean isOn = false;
        String statusText = "OFF";

        if (ba != null) {
            int state = ba.getState();
            if (state == BluetoothAdapter.STATE_ON) {
                isOn = true;
                statusText = "ON";
            } else if (state == BluetoothAdapter.STATE_TURNING_ON || state == BluetoothAdapter.STATE_TURNING_OFF) {
                statusText = "Wait...";
            }
        }

        View existingToggle = containerBtItems.findViewById(999991);
        if (existingToggle == null) {
            final LinearLayout btnToggle = createSettingRow("Bluetooth Power", statusText);
            btnToggle.setId(999991);
            btnToggle.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    clickFeedback();
                    if (ba != null) {
                        boolean isCurrentlyOn = ba.isEnabled();
                        if (isCurrentlyOn) {
                            Toast.makeText(MainActivity.this, "Turning Bluetooth OFF...", Toast.LENGTH_SHORT).show();
                            ba.disable();
                        } else {
                            Toast.makeText(MainActivity.this, "Turning Bluetooth ON...", Toast.LENGTH_SHORT).show();
                            ba.enable();
                        }
                        TextView tvRight = (TextView) btnToggle.getChildAt(1);
                        tvRight.setText("Wait...");
                        if (!btnToggle.hasFocus())
                            tvRight.setTextColor(0xFFFFFF00);
                    }
                }
            });
            containerBtItems.addView(btnToggle, 0);

            if (btnScanBt.getParent() != null) {
                ((android.view.ViewGroup) btnScanBt.getParent()).removeView(btnScanBt);
            }
            containerBtItems.addView(btnScanBt);
        } else {
            LinearLayout btnToggle = (LinearLayout) existingToggle;
            TextView tvRight = (TextView) btnToggle.getChildAt(1);
            tvRight.setText(statusText);
            if (!btnToggle.hasFocus()) {
                if (statusText.equals("ON"))
                    tvRight.setTextColor(0xFFFFFFFF);
                else if (statusText.equals("OFF"))
                    tvRight.setTextColor(0xFF888888);
                else
                    tvRight.setTextColor(0xFFFFFFFF);
            }
            for (int i = containerBtItems.getChildCount() - 1; i > 0; i--) {
                View v = containerBtItems.getChildAt(i);
                if (v != btnScanBt) {
                    containerBtItems.removeViewAt(i);
                }
            }
        }

        if (!isOn) {
            btnScanBt.setText("Bluetooth is OFF");
            if (getCurrentFocus() == null && containerBtItems.getChildCount() > 0)
                containerBtItems.getChildAt(0).requestFocus();
            return;
        }

        btnScanBt.setText("Scanning...");
        foundBtDevices.clear();

        // 🚀 1순위: 기기에 이미 페어링(저장)된 블루투스 기기들을 최상단에 먼저 깔아줍니다!
        try {
            java.util.Set<BluetoothDevice> pairedDevices = ba.getBondedDevices();
            if (pairedDevices != null && pairedDevices.size() > 0) {
                for (BluetoothDevice device : pairedDevices) {
                    foundBtDevices.add(device.getAddress());
                    addBluetoothItemToUI(device.getName() != null ? device.getName() : "Unknown Device", device, true);
                }
            }
        } catch (Exception e) {
        }

        if (getCurrentFocus() == null && containerBtItems.getChildCount() > 0)
            containerBtItems.getChildAt(0).requestFocus();

        if (ba.isDiscovering())
            ba.cancelDiscovery();
        ba.startDiscovery();
    }

    // 💡 페어링 상태(isPaired)를 파라미터로 받아서 색상과 아이콘을 바꾸는 함수
    private void addBluetoothItemToUI(String name, final BluetoothDevice device, boolean isPaired) {
        String prefix = isPaired ? "✔ [PAIRED] " : "🎧 ";
        final Button btnDevice = createListButton(prefix + name);

        if (isPaired) {
            btnDevice.setTextColor(0xFF00FF00); // 💡 페어링된 기기도 초록색!
            btnDevice.setTypeface(null, android.graphics.Typeface.BOLD); // 💡 굵은 글씨!
        }

        btnDevice.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickFeedback();
                Toast.makeText(MainActivity.this, "Pairing/Connecting: " + device.getName(), Toast.LENGTH_SHORT).show();
                try {
                    Method method = device.getClass().getMethod("createBond", (Class[]) null);
                    method.invoke(device, (Object[]) null);
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Pairing failed.", Toast.LENGTH_SHORT).show();
                }
            }
        });
        containerBtItems.addView(btnDevice);
    }

    private void startWifiScan() {
        WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        boolean isOn = wm != null && wm.isWifiEnabled();
        updateWifiUI(null);

        if (isOn) {
            btnScanWifi.setText("Scanning...");
            foundWifiNetworks.clear();
            // 💡 무조건 최상단 전원 버튼으로 포커스 강제 이동!
            if (containerWifiItems.getChildCount() > 0)
                containerWifiItems.getChildAt(0).requestFocus();
            wm.startScan();
        } else {
            btnScanWifi.setText("Wi-Fi is OFF");
            // 💡 무조건 최상단 전원 버튼으로 포커스 강제 이동!
            if (containerWifiItems.getChildCount() > 0)
                containerWifiItems.getChildAt(0).requestFocus();
        }
    }

    private void updateWifiUI(List<ScanResult> results) {
        final WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        boolean isOn = false;
        String statusText = "OFF";

        if (wm != null) {
            int state = wm.getWifiState();
            if (state == WifiManager.WIFI_STATE_ENABLED) {
                isOn = true;
                statusText = "ON";
            } else if (state == WifiManager.WIFI_STATE_ENABLING || state == WifiManager.WIFI_STATE_DISABLING) {
                statusText = "Wait...";
            }
        }

        View existingToggle = containerWifiItems.findViewById(999992);
        if (existingToggle == null) {
            final LinearLayout btnToggle = createSettingRow("Wi-Fi Power", statusText);
            btnToggle.setId(999992);
            btnToggle.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    clickFeedback();
                    if (wm != null) {
                        boolean isCurrentlyOn = wm.isWifiEnabled();
                        if (isCurrentlyOn) {
                            Toast.makeText(MainActivity.this, "Turning Wi-Fi OFF...", Toast.LENGTH_SHORT).show();
                            wm.setWifiEnabled(false);
                        } else {
                            Toast.makeText(MainActivity.this, "Turning Wi-Fi ON...", Toast.LENGTH_SHORT).show();
                            wm.setWifiEnabled(true);
                        }
                        TextView tvRight = (TextView) btnToggle.getChildAt(1);
                        tvRight.setText("Wait...");
                        if (!btnToggle.hasFocus())
                            tvRight.setTextColor(0xFFFFFF00);
                    }
                }
            });
            containerWifiItems.addView(btnToggle, 0);

            if (btnScanWifi.getParent() != null) {
                ((android.view.ViewGroup) btnScanWifi.getParent()).removeView(btnScanWifi);
            }
            containerWifiItems.addView(btnScanWifi);
        } else {
            LinearLayout btnToggle = (LinearLayout) existingToggle;
            TextView tvRight = (TextView) btnToggle.getChildAt(1);
            tvRight.setText(statusText);
            if (!btnToggle.hasFocus()) {
                if (statusText.equals("ON"))
                    tvRight.setTextColor(0xFFFFFFFF);
                else if (statusText.equals("OFF"))
                    tvRight.setTextColor(0xFF888888);
                else
                    tvRight.setTextColor(0xFFFFFFFF);
            }
            for (int i = containerWifiItems.getChildCount() - 1; i > 0; i--) {
                View v = containerWifiItems.getChildAt(i);
                if (v != btnScanWifi) {
                    containerWifiItems.removeViewAt(i);
                }
            }
        }

        if (!isOn)
            return;

        if (results != null) {
            foundWifiNetworks.clear();
            WifiManager manager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            WifiInfo wifiInfo = manager.getConnectionInfo();
            String connectedSSID = "";
            if (wifiInfo != null && wifiInfo.getSSID() != null) {
                connectedSSID = wifiInfo.getSSID().replace("\"", "");
            }

            // 🚀 1순위: 현재 연결된 와이파이를 가장 먼저 찾아서 최상단에 배치!
            for (ScanResult result : results) {
                if (result.SSID != null && !result.SSID.isEmpty() && !foundWifiNetworks.contains(result.SSID)) {
                    if (result.SSID.equals(connectedSSID)) {
                        foundWifiNetworks.add(result.SSID);
                        addWifiItemToUI(result.SSID, result.capabilities, true);
                    }
                }
            }

            // 🚀 2순위: 연결되지 않은 나머지 잡다한 와이파이들을 그 밑으로 나열
            for (ScanResult result : results) {
                if (result.SSID != null && !result.SSID.isEmpty() && !foundWifiNetworks.contains(result.SSID)) {
                    foundWifiNetworks.add(result.SSID);
                    addWifiItemToUI(result.SSID, result.capabilities, false);
                }
            }
        }
    }

    // 💡 연결 상태(isConnected)를 파라미터로 직접 전달받도록 개조된 함수
    private void addWifiItemToUI(final String ssid, String capabilities, final boolean isConnected) {
        final boolean isOpen = !capabilities.contains("WPA") && !capabilities.contains("WEP");
        String lockIcon = isOpen ? "📶 " : "🔒 ";

        // 연결된 기기 앞에는 투박한 글씨 대신 애플처럼 예쁜 체크마크(✔) 부여!
        String prefix = isConnected ? "✔ " : "";

        Button btnWifi = createListButton(prefix + lockIcon + ssid);

        if (isConnected) {
            btnWifi.setTextColor(0xFF00FF00); // 눈에 확 띄는 초록색!
            btnWifi.setTypeface(null, android.graphics.Typeface.BOLD); // 굵은 글씨로 강조!
        }

        btnWifi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickFeedback();
                if (isConnected) {
                    Toast.makeText(MainActivity.this, "Already connected.", Toast.LENGTH_SHORT).show();
                    return;
                }

                WifiManager manager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                boolean isSaved = false;
                int savedNetId = -1;
                try {
                    List<WifiConfiguration> configuredNetworks = manager.getConfiguredNetworks();
                    if (configuredNetworks != null) {
                        for (WifiConfiguration conf : configuredNetworks) {
                            if (conf.SSID != null && conf.SSID.equals("\"" + ssid + "\"")) {
                                isSaved = true;
                                savedNetId = conf.networkId;
                                break;
                            }
                        }
                    }
                } catch (Exception e) {
                }

                if (isSaved && savedNetId != -1) {
                    Toast.makeText(MainActivity.this, "Connecting to saved network...", Toast.LENGTH_SHORT).show();
                    manager.disconnect();
                    manager.enableNetwork(savedNetId, true);
                    manager.reconnect();
                } else {
                    targetWifiSsid = ssid;
                    isTargetWifiOpen = isOpen;
                    changeScreen(STATE_WIFI_KEYBOARD);
                }
            }
        });
        containerWifiItems.addView(btnWifi);
    }

    private void createCategoryHeader(String title) {
        TextView tv = new TextView(this);
        tv.setTypeface(ThemeManager.getCustomFont(), android.graphics.Typeface.BOLD);
        tv.setText(title);
        // 💡 하늘색을 빼고, 애플 스타일의 은은한 반투명 흰색 & 굵은 글씨로 변경!
        tv.setTextColor(0xBBFFFFFF);
        tv.setTextSize(14);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        tv.setPadding(10, 30, 10, 5);
        containerSettingsItems.addView(tv);
    }

    private LinearLayout createSettingRow(String leftText, String rightText) {
        final LinearLayout layout = new LinearLayout(this);
        layout.setSoundEffectsEnabled(false);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setFocusable(true);
        layout.setPadding(20, 15, 20, 15);

        // 🚀 [수정 완료] 단색 덮어쓰기(setBackgroundColor)를 삭제하고, 둥글기가 적용된 배경만 입힙니다!
        layout.setBackground(createButtonBackground(ThemeManager.getListButtonNormalBg()));

        TextView tvLeft = new TextView(this);
        tvLeft.setTypeface(ThemeManager.getCustomFont(), android.graphics.Typeface.NORMAL);
        tvLeft.setText(leftText);
        tvLeft.setTextColor(ThemeManager.getTextColorPrimary());
        tvLeft.setTextSize(18);
        tvLeft.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));

        TextView tvRight = new TextView(this);
        tvRight.setTypeface(ThemeManager.getCustomFont(), android.graphics.Typeface.BOLD);
        tvRight.setText(rightText);
        tvRight.setTextSize(18);
        tvRight.setTypeface(null, android.graphics.Typeface.BOLD);
        tvRight.setGravity(android.view.Gravity.RIGHT);
        tvRight.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        if (rightText.equals("ON") || rightText.equals("ONE") || rightText.equals("ALL"))
            tvRight.setTextColor(ThemeManager.getTextColorPrimary());
        else
            tvRight.setTextColor(ThemeManager.getTextColorSecondary());

        layout.addView(tvLeft);
        layout.addView(tvRight);

        layout.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    // 🚀 포커스 상태 둥근 배경 적용! (단색 덮어쓰기 제거)
                    layout.setBackground(createButtonBackground(ThemeManager.getListButtonFocusedBg()));
                    ((TextView) layout.getChildAt(0)).setTextColor(ThemeManager.getListButtonFocusedTextColor());
                    ((TextView) layout.getChildAt(1)).setTextColor(ThemeManager.getListButtonFocusedTextColor());

                    if (currentScreenState == STATE_SETTINGS) {
                        int idx = containerSettingsItems.indexOfChild(layout);
                        if (idx != -1) lastSettingsFocusIndex = idx;
                    }
                } else {
                    // 🚀 일반 상태 둥근 배경 적용! (단색 덮어쓰기 제거)
                    layout.setBackground(createButtonBackground(ThemeManager.getListButtonNormalBg()));
                    ((TextView) layout.getChildAt(0)).setTextColor(ThemeManager.getTextColorPrimary());

                    TextView rightTv = (TextView) layout.getChildAt(1);
                    String currentText = rightTv.getText().toString();
                    if (currentText.equals("ON") || currentText.equals("ONE") || currentText.equals("ALL"))
                        rightTv.setTextColor(ThemeManager.getTextColorPrimary());
                    else
                        rightTv.setTextColor(ThemeManager.getTextColorSecondary());
                }
            }
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 2, 0, 2);
        layout.setLayoutParams(lp);

        return layout;
    }
    private Button createListButton(String text) {
        final Button btn = new Button(this);

        // 🚀 [수정 완료] 단색 덮어쓰기(setBackgroundColor)를 삭제하고, 둥글기가 적용된 배경만 입힙니다!
        btn.setBackground(createButtonBackground(ThemeManager.getListButtonNormalBg()));
        btn.setTypeface(ThemeManager.getCustomFont(), android.graphics.Typeface.NORMAL);
        btn.setSoundEffectsEnabled(false);
        btn.setText(text);
        btn.setTextSize(18);
        btn.setTextColor(ThemeManager.getTextColorPrimary());

        btn.setGravity(android.view.Gravity.LEFT | android.view.Gravity.CENTER_VERTICAL);
        btn.setPadding(20, 10, 10, 10);
        btn.setFocusable(true);
        btn.setSingleLine(true);

        btn.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    // 🚀 포커스 상태 둥근 배경 적용! (단색 덮어쓰기 제거)
                    btn.setBackground(createButtonBackground(ThemeManager.getListButtonFocusedBg()));
                    btn.setTextColor(ThemeManager.getListButtonFocusedTextColor());
                    showFastScrollLetter(((Button) v).getText().toString());

                } else {
                    // 🚀 일반 상태 둥근 배경 적용! (단색 덮어쓰기 제거)
                    btn.setBackground(createButtonBackground(ThemeManager.getListButtonNormalBg()));
                    btn.setTextColor(ThemeManager.getTextColorPrimary());
                }
            }
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 2, 0, 2);
        btn.setLayoutParams(lp);

        return btn;
    }
    private void buildSettingsUI() {
        final int targetFocusIndex = lastSettingsFocusIndex;
        containerSettingsItems.removeAllViews();

        // createCategoryHeader("━ QUICK SETTINGS ━");

        final LinearLayout btnShuffle = createSettingRow("Shuffle Mode", isShuffleMode ? "ON" : "OFF");
        btnShuffle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickFeedback();
                isShuffleMode = !isShuffleMode;
                TextView tvStatus = (TextView) btnShuffle.getChildAt(1);
                tvStatus.setText(isShuffleMode ? "ON" : "OFF");
                updatePlayerStatusIndicators();
                try {
                    prefs.edit().putBoolean("shuffle", isShuffleMode).commit();
                } catch (Exception e) {
                }

                if (!currentPlaylist.isEmpty() && !originalPlaylist.isEmpty()) {
                    File currentSong = currentPlaylist.get(currentIndex);
                    if (isShuffleMode) {
                        java.util.Collections.shuffle(currentPlaylist);
                    } else {
                        currentPlaylist.clear();
                        currentPlaylist.addAll(originalPlaylist);
                    }
                    currentIndex = currentPlaylist.indexOf(currentSong);
                    if (currentIndex == -1)
                        currentIndex = 0;
                }
            }
        });
        containerSettingsItems.addView(btnShuffle);

        final LinearLayout btnRepeat = createSettingRow("Repeat Mode", getRepeatModeText(repeatMode));
        btnRepeat.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickFeedback();
                repeatMode = (repeatMode + 1) % 3;
                TextView tvStatus = (TextView) btnRepeat.getChildAt(1);
                tvStatus.setText(getRepeatModeText(repeatMode));
                updatePlayerStatusIndicators();
                try {
                    prefs.edit().putInt("repeat_mode", repeatMode).commit();
                } catch (Exception e) {
                }
            }
        });
        containerSettingsItems.addView(btnRepeat);

        // 💡 [EQ 메뉴 추가] 이퀄라이저 프리셋 순환 버튼
        final LinearLayout btnEq = createSettingRow("Equalizer (EQ)", eqPresetNames.get(currentEqPresetIndex));
        btnEq.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickFeedback();
                if (eqPresetNames.size() > 1) {
                    currentEqPresetIndex = (currentEqPresetIndex + 1) % eqPresetNames.size();
                    ((TextView) btnEq.getChildAt(1)).setText(eqPresetNames.get(currentEqPresetIndex));
                    try {
                        prefs.edit().putInt("eq_preset", currentEqPresetIndex).commit();
                    } catch (Exception e) {
                    }

                    // 재생 중이라면 즉시 EQ를 변경합니다!
                    if (equalizer != null) {
                        try {
                            equalizer.usePreset((short) currentEqPresetIndex);
                        } catch (Exception e) {
                        }
                    }
                } else {
                    Toast.makeText(MainActivity.this, "This device does not support EQ presets.", Toast.LENGTH_SHORT)
                            .show();
                }
            }
        });
        containerSettingsItems.addView(btnEq);

        final LinearLayout btnSound = createSettingRow("Button Sound", isSoundEffectEnabled ? "ON" : "OFF");
        btnSound.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isSoundEffectEnabled = !isSoundEffectEnabled;
                applySoundSetting(); // 💡 [여기 추가] 사용자가 누르는 즉시 시스템 음소거 제어
                clickFeedback();
                TextView tvStatus = (TextView) btnSound.getChildAt(1);
                tvStatus.setText(isSoundEffectEnabled ? "ON" : "OFF");
                try {
                    prefs.edit().putBoolean("sound", isSoundEffectEnabled).commit();
                } catch (Exception e) {
                }
            }
        });
        containerSettingsItems.addView(btnSound);

        final LinearLayout btnVibrate = createSettingRow("Button Vibrate", isVibrationEnabled ? "ON" : "OFF");
        btnVibrate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isVibrationEnabled = !isVibrationEnabled;
                clickFeedback();
                TextView tvStatus = (TextView) btnVibrate.getChildAt(1);
                tvStatus.setText(isVibrationEnabled ? "ON" : "OFF");
                try {
                    prefs.edit().putBoolean("vibrate", isVibrationEnabled).commit();
                } catch (Exception e) {
                }
            }
        });
        containerSettingsItems.addView(btnVibrate);

        final LinearLayout btnScreenOffCtrl = createSettingRow("Screen-Off Control",
                isScreenOffControlEnabled ? "ON" : "OFF");
        btnScreenOffCtrl.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickFeedback();
                isScreenOffControlEnabled = !isScreenOffControlEnabled;
                TextView tvStatus = (TextView) btnScreenOffCtrl.getChildAt(1);
                tvStatus.setText(isScreenOffControlEnabled ? "ON" : "OFF");
                try {
                    prefs.edit().putBoolean("screen_off_control", isScreenOffControlEnabled).commit();
                } catch (Exception e) {
                }
            }
        });
        containerSettingsItems.addView(btnScreenOffCtrl);
// 🚀 [수정된 테마 설정 버튼]
        final LinearLayout btnTheme = createSettingRow("App Theme", ThemeManager.getCurrentTheme().name);
        btnTheme.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickFeedback();
                // 누르면 순환하지 않고, 전체 테마 리스트 화면으로 이동합니다!
                buildThemeSelectorUI();
            }
        });
        containerSettingsItems.addView(btnTheme);
        final LinearLayout btnTimeout = createSettingRow("Screen Timeout", TIMEOUT_NAMES[currentTimeoutIndex]);
        btnTimeout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickFeedback();
                currentTimeoutIndex = (currentTimeoutIndex + 1) % TIMEOUT_VALUES.length;
                ((TextView) btnTimeout.getChildAt(1)).setText(TIMEOUT_NAMES[currentTimeoutIndex]);
                try {
                    Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT,
                            TIMEOUT_VALUES[currentTimeoutIndex]);
                } catch (Exception e) {
                }
                try {
                    prefs.edit().putInt("timeout_idx", currentTimeoutIndex).commit();
                } catch (Exception e) {
                }
            }
        });
        containerSettingsItems.addView(btnTimeout);

        // createCategoryHeader("━ SYSTEM MENUS ━");

        LinearLayout btnPowerOff = createSettingRow("Power Off (Shutdown)", "〉 ");
        btnPowerOff.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickFeedback();
                new AlertDialog.Builder(MainActivity.this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                        .setTitle("Power Off")
                        .setMessage("Do you want to shut down the device?")
                        .setPositiveButton("Shut Down", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                try {
                                    Process proc = Runtime.getRuntime().exec(new String[] { "su", "-c", "reboot -p" });
                                    proc.waitFor();
                                } catch (Exception e) {
                                    try {
                                        Intent intent = new Intent("android.intent.action.ACTION_REQUEST_SHUTDOWN");
                                        intent.putExtra("android.intent.extra.KEY_CONFIRM", false);
                                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                        startActivity(intent);
                                    } catch (Exception ex) {
                                        Toast.makeText(MainActivity.this, "⚠️System security prevents powering off directly from the app.",
                                                Toast.LENGTH_LONG).show();
                                    }
                                }
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        });
        containerSettingsItems.addView(btnPowerOff);
        // 🚀 [추가] 락박스(Rockbox) OS로 재부팅하는 버튼
       /* LinearLayout btnRockbox = createSettingRow("Reboot to Rockbox", "〉 ");
        btnRockbox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickFeedback();
                new AlertDialog.Builder(MainActivity.this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                        .setTitle("Reboot to Rockbox")
                        .setMessage("Do you want to reboot into Rockbox OS?")
                        .setPositiveButton("Reboot", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                try {
                                    Toast.makeText(MainActivity.this, "Rebooting to Rockbox...", Toast.LENGTH_SHORT)
                                            .show();

                                    // 💡 락박스 진입 명령어 실행 (기기 파티션 구조에 따라 다를 수 있습니다)
                                    // 기본적으로 대부분의 듀얼 부팅 기기는 아래 명령어 중 하나를 사용합니다.
                                    Runtime.getRuntime().exec(new String[] { "su", "-c", "reboot alternate" });

                                    // 만약 위 명령어로 일반 안드로이드 재부팅이 된다면, 아래 명령어의 주석(//)을 해제하고 시도해 보세요.
                                    // Runtime.getRuntime().exec(new String[]{"su", "-c", "reboot recovery"});

                                } catch (Exception e) {
                                    Toast.makeText(MainActivity.this, "Failed: Root access required.",
                                            Toast.LENGTH_SHORT).show();
                                }
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        });
        containerSettingsItems.addView(btnRockbox);
        */
        LinearLayout btnServerMenu = createSettingRow("Web Server (PC Upload)", "〉 ");
        btnServerMenu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                changeScreen(STATE_WEBSERVER);
                clickFeedback();
            }
        });
        containerSettingsItems.addView(btnServerMenu);

        LinearLayout btnWifiMenu = createSettingRow("Wi-Fi Setup", "〉 ");
        btnWifiMenu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                changeScreen(STATE_WIFI);
                clickFeedback();
            }
        });
        containerSettingsItems.addView(btnWifiMenu);
// 🚀 [추가 1] 인터넷에서 앨범 아트 및 곡 정보 자동 검색 켜기/끄기
        final LinearLayout btnAutoFetch = createSettingRow("Auto Fetch Album Art", isAutoFetchEnabled ? "ON" : "OFF");
        btnAutoFetch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickFeedback();
                isAutoFetchEnabled = !isAutoFetchEnabled;
                ((TextView) btnAutoFetch.getChildAt(1)).setText(isAutoFetchEnabled ? "ON" : "OFF");
                try {
                    prefs.edit().putBoolean("auto_fetch", isAutoFetchEnabled).commit();
                } catch (Exception e) {}
            }
        });
        containerSettingsItems.addView(btnAutoFetch);



        LinearLayout btnBtMenu = createSettingRow("Bluetooth Setup", "〉 ");
        btnBtMenu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                changeScreen(STATE_BLUETOOTH);
                clickFeedback();
            }
        });
        containerSettingsItems.addView(btnBtMenu);

        LinearLayout btnBrightMenu = createSettingRow("Display Brightness", "〉 ");
        btnBrightMenu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                changeScreen(STATE_BRIGHTNESS);
                clickFeedback();
            }
        });
        containerSettingsItems.addView(btnBrightMenu);

        LinearLayout btnStorageMenu = createSettingRow("Storage Info", "〉 ");
        btnStorageMenu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                changeScreen(STATE_STORAGE);
                clickFeedback();
            }
        });
        containerSettingsItems.addView(btnStorageMenu);

        LinearLayout btnBg = createSettingRow("Change Background", "〉 ");
        btnBg.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickFeedback();
                isPickingBackground = true;
                currentBrowserMode = BROWSER_FOLDER;
                changeScreen(STATE_BROWSER);
                Toast.makeText(MainActivity.this, "Select a JPG/PNG image", Toast.LENGTH_SHORT).show();
            }
        });
        containerSettingsItems.addView(btnBg);

        // 🚀 [여기에 새로 추가!] 지정한 커스텀 배경을 해제하고 원래대로 돌리는 버튼
        LinearLayout btnClearBg = createSettingRow("Clear Custom Background", "〉 ");
        btnClearBg.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickFeedback();
                if (prefs.contains("bg_path")) {
                    prefs.edit().remove("bg_path").commit(); // 기억해둔 배경 경로를 영구 삭제!
                    Toast.makeText(MainActivity.this, "Custom background cleared.", Toast.LENGTH_SHORT).show();

                    // 삭제하자마자 화면을 즉시 갱신하여 앨범 아트(블러) 모드로 부드럽게 복귀시킵니다.
                    updateMainMenuBackground();
                } else {
                    Toast.makeText(MainActivity.this, "No custom background set.", Toast.LENGTH_SHORT).show();
                }
            }
        });
        containerSettingsItems.addView(btnClearBg);
// 🚀 [추가 2] 기기에 쌓인 앨범 아트 캐시 파일들 한 번에 지우기 (용량 확보)
        LinearLayout btnClearCache = createSettingRow("Clear Album Art Cache", "〉 ");
        btnClearCache.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickFeedback();
                new AlertDialog.Builder(MainActivity.this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                        .setTitle("Clear Cache")
                        .setMessage("Delete all downloaded album covers to free up storage space?")
                        .setPositiveButton("Clear", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                try {
                                    File coverFolder = new File("/storage/sdcard0/Y1_Covers");
                                    int count = 0;
                                    if (coverFolder.exists()) {
                                        File[] files = coverFolder.listFiles();
                                        if (files != null) {
                                            for (File f : files) {
                                                if (f.isFile() && f.delete()) count++;
                                            }
                                        }
                                    }
                                    Toast.makeText(MainActivity.this, "Deleted " + count + " cover images.", Toast.LENGTH_SHORT).show();

                                    // 메인 화면에 남아있는 이미지를 기본 아이콘으로 초기화합니다.
                                    ivAlbumArt.setImageResource(R.drawable.default_album);
                                    ivPlayerBgBlur.setImageResource(0);
                                    lastAlbumArtBytes = null;
                                    updateMainMenuBackground();
                                    refreshNowPlayingPreview();
                                } catch (Exception e) {
                                    Toast.makeText(MainActivity.this, "Failed to clear cache.", Toast.LENGTH_SHORT).show();
                                }
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        });
        containerSettingsItems.addView(btnClearCache);
        LinearLayout btnTime = createSettingRow("Date & Time Settings", "〉");
        btnTime.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickFeedback();

                // 시스템 시간을 먼저 읽어와서 임시 변수에 저장합니다.
                java.util.Calendar c = java.util.Calendar.getInstance();
                dtYear = c.get(java.util.Calendar.YEAR);
                dtMonth = c.get(java.util.Calendar.MONTH) + 1;
                dtDay = c.get(java.util.Calendar.DAY_OF_MONTH);
                dtHour = c.get(java.util.Calendar.HOUR_OF_DAY);
                dtMinute = c.get(java.util.Calendar.MINUTE);

                // 우리가 새로 만든 예쁜 리스트 화면을 띄웁니다!
                buildDateTimeUI();
            }
        });
        containerSettingsItems.addView(btnTime);

        // 🚀 [수정] 오염되지 않은 안전한 백업 인덱스(targetFocusIndex)를 사용하여 정확한 위치로 강제 이동!
        containerSettingsItems.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (targetFocusIndex >= 0 && targetFocusIndex < containerSettingsItems.getChildCount()) {
                    View target = containerSettingsItems.getChildAt(targetFocusIndex);
                    target.requestFocus();

                    // 스크롤 뷰가 해당 버튼 위치를 찾아서 화면을 쫙 내려주도록 강제 명령!
                    if (containerSettingsItems.getParent() instanceof android.widget.ScrollView) {
                        ((android.widget.ScrollView) containerSettingsItems.getParent()).requestChildFocus(containerSettingsItems, target);
                    }

                    // 이동을 마친 후 변수 상태를 일치시켜 줍니다.
                    lastSettingsFocusIndex = targetFocusIndex;
                } else if (containerSettingsItems.getChildCount() > 1) {
                    containerSettingsItems.getChildAt(1).requestFocus();
                }
            }
        }, 50);
    } // buildSettingsUI 함수 끝/ buildSettingsUI 함수 끝

    private boolean isAudioFile(File f) {
        if (f == null || !f.isFile())
            return false;
        String name = f.getName().toLowerCase();
        return name.endsWith(".mp3") || name.endsWith(".flac") || name.endsWith(".wav") || name.endsWith(".ogg")
                || name.endsWith(".m4a") || name.endsWith(".aac") || name.endsWith(".ape") || name.endsWith(".wma");
    }

    private boolean isApkFile(File f) {
        if (f == null || !f.isFile())
            return false;
        return f.getName().toLowerCase().endsWith(".apk");
    }

    private boolean isImageFile(File f) {
        if (f == null || !f.isFile())
            return false;
        String name = f.getName().toLowerCase();
        return name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png");
    }

    // 💡 하위 폴더까지 뒤져서 음악 파일의 '경로'만 모두 수집해 오는 함수
    private void collectAudioFiles(File file, List<String> paths) {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File f : files) {
                    collectAudioFiles(f, paths); // 폴더면 파고들기
                }
            }
        } else if (isAudioFile(file)) {
            paths.add(file.getAbsolutePath()); // 음악 파일이면 명단에 추가!
        }
    }

    // 💡 1. 안드로이드 스캐너를 버리고, 앱이 직접 MP3 태그를 추출하여 분류하는 함수!
    private void buildCustomLibrary(File folder) {
        File[] files = folder.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    buildCustomLibrary(f); // 폴더면 끝까지 파고듭니다.
                } else if (isAudioFile(f)) {

                    if (blacklist.contains(f.getAbsolutePath()))
                        continue;
                    try {
                        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
                        java.io.FileInputStream fis = new java.io.FileInputStream(f);
                        mmr.setDataSource(fis.getFD());

                        String title = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
                        String artist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
                        String album = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);

                        if (title == null || title.isEmpty())
                            title = f.getName();
                        if (artist == null || artist.isEmpty())
                            artist = "Unknown Artist";
                        if (album == null || album.isEmpty())
                            album = "Unknown Album";

                        customLibrary.add(new SongItem(f, title, artist, album));

                        fis.close();
                        mmr.release();
                    } catch (Exception e) {
                    }
                }
            }
        }
    }

    // 💡 2. 라이브러리 메인 라우터 (자체 스캔 버튼 적용)
    private void buildFileBrowserUI() {
        containerBrowserItems.removeAllViews();

        if (isPickingBackground || currentBrowserMode == BROWSER_FOLDER) {
            buildFolderBrowserUI();
            return;
        }

        if (currentBrowserMode == BROWSER_ROOT) {
            tvBrowserPath.setText("Library: Main Menu");

            Button btnFolder = createListButton("📁 Folders");
            btnFolder.setOnClickListener(v -> {
                clickFeedback();
                currentBrowserMode = BROWSER_FOLDER;
                buildFileBrowserUI();
            });
            containerBrowserItems.addView(btnFolder);

            Button btnArtist = createListButton("👤 Artists");
            btnArtist.setOnClickListener(v -> {
                clickFeedback();
                currentBrowserMode = BROWSER_ARTISTS;
                buildVirtualCategories("ARTIST");
            });
            containerBrowserItems.addView(btnArtist);

            Button btnAlbum = createListButton("💿 Albums");
            btnAlbum.setOnClickListener(v -> {
                clickFeedback();
                currentBrowserMode = BROWSER_ALBUMS;
                buildVirtualCategories("ALBUM");
            });
            containerBrowserItems.addView(btnAlbum);

            Button btnAll = createListButton("🎵 All Songs");
            btnAll.setOnClickListener(v -> {
                clickFeedback();
                currentBrowserMode = BROWSER_VIRTUAL_SONGS;
                virtualQueryType = "ALL";
                buildVirtualSongs();
            });
            containerBrowserItems.addView(btnAll);

            // 🚀 시스템을 거치지 않는 '앱 자체 스캔 엔진' 버튼!
            Button btnScan = createListButton(isCustomScanning ? "⏳ Scanning Media..." : "🔄 Scan Media Library");
            btnScan.setTextColor(isCustomScanning ? 0xFF000000 : 0xFFFFFFFF);
            btnScan.setOnClickListener(v -> {
                clickFeedback();
                if (isCustomScanning)
                    return;

                isCustomScanning = true;
                btnScan.setText("⏳ Scanning Media...");
                btnScan.setTextColor(0xFF000000);

                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        customLibrary.clear();
                        buildCustomLibrary(rootFolder);

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                isCustomScanning = false;
                                Toast.makeText(MainActivity.this,
                                                "Scan Complete! " + customLibrary.size() + " songs found.", Toast.LENGTH_SHORT)
                                        .show();
                                if (currentScreenState == STATE_BROWSER) {
                                    if (currentBrowserMode == BROWSER_ROOT) {
                                        buildFileBrowserUI();
                                    } else if (currentBrowserMode == BROWSER_ARTISTS) {
                                        buildVirtualCategories("ARTIST");
                                    } else if (currentBrowserMode == BROWSER_ALBUMS) {
                                        buildVirtualCategories("ALBUM");
                                    } else if (currentBrowserMode == BROWSER_VIRTUAL_SONGS) {
                                        buildVirtualSongs();
                                    }
                                }
                            }
                        });
                    }
                }).start();
            });
            containerBrowserItems.addView(btnScan);
            if (containerBrowserItems.getChildCount() > 0)
                containerBrowserItems.getChildAt(0).requestFocus();
        }
    }

    // 💡 3. 자체 DB에서 아티스트/앨범 카테고리 추출
    private void buildVirtualCategories(final String type) {
        containerBrowserItems.removeAllViews();
        tvBrowserPath.setText("Library: " + type + "s");

        List<String> categories = new ArrayList<>();
        for (SongItem song : customLibrary) {
            String val = type.equals("ARTIST") ? song.artist : song.album;
            if (!categories.contains(val))
                categories.add(val);
        }
        java.util.Collections.sort(categories);

        for (final String name : categories) {
            Button btn = createListButton((type.equals("ARTIST") ? "👤 " : "💿 ") + name);
            btn.setOnClickListener(v -> {
                clickFeedback();
                virtualQueryType = type;
                virtualQueryValue = name;
                currentBrowserMode = BROWSER_VIRTUAL_SONGS;
                buildVirtualSongs();
            });
            containerBrowserItems.addView(btn);
        }
        if (containerBrowserItems.getChildCount() > 0)
            containerBrowserItems.getChildAt(0).requestFocus();
    }

    // 💡 4. 자체 DB에서 선택한 곡들만 뽑아내는 함수
    private void buildVirtualSongs() {
        containerBrowserItems.removeAllViews();
        tvBrowserPath.setText("Library: " + (virtualQueryType.equals("ALL") ? "All Songs" : virtualQueryValue));

        virtualSongList.clear();
        for (SongItem song : customLibrary) {
            if (virtualQueryType.equals("ALL") ||
                    (virtualQueryType.equals("ARTIST") && song.artist.equals(virtualQueryValue)) ||
                    (virtualQueryType.equals("ALBUM") && song.album.equals(virtualQueryValue))) {

                virtualSongList.add(song.file);
                final int index = virtualSongList.size() - 1;
                Button btn = createListButton("🎵 " + song.title);
                btn.setOnClickListener(v -> {
                    clickFeedback();
                    playTrackList(virtualSongList, index);
                });
                containerBrowserItems.addView(btn);
            }
        }
        if (containerBrowserItems.getChildCount() > 0)
            containerBrowserItems.getChildAt(0).requestFocus();
    }

    private void buildFolderBrowserUI() {
        containerBrowserItems.removeAllViews();
        tvBrowserPath.setText("Path: " + currentFolder.getAbsolutePath().replace("/storage/sdcard0", ""));
        File[] files = currentFolder.listFiles();

        if (files == null || files.length == 0) {
            Button btnEmpty = createListButton(
                    files == null ? "⚠️ USB Disconnect Required (Tap to go back)" : "📂 Empty Folder (Tap to go back)");
            btnEmpty.setTextColor(0xFFFF5555);
            btnEmpty.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    clickFeedback();
                    if (currentFolder.getAbsolutePath().equals(rootFolder.getAbsolutePath())) {
                        isPickingBackground = false;
                        changeScreen(STATE_MENU);
                    } else {
                        currentFolder = currentFolder.getParentFile();
                        buildFileBrowserUI();
                    }
                }
            });
            containerBrowserItems.addView(btnEmpty);
            return;
        }

        List<File> folders = new ArrayList<File>();
        List<File> audioFiles = new ArrayList<File>();
        List<File> apkFiles = new ArrayList<File>();
        List<File> imageFiles = new ArrayList<File>();

        for (File f : files) {
            if (f.isDirectory())
                folders.add(f);
            else if (isPickingBackground && isImageFile(f))
                imageFiles.add(f);
            else if (!isPickingBackground && isAudioFile(f))
                audioFiles.add(f);
            else if (!isPickingBackground && isApkFile(f))
                apkFiles.add(f);
        }
// 🚀 [여기에 10줄 새로 추가!!] 수집된 파일과 폴더들을 이름순(알파벳 A-Z 대소문자 구분 없이)으로 정렬합니다!
        java.util.Comparator<File> fileSorter = new java.util.Comparator<File>() {
            @Override
            public int compare(File f1, File f2) {
                return f1.getName().compareToIgnoreCase(f2.getName());
            }
        };
        java.util.Collections.sort(folders, fileSorter);
        java.util.Collections.sort(audioFiles, fileSorter);
        java.util.Collections.sort(apkFiles, fileSorter);
        java.util.Collections.sort(imageFiles, fileSorter);
        // 🚀 (추가 끝)
        for (final File folder : folders) {
            Button b = createListButton("📁 " + folder.getName());
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    clickFeedback();
                    currentFolder = folder;
                    buildFileBrowserUI();
                }
            });
            containerBrowserItems.addView(b);
        }

        if (isPickingBackground) {
            for (final File img : imageFiles) {
                Button b = createListButton("🖼 " + img.getName());
                b.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        clickFeedback();
                        try {
                            prefs.edit().putString("bg_path", img.getAbsolutePath()).commit();
                        } catch (Exception e) {
                        }

                        updateMainMenuBackground(); // 💡 선택 즉시 블러 처리해서 메인 화면에 적용

                        Toast.makeText(MainActivity.this, "Background Applied!", Toast.LENGTH_SHORT).show();
                        isPickingBackground = false;
                        changeScreen(STATE_MENU);
                    }
                });
                containerBrowserItems.addView(b);
            }
        } else {
            for (final File apk : apkFiles) {
                Button b = createListButton("📦 [INSTALL] " + apk.getName());
                b.setTextColor(0xFF00FFFF);
                b.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        clickFeedback();
                        installApk(apk);
                    }
                });
                containerBrowserItems.addView(b);
            }
            for (final File audio : audioFiles) {
                Button b = createListButton("🎵 " + audio.getName());
                b.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        clickFeedback();
                        setupFolderPlaylist(audio);
                    }
                });
                containerBrowserItems.addView(b);
            }
        }
        if (containerBrowserItems.getChildCount() > 0)
            containerBrowserItems.getChildAt(0).requestFocus();
    }

    private void installApk(File apkFile) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.fromFile(apkFile), "application/vnd.android.package-archive");
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Install Failed.", Toast.LENGTH_SHORT).show();
        }
    }

    private void playTrackList(List<File> playlist, int startIndex) {
        originalPlaylist.clear();
        originalPlaylist.addAll(playlist);
        currentPlaylist.clear();
        currentPlaylist.addAll(playlist);

        if (!playlist.isEmpty()) {
            File currentSong = originalPlaylist.get(startIndex);
            if (isShuffleMode) {
                java.util.Collections.shuffle(currentPlaylist);
                currentIndex = currentPlaylist.indexOf(currentSong);
                if (currentIndex == -1)
                    currentIndex = 0;
            } else {
                currentIndex = startIndex;
            }
        } else {
            currentIndex = 0;
        }

        prepareMusicTrack(currentIndex);
        try {
            if (mediaPlayer != null) {
                mediaPlayer.start();
                isPausedByHand = false;

            }
        } catch (Exception e) {
        }
        updatePlayerUI();
        changeScreen(STATE_PLAYER);
    }

    // 💡 2. 기존 폴더 방식의 플레이리스트 생성기 (playTrackList를 부르도록 개조됨)
    private void setupFolderPlaylist(File selectedFile) {
        List<File> list = new ArrayList<>();
        File[] files = currentFolder.listFiles();
        int matchIndex = 0;
        if (files != null) {
            for (File f : files) {
                if (isAudioFile(f)) {
                    list.add(f);
                    if (f.getAbsolutePath().equals(selectedFile.getAbsolutePath()))
                        matchIndex = list.size() - 1;
                }
            }
        }
        playTrackList(list, matchIndex);
    }

    private void prepareMusicTrack(int index) {
        if (currentPlaylist.isEmpty())
            return;
        final File track = currentPlaylist.get(index);
        // 🚀 [추가된 부분] 손상된 파일 방어막: 파일이 없거나 용량이 1KB(1024 bytes) 미만인 껍데기 파일일 경우
        if (!track.exists() || track.length() < 1024) {
            tvPlayerTitle.setText("Corrupted File");
            tvPlayerArtist.setText("Skipping...");
            ivAlbumArt.setImageResource(R.drawable.default_album);

            // 시스템이 뻗기 전에 경고창을 띄우고 1.5초 뒤에 다음 곡으로 자동으로 부드럽게 넘겨버립니다!
            Toast.makeText(this, "Corrupted file detected. Skipping...", Toast.LENGTH_SHORT).show();
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    nextTrack();
                }
            }, 1500);
            return;
        }
        tvPlayerTitle.setText(track.getName());
        tvPlayerArtist.setText("Loading...");
        ivAlbumArt.setImageResource(R.drawable.default_album);
        ivPlayerBgBlur.setImageResource(0);
        playerProgress.setProgress(0);
        tvPlayerTimeCurrent.setText("00:00");
        tvPlayerTimeTotal.setText("00:00");

        try {
            MediaMetadataRetriever mmr = new MediaMetadataRetriever();
            java.io.FileInputStream fisMmr = new java.io.FileInputStream(track);
            mmr.setDataSource(fisMmr.getFD());


            // 1. 파일에서 메타데이터(태그) 추출
            String t = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
            String a = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
            lastAlbumArtBytes = mmr.getEmbeddedPicture();

            String safeFileName = track.getName().replace(".mp3", "").replace(".flac", "").replace(".wav", "").replace(".m4a", "");
            File coverFile = new File("/storage/sdcard0/Y1_Covers", safeFileName + ".jpg");

            if (prefs.contains("meta_title_" + track.getAbsolutePath())) {
                t = prefs.getString("meta_title_" + track.getAbsolutePath(), t);
                a = prefs.getString("meta_artist_" + track.getAbsolutePath(), a);
            }

            // 🚀 [핵심 판단 로직] 이 파일에 정말 멀쩡한 태그(가수+제목)가 들어있는지 검사합니다.
            boolean hasValidTags = (t != null && !t.trim().isEmpty() && a != null && !a.trim().isEmpty() && !a.equalsIgnoreCase("Unknown Artist"));

            // 제목 화면에 표시
            if (t != null && !t.trim().isEmpty()) tvPlayerTitle.setText(t);
            else tvPlayerTitle.setText(safeFileName);

            // 가수 화면에 표시
            if (a != null && !a.trim().isEmpty()) tvPlayerArtist.setText(a);
            else tvPlayerArtist.setText("Unknown Artist");

            // 2. 앨범 아트 세팅 및 인터넷 검색
            if (lastAlbumArtBytes != null) {
                // 원본 파일에 앨범 아트가 있으면 그대로 사용
                updateMainMenuBackground();
                refreshNowPlayingPreview();
                try {
                    android.graphics.BitmapFactory.Options optsCenter = new android.graphics.BitmapFactory.Options();
                    optsCenter.inSampleSize = 2;
                    android.graphics.Bitmap bmpCenter = android.graphics.BitmapFactory.decodeByteArray(lastAlbumArtBytes, 0, lastAlbumArtBytes.length, optsCenter);
                    ivAlbumArt.setImageBitmap(bmpCenter);
                    try {
                        int centerX = bmpCenter.getWidth() / 2;
                        int centerY = (int)(bmpCenter.getHeight() * 0.8);
                        currentAlbumColor = bmpCenter.getPixel(centerX, centerY) | 0xFF000000;
                    } catch (Exception e) {
                        currentAlbumColor = ThemeManager.getListButtonFocusedBg() | 0xFF000000;
                    }
                    android.graphics.BitmapFactory.Options optsBg = new android.graphics.BitmapFactory.Options();
                    optsBg.inSampleSize = 4;
                    android.graphics.Bitmap sourceBg = android.graphics.BitmapFactory.decodeByteArray(lastAlbumArtBytes, 0, lastAlbumArtBytes.length, optsBg);
                    android.graphics.Bitmap blurredBg = applyGaussianBlur(sourceBg);
                    ivPlayerBgBlur.setImageBitmap(blurredBg);
                    if (sourceBg != blurredBg) sourceBg.recycle();
                } catch (Throwable e) {}

            } else if (coverFile.exists()) {
                // 다운받아둔 앨범 아트가 있으면 사용
                applyCachedCoverArt(coverFile.getAbsolutePath());

            } else {

                ivAlbumArt.setImageResource(R.drawable.default_album);
                currentAlbumColor = ThemeManager.getListButtonFocusedBg() | 0xFF000000;
                ivPlayerBgBlur.setImageResource(0); // 뒷배경 블러 비우기
                updateMainMenuBackground();
                refreshNowPlayingPreview();
                // 없으면 인터넷에서 검색 출동!
                if (isAutoFetchEnabled) {
                    android.net.wifi.WifiManager wm = (android.net.wifi.WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                    if (wm != null && wm.isWifiEnabled() && wm.getConnectionInfo().getNetworkId() != -1) {

                        String searchQuery = "";
                        // 🚀 [스마트 쿼리 생성] 멀쩡한 태그가 있다면 그걸 합쳐서(가수+제목) 무조건 100% 일치하는 곡을 찾습니다!
                        if (hasValidTags) {
                            searchQuery = a + " " + t;
                        } else {
                            searchQuery = safeFileName.replace("-", " ").replace("_", " ");
                        }

                        // 태그가 있다는 사실과 기존 태그 내용을 같이 넘겨서, 정보가 덮어씌워지지 않게 막습니다.
                        fetchTrackInfoFromInternet(track, searchQuery, hasValidTags, t, a);
                    }
                }
            }

            fisMmr.close();
            mmr.release();
        } catch (Throwable t) {
        }

        try {
            // 🚀 [가장 우아하고 근본적인 해결책]
            // 1. 플레이어를 리셋하기 전에 현재 사용 중인 '오디오 회선 번호(Session ID)'를 기억해 둡니다.
            int previousSessionId = 0;
            if (mediaPlayer != null) {
                try { previousSessionId = mediaPlayer.getAudioSessionId(); } catch (Exception e) {}
            }

            // 🚀 [추가] 시각화 엔진(Visualizer)은 안드로이드 내부 버그로 인해 살려둔 채로 3곡 이상 넘기면
            // 메모리가 터져서 시스템을 다운시켜버립니다(3곡 프리징의 원인!).
            // 따라서 곡이 바뀔 때는 반드시 '완전히 파괴(release)' 해 주어야 합니다.
            if (audioVisualizer != null) {
                try {
                    audioVisualizer.setEnabled(false);
                    audioVisualizer.release(); // 🚀 숨통을 완전히 끊어버립니다!
                    audioVisualizer = null;
                } catch (Exception e) {}
            }

            if (mediaPlayer == null) {
                mediaPlayer = new MediaPlayer();
            } else {
                mediaPlayer.reset();
            }

            // 2. 리셋된 플레이어에 방금 기억해둔 회선 번호를 다시 연결해 줍니다!
            // 이렇게 하면 이퀄라이저가 유지한 회선에 다시 탑승하게 되어, 볼륨이 리셋되는 버그가 원천 차단됩니다.
            if (previousSessionId != 0) {
                try { mediaPlayer.setAudioSessionId(previousSessionId); } catch (Exception e) {}
            }

            // 🚀 [버그 수정] 권한 누락으로 인해 음악 재생이 통째로 취소되는 것을 막는 방어막!
            try {
                mediaPlayer.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
            } catch (Exception e) { }
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);

            mediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    String err = "Audio Error: what=" + what + " extra=" + extra;
                    try {
                        java.io.File log = new java.io.File("/storage/sdcard0/y1_audio_error.txt");
                        java.io.FileOutputStream fos = new java.io.FileOutputStream(log, true);
                        fos.write((new java.util.Date().toString() + " - " + err + " File: " + track.getName() + "\n")
                                .getBytes());
                        fos.close();
                    } catch (Exception e) {
                    }
                    return true;
                }
            });

            if (currentFileInputStream != null) {
                try {
                    currentFileInputStream.close();
                } catch (Exception e) {
                }
            }
            currentFileInputStream = new java.io.FileInputStream(track);

            mediaPlayer.setDataSource(currentFileInputStream.getFD());
            mediaPlayer.prepare();
            setupVisualizer();
            // 🚀 [근본적 해결책 1] 이퀄라이저를 매번 부수지 않고 한 번 만든 것을 재사용합니다! (DAC 리셋 방지)
            try {
                if (equalizer == null) {
                    equalizer = new Equalizer(0, mediaPlayer.getAudioSessionId());
                    equalizer.setEnabled(true);
                }
                if (currentEqPresetIndex < equalizer.getNumberOfPresets()) {
                    equalizer.usePreset((short) currentEqPresetIndex);
                }
            } catch (Exception e) {
            }

            tvPlayerTimeTotal.setText(formatTime(mediaPlayer.getDuration()));
            String currentTrackNum = String.format(Locale.US, "%02d", index + 1);
            String totalTrackNum = String.format(Locale.US, "%02d", currentPlaylist.size());
            tvPlayerTrackCount.setText(currentTrackNum + " / " + totalTrackNum);

            mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    try {
                        if (repeatMode == 1) { // Repeat One
                            mediaPlayer.seekTo(0);
                            mediaPlayer.start();
                        } else if (repeatMode == 2) { // Repeat All
                            nextTrack();
                        } else { // Repeat Off
                            if (currentIndex < currentPlaylist.size() - 1) {
                                nextTrack();
                            } else {
                                // Reached the end, stop playback
                                currentIndex = 0;
                                prepareMusicTrack(currentIndex);
                                isPausedByHand = true;
                                updatePlayerUI();
                            }
                        }
                    } catch (Exception e) {
                    }
                }
            });
        } catch (Throwable e) {
            tvPlayerTitle.setText("Load Failed: " + track.getName());
        }
    }
    // 💡 [수정] 액자 전체를 숨기도록 개조
    private void toggleVisualizer() {
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, 101);
                Toast.makeText(this, "Please grant Audio Permission first!", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        isVisualizerShowing = !isVisualizerShowing;
        View albumContainer = (View) ivAlbumArt.getParent(); // 🚀 앨범 아트의 부모(액자)를 통째로 숨깁니다!

        if (isVisualizerShowing) {
            albumContainer.setVisibility(View.GONE);
            visualizerView.setVisibility(View.VISIBLE);
            visualizerView.invalidate(); // 애니메이션 킥스타트
            if (audioVisualizer != null) audioVisualizer.setEnabled(true);
        } else {
            visualizerView.setVisibility(View.GONE);
            albumContainer.setVisibility(View.VISIBLE);
            if (audioVisualizer != null) audioVisualizer.setEnabled(false);
        }
    }
    // 💡 [수정] 오디오 엔진에 빨대를 꽂아 주파수 데이터를 빼오는 함수
    private void setupVisualizer() {
        try {
            // 🚀 [완벽 해결] 매번 새롭게 엔진을 만들어서 장착합니다! (메모리 누수 원천 차단)
            if (audioVisualizer != null) {
                audioVisualizer.setEnabled(false);
                audioVisualizer.release();
                audioVisualizer = null;
            }

            audioVisualizer = new android.media.audiofx.Visualizer(mediaPlayer.getAudioSessionId());
            audioVisualizer.setCaptureSize(android.media.audiofx.Visualizer.getCaptureSizeRange()[1]);
            audioVisualizer.setDataCaptureListener(new android.media.audiofx.Visualizer.OnDataCaptureListener() {
                @Override
                public void onWaveFormDataCapture(android.media.audiofx.Visualizer visualizer, byte[] waveform, int samplingRate) {}

                @Override
                public void onFftDataCapture(android.media.audiofx.Visualizer visualizer, byte[] fft, int samplingRate) {
                    if (isVisualizerShowing && visualizerView != null && visualizerView.getVisibility() == View.VISIBLE) {
                        visualizerView.updateVisualizer(fft, currentAlbumColor);
                    }
                }
            }, android.media.audiofx.Visualizer.getMaxCaptureRate() / 2, false, true);

            if (isVisualizerShowing) {
                audioVisualizer.setEnabled(true);
            }
        } catch (Exception e) {}
    }
    private String getRepeatModeText(int mode) {
        switch (mode) {
            case 1:
                return "ONE";
            case 2:
                return "ALL";
            default:
                return "OFF";
        }
    }

    //    private void updatePlayerStatusIndicators() {
//        try {
//            if (tvPlayerShuffleStatus != null) {
//                tvPlayerShuffleStatus.setVisibility(isShuffleMode ? View.VISIBLE : View.GONE);
//            }
//            if (tvPlayerRepeatStatus != null) {
//                if (repeatMode == 1) {
//                    tvPlayerRepeatStatus.setText("REPEAT ONE");
//                    tvPlayerRepeatStatus.setVisibility(View.VISIBLE);
//                } else if (repeatMode == 2) {
//                    tvPlayerRepeatStatus.setText("REPEAT ALL");
//                    tvPlayerRepeatStatus.setVisibility(View.VISIBLE);
//                } else {
//                    tvPlayerRepeatStatus.setVisibility(View.GONE);
//                }
//            }
//        } catch (Exception e) {
//        }
//    }
    private void updatePlayerStatusIndicators() {
        try {
// 1. 셔플 아이콘 세팅
            if (ivPlayerShuffleStatus != null) {
                if (isShuffleMode) {
                    ivPlayerShuffleStatus.setImageResource(R.drawable.ic_shuffle);
                    ivPlayerShuffleStatus.setVisibility(View.VISIBLE);
                } else {
                    ivPlayerShuffleStatus.setVisibility(View.GONE);
                }
            }
            if (ivPlayerRepeatStatus != null) {
                if (repeatMode == 1) { // 한 곡 반복
                    ivPlayerRepeatStatus.setImageResource(R.drawable.ic_repeat_one);
                    ivPlayerRepeatStatus.setVisibility(View.VISIBLE);
                } else if (repeatMode == 2) { // 전곡 반복
                    ivPlayerRepeatStatus.setImageResource(R.drawable.ic_repeat);
                    ivPlayerRepeatStatus.setVisibility(View.VISIBLE);
                } else { // 반복 꺼짐
                    ivPlayerRepeatStatus.setVisibility(View.GONE);
                }
            }
        } catch (Exception e) {
        }
    }
    private void updatePlayerUI() {
        try {
            if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                ivAlbumArt.setAlpha(1.0f);
                ivPauseOverlay.setVisibility(View.GONE);
                progressHandler.post(updateProgressTask);

                // 🚀 [스크린 오프 완벽 제어 3단계] 재생 상태(PLAYING)를 시스템에 신고하여 제어권 유지
                if (mediaSession != null && android.os.Build.VERSION.SDK_INT >= 21) {
                    PlaybackState state = new PlaybackState.Builder()
                            .setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS)
                            .setState(PlaybackState.STATE_PLAYING, mediaPlayer.getCurrentPosition(), 1.0f)
                            .build();
                    mediaSession.setPlaybackState(state);
                }
            } else {
                ivAlbumArt.setAlpha(0.4f);
                ivPauseOverlay.setVisibility(View.VISIBLE);
                progressHandler.removeCallbacks(updateProgressTask);

                // 일시정지 상태(PAUSED) 신고
                if (mediaSession != null && android.os.Build.VERSION.SDK_INT >= 21) {
                    PlaybackState state = new PlaybackState.Builder()
                            .setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS)
                            .setState(PlaybackState.STATE_PAUSED, mediaPlayer == null ? 0 : mediaPlayer.getCurrentPosition(), 1.0f)
                            .build();
                    mediaSession.setPlaybackState(state);
                }
            }
            updatePlayerStatusIndicators();
        } catch (Exception e) {
        }
    }
    private void playOrPauseMusic() {
        try {
            if (mediaPlayer == null || currentPlaylist.isEmpty())
                return;
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
                isPausedByHand = true;
            } else {
                mediaPlayer.start();
                isPausedByHand = false;

            }
            updatePlayerUI();
        } catch (Throwable e) {
        }
    }
    private void nextTrack() {
        lastTrackChangeTime = System.currentTimeMillis();
        if (currentPlaylist.isEmpty())
            return;
        currentIndex = (currentIndex + 1) % currentPlaylist.size();
        prepareMusicTrack(currentIndex);
        if (!isPausedByHand) {
            try {
                mediaPlayer.start();

               ;

                updatePlayerUI();
            } catch (Exception e) {
            }
        } else {
            updatePlayerUI();
        }
    }

    private void prevTrack() {
        lastTrackChangeTime = System.currentTimeMillis();
        if (currentPlaylist.isEmpty())
            return;
        currentIndex = (currentIndex - 1 + currentPlaylist.size()) % currentPlaylist.size();
        prepareMusicTrack(currentIndex);
        if (!isPausedByHand) {
            try {
                mediaPlayer.start();


                updatePlayerUI();
            } catch (Exception e) {
            }
        } else {
            updatePlayerUI();
        }
    }

    private void adjustVolume(boolean up) {
        int currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        int maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        if (up && currentVol < maxVol)
            currentVol++;
        else if (!up && currentVol > 0)
            currentVol--;
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, currentVol, 0);
        showDynamicVolumeOverlay();
    }

    private void showDynamicVolumeOverlay() {
        int currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        layoutVolumeOverlay.setVisibility(View.VISIBLE);
        volumeProgress.setProgress(currentVol);
        volumeHandler.removeCallbacks(hideVolumeTask);
        volumeHandler.postDelayed(hideVolumeTask, 2000);
    }

    private String formatTime(int ms) {
        int s = (ms / 1000) % 60;
        int m = (ms / (1000 * 60)) % 60;
        return String.format(Locale.US, "%02d:%02d", m, s);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        boolean isScreenOn = true;
        try {
            if (android.os.Build.VERSION.SDK_INT >= 20)
                isScreenOn = pm.isInteractive();
            else
                isScreenOn = pm.isScreenOn();
        } catch (Exception e) {
        }

        boolean isWakingUp = !isScreenOn || ((event.getFlags() & KeyEvent.FLAG_WOKE_HERE) != 0)
                || (System.currentTimeMillis() - lastScreenOnTime < 500);

        if (isWakingUp) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                return true;
            }

            if (isScreenOffControlEnabled && currentScreenState == STATE_PLAYER) {
                if (keyCode == 21) {
                    // 🚀 방어막: 곡 넘김 직후 0.3초(300ms) 안에는 볼륨 조절을 차단합니다!
                    if (System.currentTimeMillis() - lastTrackChangeTime > 300) {
                        adjustVolume(false);
                        clickFeedback();
                    }
                    return true;
                }
                if (keyCode == 22) {
                    if (System.currentTimeMillis() - lastTrackChangeTime > 300) {
                        adjustVolume(true);
                        clickFeedback();
                    }
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS || keyCode == 88) {
                    prevTrack();
                    clickFeedback();
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_MEDIA_NEXT || keyCode == 87) {
                    nextTrack();
                    clickFeedback();
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || keyCode == 85 || keyCode == 86) {
                    playOrPauseMusic();
                    clickFeedback();
                    return true;
                }
            }
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || keyCode == 85 || keyCode == KeyEvent.KEYCODE_MEDIA_STOP
                || keyCode == 86) {
            if (event.getRepeatCount() == 0) {
                playOrPauseMusic();
            }
            return true;
        }

        // 🚀 [수정] 플레이어 화면(STATE_PLAYER) 제한을 완전히 삭제했습니다!
        // 이제 메인 화면, 브라우저, 설정 창 등 어느 화면에 있든 버튼(87, 88)을 누르면 즉시 곡이 넘어갑니다.
        if (keyCode == KeyEvent.KEYCODE_MEDIA_NEXT || keyCode == 87) {
            if (event.getRepeatCount() == 0) {
                clickFeedback();
                nextTrack();
            }
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS || keyCode == 88) {
            if (event.getRepeatCount() == 0) {
                clickFeedback();
                prevTrack();
            }
            return true;
        }

        if (currentScreenState == STATE_WIFI_KEYBOARD) {
            if (keyCode == 21) {
                keyboardIndex = (keyboardIndex - 1 + KEYBOARD_CHARS.length) % KEYBOARD_CHARS.length;
                updateKeyboardUI();
                clickFeedback();
                return true;
            }
            if (keyCode == 22) {
                keyboardIndex = (keyboardIndex + 1) % KEYBOARD_CHARS.length;
                updateKeyboardUI();
                clickFeedback();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                changeScreen(STATE_WIFI);
                clickFeedback();
                return true;
            }
            return true;
        }

        if (currentScreenState == STATE_PLAYER) {
            if (keyCode == 21) {
                adjustVolume(false);
                clickFeedback();
                return true;
            }
            if (keyCode == 22) {
                adjustVolume(true);
                clickFeedback();
                return true;
            }

            if (keyCode == KeyEvent.KEYCODE_BACK) {
                changeScreen(STATE_BROWSER);
                clickFeedback();
                return true;
            }
            return true;
        }

        if (currentScreenState == STATE_BRIGHTNESS) {
            if (keyCode == 21) {
                currentSystemBrightness = Math.max(10, currentSystemBrightness - 15);
                updateBrightness(currentSystemBrightness);
                clickFeedback();
                return true;
            }
            if (keyCode == 22) {
                currentSystemBrightness = Math.min(255, currentSystemBrightness + 15);
                updateBrightness(currentSystemBrightness);
                clickFeedback();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                changeScreen(STATE_SETTINGS);
                clickFeedback();
                return true;
            }
            return true;
        }

        if (currentScreenState == STATE_STORAGE) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                changeScreen(STATE_SETTINGS);
                clickFeedback();
                return true;
            }
            return true;
        }

        if (currentScreenState == STATE_WEBSERVER) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                clickFeedback();
                if (isServerRunning) {
                    new AlertDialog.Builder(MainActivity.this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                            .setTitle("Server is Running")
                            .setMessage(
                                    "The Web Server is still active. Do you want to shut it down completely and exit?")
                            .setPositiveButton("Stop Server (Exit)", new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    toggleWebServer();
                                    changeScreen(STATE_SETTINGS);
                                }
                            })
                            .setNegativeButton("Keep Running (Stay)", new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    changeScreen(STATE_SETTINGS);
                                }
                            })
                            .show();
                } else {
                    changeScreen(STATE_SETTINGS);
                }
                return true;
            }
        }

        if (currentScreenState == STATE_MENU || currentScreenState == STATE_BROWSER
                || currentScreenState == STATE_SETTINGS || currentScreenState == STATE_BLUETOOTH
                || currentScreenState == STATE_WIFI) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                clickFeedback();
                if (currentScreenState == STATE_BROWSER) {
                    if (isPickingBackground) {
                        if (currentFolder.getAbsolutePath().equals(rootFolder.getAbsolutePath())) {
                            isPickingBackground = false;
                            changeScreen(STATE_MENU);
                        } else {
                            currentFolder = currentFolder.getParentFile();
                            buildFileBrowserUI();
                        }
                    } else {
                        // 💡 라이브러리 뒤로가기 로직
                        if (currentBrowserMode == BROWSER_ROOT) {
                            changeScreen(STATE_MENU);
                        } else if (currentBrowserMode == BROWSER_FOLDER) {
                            if (currentFolder.getAbsolutePath().equals(rootFolder.getAbsolutePath())) {
                                currentBrowserMode = BROWSER_ROOT;
                                buildFileBrowserUI();
                            } else {
                                currentFolder = currentFolder.getParentFile();
                                buildFileBrowserUI();
                            }
                        } else if (currentBrowserMode == BROWSER_VIRTUAL_SONGS) {
                            currentBrowserMode = virtualQueryType.equals("ALL") ? BROWSER_ROOT
                                    : (virtualQueryType.equals("ARTIST") ? BROWSER_ARTISTS : BROWSER_ALBUMS);
                            if (currentBrowserMode == BROWSER_ROOT)
                                buildFileBrowserUI();
                            else
                                buildVirtualCategories(virtualQueryType);
                        } else {
                            currentBrowserMode = BROWSER_ROOT;
                            buildFileBrowserUI();
                        }
                    }
                } else if (currentScreenState == STATE_BLUETOOTH || currentScreenState == STATE_WIFI) {
                    changeScreen(STATE_SETTINGS);
                } else if (currentScreenState == STATE_SETTINGS) {
                    changeScreen(STATE_MENU);
                }
                return true;
            }

            View c = getCurrentFocus();
            if (c != null) {
                if (keyCode == 21) {
                    View n = c.focusSearch(View.FOCUS_UP);
                    if (n != null)
                        n.requestFocus();
                    clickFeedback();
                    return true;
                }
                if (keyCode == 22) {
                    View n = c.focusSearch(View.FOCUS_DOWN);
                    if (n != null)
                        n.requestFocus();
                    clickFeedback();
                    return true;
                }
            }
            return super.onKeyDown(keyCode, event);
        }

        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        boolean isScreenOn = true;
        try {
            if (android.os.Build.VERSION.SDK_INT >= 20)
                isScreenOn = pm.isInteractive();
            else
                isScreenOn = pm.isScreenOn();
        } catch (Exception e) {
        }

        boolean isWakingUp = !isScreenOn || ((event.getFlags() & KeyEvent.FLAG_WOKE_HERE) != 0)
                || (System.currentTimeMillis() - lastScreenOnTime < 500);

        if (isWakingUp) {
            return true;
        }

        // 💡 [핵심 차단 구역] 휠 조작(21, 22)이나 뒤로가기(BACK)를 '뗄 때'
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == 21 || keyCode == 22) {
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            try {
                handleCenterShortClick();
            } catch (Exception e) {
            }
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || keyCode == 85 || keyCode == 86
                || keyCode == KeyEvent.KEYCODE_MEDIA_NEXT || keyCode == 87 || keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS
                || keyCode == 88) {
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        clockHandler.removeCallbacks(clockTask);
        progressHandler.removeCallbacks(updateProgressTask);
        volumeHandler.removeCallbacks(hideVolumeTask);

        if (currentFileInputStream != null) {
            try {
                currentFileInputStream.close();
            } catch (Exception e) {
            }
        }

        // 💡 앱이 꺼질 때 엔진도 안전하게 종료
        if (equalizer != null) {
            equalizer.release();
            equalizer = null;
        }
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }

        // 🚀 [스크린 오프 완벽 제어 4단계] 앱 종료 시 권한 반납
        if (mediaSession != null && android.os.Build.VERSION.SDK_INT >= 21) {
            mediaSession.release();
        }

        unregisterReceiver(systemStatusReceiver);
    }


    // 💡 안드로이드 시스템 자체의 하드웨어 삑 소리 스트림을 직접 차단/허용하는 함수
    private void applySoundSetting() {
        try {
            if (audioManager != null) {
                audioManager.setStreamMute(AudioManager.STREAM_SYSTEM, !isSoundEffectEnabled);
            }
            // 💡 핵심: 기기 터치 패널의 하드웨어 삑 소리를 강제로 차단하는 시스템 설정 덮어쓰기!
            Settings.System.putInt(getContentResolver(), Settings.System.SOUND_EFFECTS_ENABLED,
                    isSoundEffectEnabled ? 1 : 0);
        } catch (Exception e) {
        }
    }

    // 💡 안드로이드 하드웨어 가속(RenderScript)을 이용한 고화질 가우시안 블러 함수!
    private Bitmap applyGaussianBlur(Bitmap original) {
        if (original == null)
            return null;
        try {
            Bitmap output = Bitmap.createBitmap(original.getWidth(), original.getHeight(), Bitmap.Config.ARGB_8888);
            RenderScript rs = RenderScript.create(this);
            ScriptIntrinsicBlur script = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs));
            Allocation inAlloc = Allocation.createFromBitmap(rs, original, Allocation.MipmapControl.MIPMAP_NONE,
                    Allocation.USAGE_SCRIPT);
            Allocation outAlloc = Allocation.createFromBitmap(rs, output);

            script.setRadius(25f); // 💡 블러 강도 설정 (0.0 ~ 25.0 범위, 25가 최대)
            script.setInput(inAlloc);
            script.forEach(outAlloc);
            outAlloc.copyTo(output);
            rs.destroy();

            return output;
        } catch (Exception e) {
            return original;
        }
    }

    // 💡 1. 날짜/시간 설정 메인 화면 (시간 오류 및 포커스 락 버그 완벽 수정 버전)
    private void buildDateTimeUI() {
        containerSettingsItems.removeAllViews();
        createCategoryHeader("━ SET DATE & TIME ━");

        final LinearLayout rowYear = createSettingRow("Year", String.valueOf(dtYear));
        rowYear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickFeedback();
                buildDateTimeSelectorUI("Year", 2020, 2035, dtYear);
            }
        });
        containerSettingsItems.addView(rowYear);

        final LinearLayout rowMonth = createSettingRow("Month", String.format(java.util.Locale.US, "%02d", dtMonth));
        rowMonth.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickFeedback();
                buildDateTimeSelectorUI("Month", 1, 12, dtMonth);
            }
        });
        containerSettingsItems.addView(rowMonth);

        final LinearLayout rowDay = createSettingRow("Day", String.format(java.util.Locale.US, "%02d", dtDay));
        rowDay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickFeedback();
                buildDateTimeSelectorUI("Day", 1, 31, dtDay);
            }
        });
        containerSettingsItems.addView(rowDay);

        final LinearLayout rowHour = createSettingRow("Hour (24H)", String.format(java.util.Locale.US, "%02d", dtHour));
        rowHour.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickFeedback();
                buildDateTimeSelectorUI("Hour", 0, 23, dtHour);
            }
        });
        containerSettingsItems.addView(rowHour);

        final LinearLayout rowMinute = createSettingRow("Minute", String.format(java.util.Locale.US, "%02d", dtMinute));
        rowMinute.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickFeedback();
                buildDateTimeSelectorUI("Minute", 0, 59, dtMinute);
            }
        });
        containerSettingsItems.addView(rowMinute);

        createCategoryHeader("━━━━━━━━━━━━━━");

        final Button btnApply = createListButton("✅ APPLY DATE & TIME");
        btnApply.setTextColor(0xFFFFFFFF);
        btnApply.setTypeface(null, android.graphics.Typeface.BOLD);
        btnApply.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickFeedback();
                try {
                    // 🚀 [시간 오류 영구 해결] 기기의 기존 타임존을 건드리지 않고, 시간을 설정합니다.
                    // 기존 안드로이드의 `date` 명령어는 기기에 내장된 쉘(Toolbox vs Toybox)에 따라 파싱 방식이 완전히 달라,
                    // 잘못된 포맷이 들어가면 무조건 1970년이나 1980년으로 초기화(리셋)해버리는 심각한 버그가 있습니다.
                    // 이를 완벽히 방지하기 위해, 하나의 포맷을 적용해본 후 ➡️ 제대로 연도/월/일이 적용되었는지 확인하고 ➡️ 실패했다면 다음 포맷을
                    // 시도하는 자동 검증(Self-Verifying) 스크립트를 작성합니다!

                    String cmd = "settings put global auto_time 0; settings put system auto_time 0; ";

                    // 목표 날짜를 YYYYMMDD 형태로 만듭니다 (검증용)
                    String targetYMD = String.format(java.util.Locale.US, "%04d%02d%02d", dtYear, dtMonth, dtDay);

                    // 포맷 1: 구형 안드로이드(Toolbox) 전용 포맷 -> YYYYMMDD.HHmmss
                    String dateToolbox = String.format(java.util.Locale.US, "%04d%02d%02d.%02d%02d%02d", dtYear,
                            dtMonth, dtDay, dtHour, dtMinute, 0);
                    // 포맷 2: POSIX 국제 표준 포맷 (Toybox/Busybox 호환) -> MMDDhhmmYYYY.ss
                    String datePosix = String.format(java.util.Locale.US, "%02d%02d%02d%02d%04d.00", dtMonth, dtDay,
                            dtHour, dtMinute, dtYear);
                    // 포맷 3: 최신 안드로이드(Toybox) 문자열 포맷 -> YYYY-MM-DD HH:MM:SS
                    String dateString = String.format(java.util.Locale.US, "%04d-%02d-%02d %02d:%02d:%02d", dtYear,
                            dtMonth, dtDay, dtHour, dtMinute, 0);

                    // 💡 자체 검증 쉘 스크립트:
                    // 1. Toolbox 포맷을 먼저 시도합니다. (Toybox 기기에서는 에러가 나거나 시간이 뒤틀립니다)
                    // 2. 적용된 시간을 즉시 확인하여 목표 날짜와 다르면(1970년 등으로 초기화되었으면) POSIX 포맷을 시도합니다.
                    // 3. 그래도 안 되면 문자열 포맷을 시도합니다.
                    String executeCmd = cmd +
                            "date -s " + dateToolbox + "; " +
                            "if [ \"$(date +%Y%m%d)\" != \"" + targetYMD + "\" ]; then " +
                            "  date " + datePosix + "; " +
                            "  if [ \"$(date +%Y%m%d)\" != \"" + targetYMD + "\" ]; then " +
                            "    date -s \"" + dateString + "\"; " +
                            "  fi; " +
                            "fi; " +
                            "hwclock -w; sync";

                    Process proc = Runtime.getRuntime().exec(new String[] { "su", "-c", executeCmd });
                    proc.waitFor(); // 💡 시스템에 시간이 완벽하게 적용될 때까지 잠깐 기다립니다.

                    // 시스템 전역에 시간이 변경되었음을 강제로 방송하여 메인 페이지 시계와 시스템 앱들을 동기화시킵니다.
                    sendBroadcast(new Intent(Intent.ACTION_TIME_CHANGED));

                    Toast.makeText(MainActivity.this, "Time applied successfully!", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Failed: Root access required.", Toast.LENGTH_SHORT).show();
                }

                // 🚀 [포커스 버그 해결 1] 오염된 인덱스를 'Date & Time Settings' 메뉴 위치(14번째 항목)로 강제 정화
                lastSettingsFocusIndex = 14;
                buildSettingsUI();

                // 🚀 [포커스 버그 해결 2] 50ms의 미세한 안전 딜레이를 주어 UI 가 완벽히 배치된 후 포커스를 확실히 꽂아줍니다.
                containerSettingsItems.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (containerSettingsItems != null && containerSettingsItems.getChildCount() > 0) {
                            containerSettingsItems.getChildAt(containerSettingsItems.getChildCount() - 1)
                                    .requestFocus();
                        }
                    }
                }, 50);
            }
        });
        containerSettingsItems.addView(btnApply);

        final Button btnCancel = createListButton("❌ CANCEL");
        btnCancel.setTextColor(0xFF888888);
        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickFeedback();

                // 취소하고 나갈 때도 인덱스를 안전하게 복구하고 포커스를 인위적으로 매핑합니다.
                lastSettingsFocusIndex = 14;
                buildSettingsUI();
                containerSettingsItems.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (containerSettingsItems != null && containerSettingsItems.getChildCount() > 0) {
                            containerSettingsItems.getChildAt(containerSettingsItems.getChildCount() - 1)
                                    .requestFocus();
                        }
                    }
                }, 50);
            }
        });
        containerSettingsItems.addView(btnCancel);

        if (containerSettingsItems.getChildCount() > 1)
            containerSettingsItems.getChildAt(1).requestFocus();
    }

    // 💡 2. 숫자(년/월/일/시/분) 선택용 세로 리스트 화면
    private void buildDateTimeSelectorUI(final String type, int min, int max, int currentValue) {
        containerSettingsItems.removeAllViews();
        createCategoryHeader("━ SELECT " + type.toUpperCase() + " ━");

        Button btnBack = createListButton("〈 CANCEL & BACK");
        btnBack.setTextColor(0xFF888888);
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickFeedback();
                buildDateTimeUI();
            }
        });
        containerSettingsItems.addView(btnBack);

        Button focusBtn = null;
        for (int i = min; i <= max; i++) {
            final int val = i;
            String displayVal = (type.equals("Minute") || type.equals("Hour") || type.equals("Month")
                    || type.equals("Day")) ? String.format(java.util.Locale.US, "%02d", val) : String.valueOf(val);
            Button btn = createListButton(displayVal);
            btn.setGravity(android.view.Gravity.CENTER); // 가운데 정렬로 예쁘게!

            btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    clickFeedback();
                    if (type.equals("Year"))
                        dtYear = val;
                    else if (type.equals("Month"))
                        dtMonth = val;
                    else if (type.equals("Day"))
                        dtDay = val;
                    else if (type.equals("Hour"))
                        dtHour = val;
                    else if (type.equals("Minute"))
                        dtMinute = val;
                    buildDateTimeUI(); // 선택하면 자동으로 이전 화면으로 복귀!
                }
            });
            containerSettingsItems.addView(btn);
            if (val == currentValue)
                focusBtn = btn;
        }

        // 현재 설정되어 있는 시간으로 포커스 자동 이동
        if (focusBtn != null)
            focusBtn.requestFocus();
        else if (containerSettingsItems.getChildCount() > 1)
            containerSettingsItems.getChildAt(1).requestFocus();
    }
    // 💡 [화면 꺼짐 전용 수신기] 화면이 꺼진 상태에서 시스템이 버튼 신호를 여기로 쏴줍니다!
    public static class MediaBtnReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_MEDIA_BUTTON.equals(intent.getAction()) && MainActivity.instance != null) {
                KeyEvent event = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);

                if (event != null && event.getAction() == KeyEvent.ACTION_DOWN) {
                    // 설정에서 스크린 오프가 꺼져있으면 무시합니다.
                    if (!MainActivity.instance.isScreenOffControlEnabled) return;

                    int keyCode = event.getKeyCode();

                    // ⏮ 이전 곡 버튼
                    if (keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS || keyCode == 88) {
                        MainActivity.instance.prevTrack();
                        MainActivity.instance.clickFeedback();
                    }
                    // ⏭ 다음 곡 버튼
                    else if (keyCode == KeyEvent.KEYCODE_MEDIA_NEXT || keyCode == 87) {
                        MainActivity.instance.nextTrack();
                        MainActivity.instance.clickFeedback();
                    }
                    // ⏯ 재생/일시정지 버튼
                    else if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || keyCode == 85 || keyCode == 86) {
                        MainActivity.instance.playOrPauseMusic();
                        MainActivity.instance.clickFeedback();
                    }
                    // 🔊 혹시 기기가 휠 조작(21, 22)을 미디어 신호로 보내줄 경우를 대비한 방어 코드
                    else if (keyCode == 21) {
                        MainActivity.instance.adjustVolume(false);
                        MainActivity.instance.clickFeedback();
                    }
                    else if (keyCode == 22) {
                        MainActivity.instance.adjustVolume(true);
                        MainActivity.instance.clickFeedback();
                    }
                }
            }
        }
    }
    // 💡 [수정] 메인 화면 미리보기도 재생 상태에 따라 아이콘을 똑똑하게 바꿉니다.
    private void refreshNowPlayingPreview() {
        if (btnNowPlaying != null && btnNowPlaying.hasFocus()) {
            if (currentPlaylist.isEmpty()) {
                ivMenuPreview.setImageResource(R.drawable.music_circle);
                if (tvMenuPreviewTitle != null && tvMenuPreviewArtist != null) {
                    tvMenuPreviewTitle.setVisibility(View.GONE);
                    tvMenuPreviewArtist.setVisibility(View.GONE);
                }
            } else {
                if (lastAlbumArtBytes != null && lastAlbumArtBytes.length > 0) {
                    try {
                        android.graphics.BitmapFactory.Options opts = new android.graphics.BitmapFactory.Options();
                        opts.inSampleSize = 2;
                        android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeByteArray(lastAlbumArtBytes, 0, lastAlbumArtBytes.length, opts);
                        ivMenuPreview.setImageBitmap(bmp);
                    } catch (Exception e) {
                        ivMenuPreview.setImageResource(R.drawable.default_album);
                    }
                } else {
                    ivMenuPreview.setImageResource(R.drawable.default_album);
                }

                if (tvMenuPreviewTitle != null && tvMenuPreviewArtist != null) {
                    tvMenuPreviewTitle.setVisibility(View.VISIBLE);
                    tvMenuPreviewArtist.setVisibility(View.VISIBLE);
                    tvMenuPreviewTitle.setText(tvPlayerTitle.getText());
                    tvMenuPreviewArtist.setText(tvPlayerArtist.getText());
                }
            }
        }
    }
    // 💡 [추가] 1. 인터넷에서 받아온 커버 이미지를 캐시 폴더에서 불러와 화면에 띄우는 함수
    private void applyCachedCoverArt(String imagePath) {
        try {
            // 중앙의 선명한 앨범 아트
            android.graphics.BitmapFactory.Options optsCenter = new android.graphics.BitmapFactory.Options();
            optsCenter.inSampleSize = 2;
            android.graphics.Bitmap bmpCenter = android.graphics.BitmapFactory.decodeFile(imagePath, optsCenter);
            ivAlbumArt.setImageBitmap(bmpCenter);

            // 🚀 [완벽 수정] 앨범 아트의 하단 중앙 색상을 스포이드로 정확히 뽑아냅니다!
            try {
                int centerX = bmpCenter.getWidth() / 2;
                int centerY = (int)(bmpCenter.getHeight() * 0.8); // 정중앙보다 약간 아래의 포인트 색상
                currentAlbumColor = bmpCenter.getPixel(centerX, centerY) | 0xFF000000;
            } catch (Exception e) {
                currentAlbumColor = ThemeManager.getListButtonFocusedBg() | 0xFF000000;
            }

            // 뒷배경 블러 처리
            android.graphics.BitmapFactory.Options optsBg = new android.graphics.BitmapFactory.Options();
            optsBg.inSampleSize = 4;
            android.graphics.Bitmap sourceBg = android.graphics.BitmapFactory.decodeFile(imagePath, optsBg);
            android.graphics.Bitmap blurredBg = applyGaussianBlur(sourceBg);
            ivPlayerBgBlur.setImageBitmap(blurredBg);
            if (sourceBg != blurredBg) sourceBg.recycle();

            // 메인 메뉴 배경도 연동하기 위해 파일 데이터를 byte[]로 변환해서 lastAlbumArtBytes에 집어넣습니다!
            java.io.File file = new java.io.File(imagePath);
            int size = (int) file.length();
            byte[] bytes = new byte[size];
            java.io.BufferedInputStream buf = new java.io.BufferedInputStream(new java.io.FileInputStream(file));
            buf.read(bytes, 0, bytes.length);
            buf.close();

            lastAlbumArtBytes = bytes;
            updateMainMenuBackground();
            refreshNowPlayingPreview();

        } catch (Exception e) {}
    }

    // 💡 [수정] 정밀 검색 및 기존 태그(가수/제목) 보호 기능이 추가된 Deezer 스크래핑 엔진
    private void fetchTrackInfoFromInternet(final File track, final String originalQuery, final boolean hasValidTags, final String origTitle, final String origArtist) {
        // 찌꺼기 텍스트 청소기
        final String cleanQuery = originalQuery
                .replaceAll("\\[.*?\\]", "")
                .replaceAll("\\(.*?\\)", "")
                .replaceAll("^[0-9\\s\\-]+", "")
                .replaceAll("\\s[0-9]{2}\\s", " ")
                .trim();

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, "🔍 Searching: " + cleanQuery, Toast.LENGTH_SHORT).show();
            }
        });

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String query = java.net.URLEncoder.encode(cleanQuery, "UTF-8");
                    String urlString = "http://api.deezer.com/search?q=" + query;
                    java.net.URL url = new java.net.URL(urlString);

                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(10000);
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");

                    int responseCode = conn.getResponseCode();
                    if (responseCode != 200) throw new Exception("HTTP Response Code: " + responseCode);

                    java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) response.append(line);
                    reader.close();

                    org.json.JSONObject jsonResponse = new org.json.JSONObject(response.toString());
                    org.json.JSONArray dataArray = jsonResponse.optJSONArray("data");

                    if (dataArray != null && dataArray.length() > 0) {
                        org.json.JSONObject trackInfo = dataArray.getJSONObject(0);

                        // 서버에서 가져온 가수와 제목
                        final String fetchedTitle = trackInfo.getString("title");
                        final String fetchedArtist = trackInfo.getJSONObject("artist").getString("name");

                        // 🚀 [태그 보호 장치] 파일에 원래 완벽한 태그가 있었다면 서버 정보를 무시하고 기존 것을 유지합니다!
                        final String finalTitle = hasValidTags ? origTitle : fetchedTitle;
                        final String finalArtist = hasValidTags ? origArtist : fetchedArtist;

                        String coverUrl = trackInfo.getJSONObject("album").getString("cover_xl").replace("https://", "http://");
                        java.net.URL imgUrl = new java.net.URL(coverUrl);
                        java.net.HttpURLConnection imgConn = (java.net.HttpURLConnection) imgUrl.openConnection();
                        java.io.InputStream in = imgConn.getInputStream();
                        final android.graphics.Bitmap coverBitmap = android.graphics.BitmapFactory.decodeStream(in);
                        in.close();

                        File coverFolder = new File("/storage/sdcard0/Y1_Covers");
                        if (!coverFolder.exists()) coverFolder.mkdirs();
                        String safeFileName = track.getName().replace(".mp3", "").replace(".flac", "");
                        final File coverFile = new File(coverFolder, safeFileName + ".jpg");
                        java.io.FileOutputStream fos = new java.io.FileOutputStream(coverFile);
                        coverBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, fos);
                        fos.close();

                        // 원래 태그가 없었을 때만! 우리가 찾아온 정보를 영구 저장소에 기록합니다.
                        if (!hasValidTags) {
                            prefs.edit()
                                    .putString("meta_title_" + track.getAbsolutePath(), finalTitle)
                                    .putString("meta_artist_" + track.getAbsolutePath(), finalArtist)
                                    .commit();
                        }

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(MainActivity.this, "✅ Album Art Updated!", Toast.LENGTH_SHORT).show();
                                if (currentPlaylist.get(currentIndex).getAbsolutePath().equals(track.getAbsolutePath())) {
                                    // 기존 태그가 없었을 때만 화면의 글씨를 바꿔줍니다. (있었다면 이미 정상 출력 중이므로 이미지 갈아끼우기만 실행)
                                    if (!hasValidTags) {
                                        tvPlayerTitle.setText(finalTitle);
                                        tvPlayerArtist.setText(finalArtist);
                                    }
                                    applyCachedCoverArt(coverFile.getAbsolutePath());
                                }
                            }
                        });
                    } else {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(MainActivity.this, "❌ No results found.", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "⚠️ Connection Error", Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        }).start();
    }
    // 💡 [추가] 구형 안드로이드의 잠든 최신 보안(TLS 1.2)을 강제로 깨우는 전용 소켓 팩토리
    private static class TLSSocketFactory extends javax.net.ssl.SSLSocketFactory {
        private javax.net.ssl.SSLSocketFactory internalSSLSocketFactory;

        public TLSSocketFactory() throws java.security.KeyManagementException, java.security.NoSuchAlgorithmException {
            javax.net.ssl.SSLContext context = javax.net.ssl.SSLContext.getInstance("TLS");
            context.init(null, null, null);
            internalSSLSocketFactory = context.getSocketFactory();
        }

        @Override public String[] getDefaultCipherSuites() { return internalSSLSocketFactory.getDefaultCipherSuites(); }
        @Override public String[] getSupportedCipherSuites() { return internalSSLSocketFactory.getSupportedCipherSuites(); }
        @Override public java.net.Socket createSocket() throws java.io.IOException { return enableTLSOnSocket(internalSSLSocketFactory.createSocket()); }
        @Override public java.net.Socket createSocket(java.net.Socket s, String host, int port, boolean autoClose) throws java.io.IOException { return enableTLSOnSocket(internalSSLSocketFactory.createSocket(s, host, port, autoClose)); }
        @Override public java.net.Socket createSocket(String host, int port) throws java.io.IOException, java.net.UnknownHostException { return enableTLSOnSocket(internalSSLSocketFactory.createSocket(host, port)); }
        @Override public java.net.Socket createSocket(String host, int port, java.net.InetAddress localHost, int localPort) throws java.io.IOException, java.net.UnknownHostException { return enableTLSOnSocket(internalSSLSocketFactory.createSocket(host, port, localHost, localPort)); }
        @Override public java.net.Socket createSocket(java.net.InetAddress host, int port) throws java.io.IOException { return enableTLSOnSocket(internalSSLSocketFactory.createSocket(host, port)); }
        @Override public java.net.Socket createSocket(java.net.InetAddress address, int port, java.net.InetAddress localAddress, int localPort) throws java.io.IOException { return enableTLSOnSocket(internalSSLSocketFactory.createSocket(address, port, localAddress, localPort)); }

        // 🚀 가장 핵심! 열리는 모든 소켓의 설정값을 TLSv1.2 로 강제 고정합니다.
        private java.net.Socket enableTLSOnSocket(java.net.Socket socket) {
            if (socket instanceof javax.net.ssl.SSLSocket) {
                ((javax.net.ssl.SSLSocket) socket).setEnabledProtocols(new String[] {"TLSv1.1", "TLSv1.2"});
            }
            return socket;
        }
    }
    // 💡 [수정] 속이 꽉 찬 리얼 파이(Pie) 차트 클래스
    public static class PieChartView extends View {
        private android.graphics.Paint paintBg;
        private android.graphics.Paint paintUsed;
        private float percentage = 0f;

        public PieChartView(Context context) {
            super(context);
            init();
        }

        private void init() {
            // 1. 남은 용량 (배경 원) - 은은한 반투명 흰색
            paintBg = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            paintBg.setStyle(android.graphics.Paint.Style.FILL); // 🚀 선(STROKE)에서 면(FILL)으로 변경!
            paintBg.setColor(0x33FFFFFF);

            // 2. 사용한 용량 (테마 색상 파이 조각)
            paintUsed = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            paintUsed.setStyle(android.graphics.Paint.Style.FILL); // 🚀 면(FILL)으로 변경!
        }

        public void setStorageData(long used, long total, int themeColor) {
            if (total > 0) percentage = (float) used / total;
            paintUsed.setColor(themeColor);
            invalidate();
        }

        @Override
        protected void onDraw(android.graphics.Canvas canvas) {
            super.onDraw(canvas);
            int width = getWidth();
            int height = getHeight();
            float padding = 10f;
            android.graphics.RectF rect = new android.graphics.RectF(padding, padding, width - padding, height - padding);

            // 360도 전체 원 그리기 (남은 용량 베이스)
            canvas.drawArc(rect, 0, 360, true, paintBg);

            // 사용된 용량만큼 파이 조각 덮어 그리기 (시작점 -90도는 12시 방향)
            float sweepAngle = percentage * 360f;
            canvas.drawArc(rect, -90, sweepAngle, true, paintUsed); // 🚀 useCenter를 true로 하여 꽉 찬 조각을 만듭니다.
        }
    }
    // 💡 [추가] 화면에 존재하는 모든 글씨를 찾아내 테마 폰트로 갈아입히는 재귀 엔진!
    private void applyFontToAllViews(android.view.ViewGroup parent, android.graphics.Typeface font) {
        if (font == null) return;
        for (int i = 0; i < parent.getChildCount(); i++) {
            android.view.View child = parent.getChildAt(i);

            // 1. 만약 폴더(레이아웃)라면 안쪽으로 파고듭니다.
            if (child instanceof android.view.ViewGroup) {
                applyFontToAllViews((android.view.ViewGroup) child, font);
            }
            // 2. 만약 글씨(TextView, Button 등)라면 폰트를 즉시 교체합니다.
            else if (child instanceof android.widget.TextView) {
                // 기존에 굵은 글씨(Bold) 설정이 되어있었다면 그 특성은 유지해 줍니다!
                android.graphics.Typeface current = ((android.widget.TextView) child).getTypeface();
                int style = android.graphics.Typeface.NORMAL;
                if (current != null) style = current.getStyle();

                ((android.widget.TextView) child).setTypeface(font, style);
            }
        }
    }
    // 💡 [완벽 수정] 60fps 부드러운 애니메이션과 높이 제한이 적용된 와이드 스펙트럼 뷰!
    public static class AudioVisualizerView extends View {
        private byte[] fftData;
        private float[] currentHeights; // 🚀 부드러운 움직임을 위한 이전 높이 기억 장치
        private android.graphics.Paint paint;
        private int barCount = 40; // 🚀 막대기 개수를 늘려서 옆으로 쫙 퍼지게!

        public AudioVisualizerView(Context context) {
            super(context);
            paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            paint.setStyle(android.graphics.Paint.Style.FILL);
            paint.setStrokeCap(android.graphics.Paint.Cap.ROUND);
            currentHeights = new float[barCount];
        }

        public void updateVisualizer(byte[] fft, int color) {
            this.fftData = fft;
            paint.setColor(color);
            // invalidate() 대신 onDraw 내부에서 무한 루프를 돌려 60fps를 방어합니다!
        }

        @Override
        protected void onDraw(android.graphics.Canvas canvas) {
            super.onDraw(canvas);

            int width = getWidth();
            int height = getHeight();
            float barWidth = width / (float) barCount;
            paint.setStrokeWidth(barWidth * 0.4f); // 🚀 막대기 두께를 얇고 세련되게 (40%)

            if (fftData != null) {
                for (int i = 0; i < barCount && (i * 2 + 2) < fftData.length; i++) {
                    byte rfk = fftData[i * 2 + 2];
                    byte ifk = fftData[i * 2 + 3];
                    float magnitude = (float) Math.hypot(rfk, ifk);

                    // 🚀 1. 높이 제한: 아무리 소리가 커도 화면 높이의 85%를 넘지 못하게 캡을 씌웁니다.
                    float targetHeight = Math.min(height * 0.85f, (magnitude * height) / 100f);

                    // 🚀 2. 부드러운 보간: 목표 지점까지 한 번에 점프하지 않고 15%씩 스무스하게 따라갑니다.
                    currentHeights[i] += (targetHeight - currentHeights[i]) * 0.15f;
                }
            }

            // 그려내기
            for (int i = 0; i < barCount; i++) {
                float x = i * barWidth + (barWidth / 2f);
                canvas.drawLine(x, height, x, height - currentHeights[i], paint);
            }

            // 🚀 3. 화면에 보일 때는 초당 60번(16ms) 강제 새로고침하여 버벅임을 없앱니다.
            if (getVisibility() == View.VISIBLE) {
                postInvalidateDelayed(16);
            }
        }
    }
}