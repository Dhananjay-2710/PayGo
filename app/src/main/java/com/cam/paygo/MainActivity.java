package com.cam.paygo;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.hardware.usb.UsbManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import androidx.activity.OnBackPressedCallback;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.cam.paygo.api.ApiConstants;
import com.cam.paygo.bank.ABPBank;
import com.cam.paygo.bank.BankResponseProcessor;
import com.cam.paygo.card.CardDetector;
import com.cam.paygo.constants.AppConstants;
import com.cam.paygo.constants.BankConstants;
import com.cam.paygo.constants.IntegrationConstants;
import com.cam.paygo.constants.JsonKeys;
import com.cam.paygo.constants.MqttConstants;
import com.cam.paygo.constants.StatusConstants;
import com.cam.paygo.constants.TxnConstants;
import com.cam.paygo.manager.AuthManager;
import com.cam.paygo.manager.HeartbeatManager;
import com.cam.paygo.manager.IntegrationModeStore;
import com.cam.paygo.manager.UartManager;
import com.cam.paygo.model.request.DeviceRequest;
import com.cam.paygo.model.request.LoginRequest;
import com.cam.paygo.model.response.DeviceResponse;
import com.cam.paygo.model.response.LoginResponse;
import com.cam.paygo.repository.AuthRepository;
import com.cam.paygo.repository.DeviceRepository;
import com.cam.paygo.utils.AppLogger;
import com.cam.paygo.utils.LogUploadWorker;
import com.google.android.material.navigation.NavigationView;
import com.pax.dal.IDAL;
import com.pax.dal.entity.ENavigationKey;
import com.pax.neptunelite.api.NeptuneLiteUser;

import org.json.JSONObject;

import com.cam.mqtt.MqttLog;
import com.cam.mqtt.MqttManager;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int AUTH_RETRY_BASE_MS = 5_000;
    private static final int AUTH_RETRY_MAX_MS = 60_000;
    private IDAL iDal = null;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private UartManager uartManager;
    private AlertDialog tapCardDialog;
    private TextView tvTapMessage;
    private ProgressBar loader;
    private Runnable timeoutRunnable;

    private CardDetector myCardDetector;
    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();
    private Context context;
    private ABPBank abpBank;
    private BankResponseProcessor bankResponseProcessor;
    private long topupAmount = 0;
    private String deviceSerialNumber = "";
    private String deviceModel = "";
    private String tranType = "";
    private String SOURCE_TXN_ID = "";
    private String OPERATOR_ORDER_ID = "";
    private String INVOICE_NO = "";
    private String RRN = "";
    private String IS_OFFLINE = "";
    private String PRINT_FLAG = "";
    private String SHIFT_NO = "";
    private String STATION_NAME = "";
    private String STATION_ID = "";
    private String GATENO = "";
    private String UDF1 = "";
    private String UDF2 = "";
    private String UDF3 = "";
    private String UDF4 = "";
    private String UDF5 = "";

    private Runnable paymentTimeoutRunnable;
    private Runnable paymentGraceTimeoutRunnable;
    private Runnable enquiryTimeoutRunnable;

    private static final long REQUEST_TIMEOUT = AppConstants.REQUEST_TIMEOUT_MS;
    private static final long PAYMENT_GRACE_TIMEOUT = AppConstants.PAYMENT_GRACE_TIMEOUT_MS;
    private static final long ENQUIRY_TIMEOUT = AppConstants.ENQUIRY_TIMEOUT_MS;

    private enum FlowState {
        IDLE,
        PAYMENT,
        ENQUIRY
    }

    private int clickCount = 0;
    private long firstClickTime = 0;
    private int authRetryDelayMs = AUTH_RETRY_BASE_MS;
    private boolean authBootstrapInProgress = false;
    private boolean networkCallbackRegistered = false;
    private Boolean lastNetworkAvailable = null;
    private final Runnable authRetryRunnable = this::initAuthFlow;
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;
    private MqttManager mqttManager;
    private AlertDialog mqttMessageDialog;
    /** True when the active bank sale was started from an MQTT card request. */
    private boolean mqttTxnPending = false;
    private String mqttRequestId = "";
    private String mqttReplyTopic = "";
    private String mqttAmount = "";
    private String mqttPhone = "";
    private String mqttTerminalId = "";

    private FlowState flowState = FlowState.IDLE;
    /** True from paymentLauncher.launch until ActivityResult returns — blocks a second bank Intent. */
    private boolean bankLaunchPending = false;
    /** Payment 65s elapsed while bank still in flight; start enquiry only after that result (if non-OK). */
    private boolean awaitingEnquiryAfterPaymentTimeout = false;
    /** True after hard abort APB timeout was already sent to TVM (ignore late bank result). */
    private boolean hardAbortSentToTvm = false;
    /** elapsedRealtime when the current payment bank Intent was launched. */
    private long paymentStartedAtMs = 0L;

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        hideSystemUI();
        setContentView(R.layout.activity_main);
        deviceSerialNumber = getDeviceSerialNumber();
        deviceModel = getDeviceModel();
        copyLibDeviceConfigSoToInternalStorage();
        patchNativeLibrarySearchPath();
        abpBank = new ABPBank(this);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                showExitConfirmationDialog();
            }
        });

        context = this;
        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                runOnUiThread(() -> {
                    onNetworkStatusChanged(true);
                    initAuthFlow();
                });
            }

            @Override
            public void onLost(@NonNull Network network) {
                runOnUiThread(() -> onNetworkStatusChanged(false));
            }
        };
        NavigationView navView = findViewById(R.id.nav_view);
        DrawerLayout drawer = findViewById(R.id.drawer_layout);

        // Get header layout
        View headerView = navView.getHeaderView(0);

        // Back button
        ImageView backBtn = headerView.findViewById(R.id.image_back);

        backBtn.setOnClickListener(v -> {
            drawer.closeDrawer(GravityCompat.START);
        });

        ImageView serialNumberBtn = headerView.findViewById(R.id.image_register);
        // Version View
        TextView tvVersion = headerView.findViewById(R.id.text_version);
        TextView tvBuild = headerView.findViewById(R.id.text_build_no);
        TextView device_id = headerView.findViewById(R.id.text_device_id);
        TextView text_release_date = headerView.findViewById(R.id.text_release_date);

        // Admin Button View
        Button btnAdmin = headerView.findViewById(R.id.btn_admin);
        btnAdmin.setOnClickListener(v -> showAdminDialog());

        tvVersion.setText("Version : " + getVersionNameOnly());
        tvBuild.setText("Build : " + getBuildNumber());
        text_release_date.setText("Release Date : " + AppConstants.RELEASE_DATE);
        serialNumberBtn.setOnClickListener(v -> {
            long currentTime = System.currentTimeMillis();
            // Reset counter if delay between clicks is too long
            if (currentTime - firstClickTime > 3000) { // 3 sec window
                clickCount = 0;
                firstClickTime = currentTime;
            }
            clickCount++;
            Log.d("CLICK_DEBUG", "Click Count: " + clickCount);
            // Trigger on 5 continuous clicks
            if (clickCount == 5) {
                clickCount = 0;
                device_id.setText("Serial Number : " + deviceSerialNumber);
                Toast.makeText(this, "Serial Number Visible", Toast.LENGTH_SHORT).show();
                // Hide again after 1 minute
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    device_id.setText("");
                    Toast.makeText(this, "Serial Number Hidden", Toast.LENGTH_SHORT).show();
                }, 60000); // 1 minute
            }
        });

        try {
            myCardDetector = new CardDetector(this, iDal);
            myCardDetector.setCallback(new CardDetector.Callback() {
                @SuppressLint("SetTextI18n")
                @Override
                public void onCardDetected(String cardType, String uid, String balance, Intent intentToLaunch, String tranType, String topupAmount, String sourceTxnId, String dbTxnId) {
                    Log.d(TAG, "Card detected: " + cardType + ", UID: " + uid + ", Balance : " + balance);
                    Log.d(TAG, "On Card detect callback");

                    if (balance == null && Objects.equals(tranType, TxnConstants.CREDIT_DEBIT)) {
                        runOnUiThread(() -> {
                            if (tapCardDialog != null && tapCardDialog.isShowing()) {
                                tvTapMessage.setText("Transaction For Credit Debit Card Done");
                                loader.setVisibility(View.VISIBLE);
                            }
                            if (timeoutRunnable != null) handler.removeCallbacks(timeoutRunnable);
                        });

                        dismissTapCardDialog();

                        clearPaymentEnquiryTimers();
                        awaitingEnquiryAfterPaymentTimeout = false;
                        if (!bankLaunchPending) {
                            flowState = FlowState.IDLE;
                        }
                        uartManager.sendCreditDebitResponse(uid, null, topupAmount);

                    } else {
                        runOnUiThread(() -> {
                            if (tapCardDialog != null && tapCardDialog.isShowing()) {
                                tvTapMessage.setText("Fetching card details...");
                                loader.setVisibility(View.VISIBLE);
                            }
                            if (timeoutRunnable != null) handler.removeCallbacks(timeoutRunnable);
                        });

                        // 2) After a very short delay (300ms), show the final popup
                        handler.postDelayed(() -> runOnUiThread(() -> {
                            dismissTapCardDialog();

                            if (intentToLaunch != null) {
                                Log.d(TAG, "Bank Intent : " + intentToLaunch);
                                Log.d(TAG, "Intent Created, Bank Intent is NOT NULL. Received bank intent, Launching Bank App...");
                                startPayment(intentToLaunch);
                            } else {
                                // 3) Send UART message in background (non-blocking)
                                try {
                                    if (uartManager != null) {
                                        String uartMsg = "CARD_DETECTED : " + uid + " Balance :" + balance + "tranType : " + tranType;
                                        if (Objects.equals(tranType, TxnConstants.BALANCE_ENQ)) {
                                            uartManager.sendBalanceEnquiryResponse(
                                                    tranType,
                                                    "",
                                                    "",
                                                    balance,
                                                    "",
                                                    cardType,
                                                    "",
                                                    "",
                                                    RRN,
                                                    "",
                                                    "",
                                                    tranType,
                                                    dbTxnId,
                                                    "",
                                                    balance,
                                                    ""
                                            );
                                        } else if (Objects.equals(tranType, TxnConstants.CREDIT_DEBIT)) {
                                            String TID = "";
                                            String rrn = "";
                                            uartManager.sendSaleSuccessResponse("", TxnConstants.SALE, StatusConstants.SUCCESS, topupAmount, topupAmount,dbTxnId, uid, TID, topupAmount, rrn, "", sourceTxnId, "");
                                        } else if (Objects.equals(tranType, TxnConstants.TOPUP)) {
                                            uartManager.sendBalanceUpdateResponse(
                                                    "",
                                                    "",
                                                    "",
                                                    balance,
                                                    "",
                                                    tranType,
                                                    dbTxnId,
                                                    "",
                                                    "",
                                                    "",
                                                    "",
                                                    "",
                                                    "",
                                                    "",
                                                    "",
                                                    RRN,
                                                    balance,
                                                    "",
                                                    "",
                                                    "");
                                        } else {
                                            String msg = "Some thing Wend Wrong Translation Type Not Found";
                                            uartManager.sendErrorResponse(tranType, "", dbTxnId, msg, "");
                                        }
                                        // Response already sent to TVM — never let a stale timer start TRANSACTION_ENQUIRY.
                                        clearPaymentEnquiryTimers();
                                        awaitingEnquiryAfterPaymentTimeout = false;
                                        if (!bankLaunchPending) {
                                            flowState = FlowState.IDLE;
                                        }
                                        Log.d(TAG, "UART message sent: " + uartMsg);
                                    }
                                } catch (Exception e) {
                                    Log.e(TAG, "UART send failed: " + e.getMessage(), e);
                                }
                            }
                        }), 300);
                    }
                }

                @Override
                public void onError(String error) {
                    Log.d(TAG, "On Error : " + error);
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
        }

        // Initialize UART
        uartManager = UartManager.getInstance(this);
        uartManager.setCallback((data, dbTxnId) -> {
            Log.d("MainActivity", "UART Trigger: " + data);

            try {
                // New TVM request: drop stale payment/enquiry timers so a prior 65s callback
                // cannot fire TRANSACTION_ENQUIRY into this new flow.
                clearPaymentEnquiryTimers();
                awaitingEnquiryAfterPaymentTimeout = false;
                hardAbortSentToTvm = false;
                if (!bankLaunchPending) {
                    flowState = FlowState.IDLE;
                }

                JSONObject root = new JSONObject(data);
                JSONObject dataObj = root.getJSONObject(JsonKeys.DATA);
                tranType = dataObj.optString(JsonKeys.TRAN_TYPE, "");
                topupAmount = dataObj.optLong(JsonKeys.AMOUNT, 0);
                SOURCE_TXN_ID = dataObj.optString(JsonKeys.SOURCE_TXN_ID, "NA");
                OPERATOR_ORDER_ID = dataObj.optString(JsonKeys.OPERATOR_ORDER_ID, "NA");
                INVOICE_NO = dataObj.optString(JsonKeys.INVOICE_NO, "NA");
                RRN = dataObj.optString(JsonKeys.RRN, "NA");
                IS_OFFLINE = dataObj.optString(JsonKeys.IS_OFFLINE, "0");
                PRINT_FLAG = dataObj.optString(JsonKeys.PRINT_FLAG, "0");
                SHIFT_NO = dataObj.optString(JsonKeys.SHIFT_NO, "NA");
                STATION_NAME = dataObj.optString(JsonKeys.STATION_NAME, "NA");
                STATION_ID = dataObj.optString(JsonKeys.STATION_ID, "NA");
                GATENO = dataObj.optString(JsonKeys.GATENO, "NA");
                UDF1 = dataObj.optString(JsonKeys.UDF1, "");
                UDF2 = dataObj.optString(JsonKeys.UDF2, "");
                UDF3 = dataObj.optString(JsonKeys.UDF3, deviceSerialNumber);
                UDF4 = dataObj.optString(JsonKeys.UDF4, "");
                UDF5 = dataObj.optString(JsonKeys.UDF5, "");

                Log.d(TAG, "Tran Type Main Activity : " + tranType);

                if (Objects.equals(tranType, TxnConstants.BALANCE_ENQ) ||
                        Objects.equals(tranType, TxnConstants.TOPUP) ||
                        Objects.equals(tranType, TxnConstants.CREDIT_DEBIT)) {
                    runOnUiThread(() -> {
                        showTapCardDialog();
                        Log.d(TAG, "After showTapCardDialog : " + timeStamp());
                        if (myCardDetector != null) {
                            Log.d(TAG, "Before Start Polling");
                            myCardDetector.startPolling(data, dbTxnId);
                        }
                    });
                } else {
                    Intent bankIntent = null;
                    try {
                        Log.d(TAG, "Start Create Intent");
                        bankIntent = abpBank.createBankAppIntent(
                                tranType,
                                String.valueOf(topupAmount),
                                SOURCE_TXN_ID,
                                dbTxnId,
                                OPERATOR_ORDER_ID,
                                INVOICE_NO,
                                RRN,
                                IS_OFFLINE,
                                PRINT_FLAG,
                                SHIFT_NO,
                                STATION_NAME,
                                STATION_ID,
                                GATENO,
                                UDF1,
                                UDF2,
                                UDF3,
                                UDF4,
                                UDF5
                        );

                        Log.d(TAG, "Bank Intent : " + bankIntent);
                        if (bankIntent == null) {
                            Log.e(TAG, "Bank Intent is NULL, Unable to initiate payment. Please try again.");
                            AppLogger.trxn_log(context, StatusConstants.ERR_BANK_INTENT_NULL_LOG, "Bank Intent NULL, Bank app launch failed. TRAN_TYPE or some other required parameter is null");
                            uartManager.sendErrorResponse(
                                    tranType,
                                    StatusConstants.ERR_BANK_INTENT_NULL_CODE,
                                    "Bank Intent NULL, Bank app launch failed. TRAN_TYPE or some other required parameter is null",
                                    OPERATOR_ORDER_ID,
                                    ""
                            );
                            return;
                        } else {
                            Log.d(TAG, "Intent Created, Bank Intent is NOT NULL");
                        }
                        Log.d(TAG, "Launching Bank App...");
                        startPayment(bankIntent);
                    } catch (Exception e) {
                        Log.e(TAG, "Error while creating or launching bank intent", e);
                        AppLogger.trxn_log(context, StatusConstants.ERR_BANK_LAUNCH_FAILED_LOG, "Bank app launch failed. Something went wrong. " + e.getMessage());
                        uartManager.sendErrorResponse(
                                tranType,
                                StatusConstants.ERR_BANK_LAUNCH_FAILED_CODE,
                                "Bank app launch failed. Something went wrong. " + e.getMessage(),
                                OPERATOR_ORDER_ID,
                                ""
                        );
                    }
                }
            } catch (Exception e) {
                Log.d(TAG, Objects.requireNonNull(e.getMessage()));
            }

        });

        // Bank Response Processor (UART replies are no-ops when mode=CLOUD)
        bankResponseProcessor = new BankResponseProcessor(uartManager, this);

        // Start only one host channel based on local/server integration_type
        applyIntegrationMode(IntegrationModeStore.get(this), false);

        // AuthFlow — re-login when refresh fails / session invalidated
        AuthManager.init(getApplicationContext());
        AuthManager.setSessionListener(() -> {
            Log.w(TAG, "Auth session invalid; restarting auth flow");
            HeartbeatManager.getInstance().stop();
            initAuthFlow();
        });
        HeartbeatManager.getInstance().setIntegrationTypeListener(type ->
                runOnUiThread(() -> applyIntegrationMode(type, true)));
        initAuthFlow();

        // Periodic Upload Worker
        logWorker();

        // Some iDalSetting For PAX
        iDalSetting(false);
    }

    // Hide System UI
    private void hideSystemUI() {
        View decorView = getWindow().getDecorView();

        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
        );
    }

    private void startPayment(Intent bankIntent) {
        if (bankLaunchPending) {
            Log.w(TAG, "Ignoring startPayment — bank ActivityResult still pending (avoid double bank launch)");
            AppLogger.trxn_log(context, StatusConstants.ERR_BANK_LAUNCH_FAILED_LOG,
                    "Ignored new bank launch while previous bank request still pending");
            uartManager.sendErrorResponse(
                    tranType,
                    StatusConstants.ERR_BANK_LAUNCH_FAILED_CODE,
                    "Previous bank request still in progress. Please retry.",
                    OPERATOR_ORDER_ID,
                    ""
            );
            return;
        }

        flowState = FlowState.PAYMENT;
        awaitingEnquiryAfterPaymentTimeout = false;
        hardAbortSentToTvm = false;
        paymentStartedAtMs = SystemClock.elapsedRealtime();
        clearPaymentEnquiryTimers();

        paymentTimeoutRunnable = () -> {
            if (flowState != FlowState.PAYMENT) return;

            Log.e(TAG, "PAYMENT TIMEOUT (" + (REQUEST_TIMEOUT / 1000) + " sec) exceeded");
            AppLogger.trxn_log(context, StatusConstants.ERR_REQUEST_TIMEOUT_LOG, "Request Timeout");

            if (shouldSkipTxnEnquiry()) {
                Log.w(TAG, "Skip TRANSACTION_ENQUIRY for balance enquiry txnType=" + tranType);
                if (bankLaunchPending) {
                    // Wait up to +5s grace; hard abort at 70s if bank never returns.
                    schedulePaymentGraceTimeout();
                    return;
                }
                finishFlowWithApbTimeout();
                return;
            }

            // Critical: never launch TRANSACTION_ENQUIRY while the original bank Intent is still open.
            // User may still be on PAX (~55s) and bank may still be processing (~5–6s).
            if (bankLaunchPending) {
                Log.w(TAG, "Payment timeout but bank still in flight — defer TRANSACTION_ENQUIRY, start "
                        + (PAYMENT_GRACE_TIMEOUT / 1000) + "s grace (hard abort at "
                        + ((REQUEST_TIMEOUT + PAYMENT_GRACE_TIMEOUT) / 1000) + "s)");
                schedulePaymentGraceTimeout();
                return;
            }

            startTxnEnquiry();
        };

        handler.postDelayed(paymentTimeoutRunnable, REQUEST_TIMEOUT);

        bankLaunchPending = true;
        paymentLauncher.launch(bankIntent);
    }

    /**
     * After soft payment timeout while bank ActivityResult is still pending, wait
     * {@link #PAYMENT_GRACE_TIMEOUT} more (65s + 5s = 70s total) before APB timeout to TVM.
     * Does not launch TRANSACTION_ENQUIRY (avoids double bank Intent).
     */
    private void schedulePaymentGraceTimeout() {
        awaitingEnquiryAfterPaymentTimeout = true;
        if (paymentGraceTimeoutRunnable != null) {
            handler.removeCallbacks(paymentGraceTimeoutRunnable);
        }
        paymentGraceTimeoutRunnable = () -> {
            if (!awaitingEnquiryAfterPaymentTimeout) return;
            if (!bankLaunchPending && flowState != FlowState.PAYMENT) return;

            Log.e(TAG, "PAYMENT GRACE TIMEOUT (" + (PAYMENT_GRACE_TIMEOUT / 1000)
                    + " sec) — bank still pending after "
                    + ((REQUEST_TIMEOUT + PAYMENT_GRACE_TIMEOUT) / 1000)
                    + "s total; sending APB timeout to TVM (no TRANSACTION_ENQUIRY)");
            AppLogger.trxn_log(context, StatusConstants.ERR_REQUEST_TIMEOUT_LOG,
                    "Payment grace timeout — bank did not return ActivityResult");
            hardAbortSentToTvm = true;
            finishFlowWithApbTimeout();
        };
        handler.postDelayed(paymentGraceTimeoutRunnable, PAYMENT_GRACE_TIMEOUT);
        Log.d(TAG, "Payment grace timer started (" + (PAYMENT_GRACE_TIMEOUT / 1000) + "s)");
    }

    private void startTxnEnquiry() {
        if (shouldSkipTxnEnquiry()) {
            Log.w(TAG, "startTxnEnquiry skipped for balance enquiry txnType=" + tranType);
            finishFlowWithApbTimeout();
            return;
        }

        if (bankLaunchPending) {
            Log.w(TAG, "startTxnEnquiry blocked — bank launch still pending; scheduling grace");
            if (!awaitingEnquiryAfterPaymentTimeout) {
                schedulePaymentGraceTimeout();
            }
            return;
        }

        long enquiryWaitMs = remainingPaxDeadlineMs();
        if (enquiryWaitMs <= 0) {
            Log.w(TAG, "No time left before 70s PAX deadline — skip TRANSACTION_ENQUIRY, notify TVM");
            finishFlowWithApbTimeout();
            return;
        }

        flowState = FlowState.ENQUIRY;
        awaitingEnquiryAfterPaymentTimeout = false;
        if (enquiryTimeoutRunnable != null) {
            handler.removeCallbacks(enquiryTimeoutRunnable);
        }

        Log.d(TAG, "Enquiry started, OPERATOR_ORDER_ID=" + OPERATOR_ORDER_ID
                + ", timeout=" + enquiryWaitMs + " ms (" + (enquiryWaitMs / 1000) + " sec)");
        enquiryTimeoutRunnable = () -> {
            if (flowState != FlowState.ENQUIRY) return;
            Log.e(TAG, "ENQUIRY TIMEOUT exceeded, sending APB timeout to TVM");
            AppLogger.trxn_log(context, StatusConstants.ERR_TXN_ENQUIRY_REQUEST_TIMEOUT_LOG, "Request Timeout");
            // Do not clear bankLaunchPending here — ActivityResult may still arrive; keep blocking a second launch.
            hardAbortSentToTvm = true;
            flowState = FlowState.IDLE;
            sendApbTimeoutToTVM();
        };

        handler.postDelayed(enquiryTimeoutRunnable, enquiryWaitMs);

        try {
            Intent bankIntent = abpBank.createBankAppIntent(
                    TxnConstants.TRANSACTION_ENQUIRY,
                    "", "", "", OPERATOR_ORDER_ID,
                    "", "", "", "", "", "", "", "",
                    UDF1, UDF2, UDF3, UDF4, UDF5
            );

            if (bankIntent == null) {
                handler.removeCallbacks(enquiryTimeoutRunnable);
                bankLaunchPending = false;
                flowState = FlowState.IDLE;
                AppLogger.trxn_log(context, StatusConstants.ERR_BANK_INTENT_NULL_LOG, "Bank Intent NULL, Bank app launch failed. TRAN_TYPE or some other required parameter is null");
                uartManager.sendErrorResponse(
                        tranType, StatusConstants.ERR_BANK_INTENT_NULL_CODE, "Bank Intent NULL, Bank app launch failed. TRAN_TYPE or some other required parameter is null",
                        OPERATOR_ORDER_ID, ""
                );
                return;
            }

            bankLaunchPending = true;
            paymentLauncher.launch(bankIntent);

        } catch (Exception e) {

            handler.removeCallbacks(enquiryTimeoutRunnable);
            bankLaunchPending = false;
            flowState = FlowState.IDLE;
            AppLogger.trxn_log(context, StatusConstants.ERR_BANK_LAUNCH_FAILED_LOG, "Bank app launch failed. Something went wrong. " + e.getMessage());
            uartManager.sendErrorResponse(
                    tranType,
                    StatusConstants.ERR_BANK_LAUNCH_FAILED_CODE,
                    "Bank app launch failed. Something went wrong. " + e.getMessage(),
                    OPERATOR_ORDER_ID,
                    ""
            );
        }
    }

    private boolean shouldSkipTxnEnquiry() {
        return Objects.equals(tranType, TxnConstants.BALANCE_ENQUIRY)
                || Objects.equals(tranType, TxnConstants.BALANCE_ENQ);
    }

    /** Time left until PAX 70s deadline (65s + 5s grace). Caps enquiry so UART still fits before TVM 75s. */
    private long remainingPaxDeadlineMs() {
        long elapsed = SystemClock.elapsedRealtime() - paymentStartedAtMs;
        long remaining = (REQUEST_TIMEOUT + PAYMENT_GRACE_TIMEOUT) - elapsed;
        if (remaining <= 0) return 0L;
        return Math.min(ENQUIRY_TIMEOUT, remaining);
    }

    private void finishFlowWithApbTimeout() {
        clearPaymentEnquiryTimers();
        awaitingEnquiryAfterPaymentTimeout = false;
        bankLaunchPending = false;
        flowState = FlowState.IDLE;
        hardAbortSentToTvm = true;
        sendApbTimeoutToTVM();
    }

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                Log.d(TAG, "USB Detached");
                if (IntegrationModeStore.isUsb(MainActivity.this) && uartManager != null) {
                    uartManager.handleUsbDetached();
                }
            }

            if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                Log.d(TAG, "USB Attached");
                if (IntegrationModeStore.isUsb(MainActivity.this) && uartManager != null) {
                    uartManager.handleUsbAttached();
                } else {
                    Log.d(TAG, "Ignoring USB attach — mode=" + IntegrationModeStore.get(MainActivity.this));
                }
            }
        }
    };

    @SuppressLint("HardwareIds")
    public static String getDeviceSerialNumber() {
        return Build.SERIAL;
    }

    public static String getDeviceModel() {
        return Build.MODEL;
    }

    private String getVersionNameOnly() {
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            return pInfo.versionName;
        } catch (Exception e) {
            return "-";
        }
    }

    private String getBuildNumber() {
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                return String.valueOf(pInfo.getLongVersionCode());
            } else {
                return String.valueOf(pInfo.versionCode);
            }

        } catch (Exception e) {
            return "-";
        }
    }

    private void iDalSetting(Boolean isSet) {
        try {
            iDal = NeptuneLiteUser.getInstance().getDal(getApplicationContext());
        } catch (Exception e) {
            Log.e(TAG, "iDal initialization failed", e);
            return;
        }
        try {
            iDal.getSys().enableStatusBar(isSet);
            iDal.getSys().enableNavigationKey(ENavigationKey.HOME, isSet);
            iDal.getSys().enableNavigationKey(ENavigationKey.BACK, isSet);
            iDal.getSys().enableNavigationKey(ENavigationKey.RECENT, isSet);
        } catch (Exception e) {
            Log.e(TAG, "iDalSetting apply failed", e);
        }
    }

    private void showAdminDialog() {

        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        builder.setTitle("Enter Admin Password");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        builder.setView(input);

        builder.setPositiveButton("OK", (dialog, which) -> {
            String enteredPassword = input.getText().toString();

            if (isPasswordCorrect(enteredPassword)) {
                // Set iDalSetting to true
                iDalSetting(true);
                Intent intent = new Intent(Intent.ACTION_MAIN);
                // Add the CATEGORY_HOME category to the intent
                intent.addCategory(Intent.CATEGORY_HOME);
                // Set flags to the intent to define its behavior
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                // Start the activity with the created intent using startActivity
                startActivity(Intent.createChooser(intent, "Please set launcher settings"));
            } else {
                Toast.makeText(this, "Wrong Password", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    private void saveAdminPassword(String password) {
        SharedPreferences prefs = getSharedPreferences(AppConstants.PREF_ADMIN, MODE_PRIVATE);
        prefs.edit().putString(AppConstants.KEY_ADMIN_PASSWORD, password).apply();
    }

    private boolean isPasswordCorrect(String inputPassword) {

        SharedPreferences prefs = getSharedPreferences(AppConstants.PREF_ADMIN, MODE_PRIVATE);
        String savedPassword = prefs.getString(AppConstants.KEY_ADMIN_PASSWORD, AppConstants.DEFAULT_ADMIN_PASSWORD); // default

        return inputPassword.equals(savedPassword);
    }

    private void logWorker() {
        Log.d(TAG, "Periodic Upload Worker started");
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build();

        // 🔥 One-time (safe)
        WorkManager.getInstance(this).enqueueUniqueWork(
                "log_upload_once",
                ExistingWorkPolicy.KEEP,
                new OneTimeWorkRequest.Builder(LogUploadWorker.class)
                        .setConstraints(constraints)
                        .setBackoffCriteria(
                                BackoffPolicy.EXPONENTIAL,
                                30,
                                TimeUnit.SECONDS
                        )
                        .build()
        );

        // 🔁 Periodic Work (stable)
        PeriodicWorkRequest periodicWork =
                new PeriodicWorkRequest.Builder(LogUploadWorker.class,  BuildConfig.ENABLE_TEST_LOG_UPLOAD ? 15 : 6, BuildConfig.ENABLE_TEST_LOG_UPLOAD ? TimeUnit.MINUTES : TimeUnit.HOURS)
                        .setConstraints(constraints)
                        .setBackoffCriteria(
                                BackoffPolicy.EXPONENTIAL,
                                30,
                                TimeUnit.SECONDS
                        )
                        .build();

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "log_upload",
                ExistingPeriodicWorkPolicy.KEEP,
                periodicWork
        );
        Log.d(TAG, "End Periodic Upload Worker");
    }

    private void initAuthFlow() {
        if (authBootstrapInProgress) {
            Log.d(TAG, "Auth bootstrap already running; skipping duplicate trigger");
            return;
        }
        authBootstrapInProgress = true;
        AuthManager.init(getApplicationContext());
        Log.d(TAG, "Auth bootstrap start");
        AuthRepository authRepo = new AuthRepository();
        String token = AuthManager.getToken(getApplicationContext());

        if (token != null) {
            if (!AuthManager.hasKnownExpiry() || AuthManager.isExpiringSoon()) {
                Log.d(TAG, "Token near expiry or unknown TTL; refreshing before device register");
                refreshThenRegister(authRepo, token);
            } else {
                Log.d(TAG, "Using existing token");
                registerDevice(authRepo, token);
            }
        } else {
            Log.d(TAG, "No token found, calling login API");
            loginAndRegister(authRepo);
        }
    }

    private void refreshThenRegister(AuthRepository authRepo, String token) {
        AppLogger.api_log(getApplicationContext(), "TOKEN_REFRESH", "API Called");
        authRepo.refresh(token, new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<LoginResponse> call,
                                   @NonNull Response<LoginResponse> response) {
                if (response.isSuccessful()
                        && response.body() != null
                        && response.body().getData() != null
                        && response.body().getData().getToken() != null) {
                        String newToken = response.body().getData().getToken();
                    long expiresIn = response.body().getData().resolveExpiresInSeconds();
                    AuthManager.setSession(getApplicationContext(), newToken, expiresIn);
                    AppLogger.api_log(getApplicationContext(), "TOKEN_REFRESH",
                            "Success expires_in=" + expiresIn);
                    clearAuthRetry();
                    registerDevice(authRepo, newToken);
                } else {
                    Log.w(TAG, "Refresh failed HTTP " + response.code() + "; falling back to login");
                    AppLogger.api_log(getApplicationContext(), "TOKEN_REFRESH", "Failed HTTP " + response.code());
                    AuthManager.clearSession(getApplicationContext());
                    loginAndRegister(authRepo);
                }
            }

            @Override
            public void onFailure(@NonNull Call<LoginResponse> call, @NonNull Throwable t) {
                AppLogger.api_log(getApplicationContext(), "TOKEN_REFRESH", "Failure: " + t.getMessage());
                // Keep existing token and try register; interceptor may still recover on 401
                registerDevice(authRepo, token);
            }
        });
    }

    private void loginAndRegister(AuthRepository authRepo) {

        Log.d(TAG, "Call the login API");

        AppLogger.api_log(getApplicationContext(), "LOGIN", "API Called");

        authRepo.login(
                new LoginRequest(ApiConstants.EMAIL, ApiConstants.PASSWORD),
                new Callback<>() {

                    @Override
                    public void onResponse(@NonNull Call<LoginResponse> call,
                                           @NonNull Response<LoginResponse> response) {

                        if (response.body() == null || response.body().getData() == null) {
                            AppLogger.api_log(getApplicationContext(), "LOGIN API RESPONSE", "Empty response");
                            scheduleAuthRetry("Login API returned empty body");
                            return;
                        }

                        String token = response.body().getData().getToken();
                        long expiresIn = response.body().getData().resolveExpiresInSeconds();

                        Log.d(TAG, "Token received; expires_in=" + expiresIn);

                        AppLogger.api_log(getApplicationContext(),
                                "LOGIN API RESPONSE",
                                response.body().getMessage() + " | expires_in=" + expiresIn);

                        AuthManager.setSession(getApplicationContext(), token, expiresIn);
                        clearAuthRetry();

                        registerDevice(authRepo, token);
                    }

                    @Override
                    public void onFailure(@NonNull Call<LoginResponse> call,
                                          @NonNull Throwable t) {

                        AppLogger.api_log(getApplicationContext(),
                                "LOGIN API FAILURE",
                                t.getMessage());
                        scheduleAuthRetry("Login failure: " + t.getMessage());
                    }
                }
        );
    }

    private void registerDevice(AuthRepository authRepo, String token) {

        String appVersion = getVersionNameOnly() + "+" + getBuildNumber();
        String integrationType = IntegrationModeStore.get(this);

        AppLogger.api_log(getApplicationContext(),
                "DEVICE REGISTER",
                "Device Register API Called"
                        + " serial=" + deviceSerialNumber
                        + " model=" + deviceModel
                        + " app_version=" + appVersion
                        + " integration_type=" + integrationType);

        Log.d(TAG, "Register device payload"
                + " serial=" + deviceSerialNumber
                + " model=" + deviceModel
                + " app_version=" + appVersion
                + " integration_type=" + integrationType);

        DeviceRepository deviceRepo = new DeviceRepository();

        deviceRepo.registerDevice(
                token,
                new DeviceRequest(deviceSerialNumber, deviceModel, appVersion, integrationType),
                new Callback<>() {
                    @Override
                    public void onResponse(@NonNull Call<DeviceResponse> call,
                                           @NonNull Response<DeviceResponse> response) {

                        if (response.isSuccessful() && response.body() != null) {

                            Log.d(TAG, "deviceResponse " + response);

                            AppLogger.api_log(getApplicationContext(),
                                    "DEVICE REGISTER API RESPONSE",
                                    DeviceResponse.getMessage());

                            HeartbeatManager.getInstance()
                                    .start(getApplicationContext());
                            clearAuthRetry();
                            authBootstrapInProgress = false;

                        } else if (response.code() == 401) {

                            // Authenticator already attempted refresh; still 401 → full re-login
                            Log.d(TAG, "Token invalid after refresh attempt, re-login");

                            AuthManager.clearSession(getApplicationContext());
                            authBootstrapInProgress = false;

                            loginAndRegister(authRepo);
                        } else if (response.code() == 409) {
                            Log.d(TAG, "Device already registered");
                            HeartbeatManager.getInstance()
                                    .start(getApplicationContext());
                            clearAuthRetry();
                            authBootstrapInProgress = false;
                        } else {
                            scheduleAuthRetry("Device register failed with HTTP " + response.code());
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<DeviceResponse> call,
                                          @NonNull Throwable t) {

                        AppLogger.api_log(getApplicationContext(),
                                "DEVICE REGISTER FAILURE",
                                t.getMessage());
                        scheduleAuthRetry("Device register failure: " + t.getMessage());
                    }
                }
        );
    }

    private void scheduleAuthRetry(String reason) {
        authBootstrapInProgress = false;
        Log.w(TAG, "Scheduling auth retry in " + authRetryDelayMs + " ms. Reason: " + reason);
        AppLogger.api_log(getApplicationContext(), "AUTH_RETRY", reason + " | Retry in " + authRetryDelayMs + " ms");
        handler.removeCallbacks(authRetryRunnable);
        handler.postDelayed(authRetryRunnable, authRetryDelayMs);
        authRetryDelayMs = Math.min(authRetryDelayMs * 2, AUTH_RETRY_MAX_MS);
    }

    private void clearAuthRetry() {
        authRetryDelayMs = AUTH_RETRY_BASE_MS;
        handler.removeCallbacks(authRetryRunnable);
    }

    private boolean isInternetAvailable() {
        if (connectivityManager == null) return false;
        Network activeNetwork = connectivityManager.getActiveNetwork();
        if (activeNetwork == null) return false;
        NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(activeNetwork);
        return capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    private void onNetworkStatusChanged(boolean isConnected) {
        if (lastNetworkAvailable != null && lastNetworkAvailable == isConnected) {
            return;
        }
        lastNetworkAvailable = isConnected;
        if (isConnected) {
            Toast.makeText(this, getString(R.string.internet_connected), Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, getString(R.string.internet_not_available), Toast.LENGTH_SHORT).show();
        }
    }

    private void registerNetworkCallback() {
        if (networkCallbackRegistered || connectivityManager == null || networkCallback == null) return;
        try {
            NetworkRequest networkRequest = new NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build();
            connectivityManager.registerNetworkCallback(networkRequest, networkCallback);
            networkCallbackRegistered = true;
        } catch (Exception e) {
            Log.e(TAG, "Unable to register network callback", e);
        }
    }

    private void unregisterNetworkCallback() {
        if (!networkCallbackRegistered || connectivityManager == null || networkCallback == null) return;
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback);
        } catch (Exception e) {
            Log.e(TAG, "Unable to unregister network callback", e);
        } finally {
            networkCallbackRegistered = false;
        }
    }

    private final ActivityResultLauncher<Intent> paymentLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        bankLaunchPending = false;

                        String responseType = extractResponseType(result.getData());
                        boolean isEnquiryResponse = TxnConstants.TRANSACTION_ENQUIRY.equalsIgnoreCase(responseType);

                        Log.d(TAG, "paymentLauncher callback: resultCode=" + result.getResultCode()
                                + ", flowState=" + flowState
                                + ", responseType=" + responseType
                                + ", awaitingEnquiryAfterPaymentTimeout=" + awaitingEnquiryAfterPaymentTimeout
                                + ", hardAbortSentToTvm=" + hardAbortSentToTvm);

                        // Hard abort already notified TVM at 70s — ignore late bank result (no second UART reply).
                        if (hardAbortSentToTvm) {
                            Log.w(TAG, "Ignoring late bank result after hard abort already sent to TVM");
                            clearPaymentEnquiryTimers();
                            awaitingEnquiryAfterPaymentTimeout = false;
                            hardAbortSentToTvm = false;
                            flowState = FlowState.IDLE;
                            return;
                        }

                        if (result.getResultCode() == Activity.RESULT_OK) {
                            // Accept late payment success even if a deferred timeout already flipped intent to enquire.
                            // Previously we dropped RESULT_OK while ENQUIRY, which lost a valid txn after ~55s+bank delay.
                            if (flowState == FlowState.ENQUIRY && !isEnquiryResponse) {
                                Log.w(TAG, "Late payment RESULT_OK accepted while enquiry flow was active");
                            }

                            clearPaymentEnquiryTimers();
                            awaitingEnquiryAfterPaymentTimeout = false;
                            flowState = FlowState.IDLE;
                            if (responseType == null) {
                                Log.w(TAG, "RESULT_OK received without RESPONSE_TYPE; processing anyway");
                            }
                            bankResponseProcessor.process(result.getData());
                            maybePublishMqttBankResult(true, result.getData(), "Sale completed");
                            return;
                        }

                        if (flowState == FlowState.PAYMENT) {
                            clearPaymentEnquiryTimers();

                            if (shouldSkipTxnEnquiry()) {
                                Log.w(TAG, "APB non-OK during balance enquiry — skip TRANSACTION_ENQUIRY, notify TVM");
                                awaitingEnquiryAfterPaymentTimeout = false;
                                flowState = FlowState.IDLE;
                                sendApbTimeoutToTVM();
                                maybePublishMqttBankResult(false, result.getData(), "Bank declined / cancelled");
                                return;
                            }

                            // Bank closed without success: enquire now (do not wait for remaining 65s/grace timer).
                            Log.w(TAG, "APB returned non-OK during PAYMENT"
                                    + (awaitingEnquiryAfterPaymentTimeout
                                    ? " after deferred timeout — starting TRANSACTION_ENQUIRY"
                                    : " — starting TRANSACTION_ENQUIRY"));
                            awaitingEnquiryAfterPaymentTimeout = false;
                            // For MQTT card sale, publish failure now (enquiry still runs for UART path).
                            maybePublishMqttBankResult(false, result.getData(), "Bank non-OK during payment");
                            startTxnEnquiry();
                            return;
                        }

                        if (flowState == FlowState.ENQUIRY) {
                            Log.w(TAG, "APB returned non-OK during ENQUIRY, waiting for " + (ENQUIRY_TIMEOUT / 1000) + "s timer to expire");
                            return;
                        }

                        // Deferred timeout path if state was reset unexpectedly but flag remains.
                        if (awaitingEnquiryAfterPaymentTimeout) {
                            Log.w(TAG, "Non-OK bank result with deferred enquiry flag — starting TRANSACTION_ENQUIRY");
                            awaitingEnquiryAfterPaymentTimeout = false;
                            clearPaymentEnquiryTimers();
                            if (shouldSkipTxnEnquiry()) {
                                finishFlowWithApbTimeout();
                            } else {
                                startTxnEnquiry();
                            }
                            return;
                        }

                        Log.w(TAG, "Bank result received in IDLE state, ignoring. flowState=" + flowState);
                    }
            );

    private String extractResponseType(Intent data) {
        if (data == null || data.getExtras() == null) return null;
        String result = data.getExtras().getString(JsonKeys.RESULT);
        if (result == null || result.trim().isEmpty()) return null;

        try {
            String cleanResult = result.replace(BankConstants.STX, "").replace(BankConstants.ETX, "");
            JSONObject json = new JSONObject(cleanResult);
            return json.optString(JsonKeys.RESPONSE_TYPE, null);
        } catch (Exception e) {
            Log.w(TAG, "Unable to extract RESPONSE_TYPE from bank result");
            return null;
        }
    }

    private void clearPaymentEnquiryTimers() {
        if (paymentTimeoutRunnable != null) {
            handler.removeCallbacks(paymentTimeoutRunnable);
        }
        if (paymentGraceTimeoutRunnable != null) {
            handler.removeCallbacks(paymentGraceTimeoutRunnable);
        }
        if (enquiryTimeoutRunnable != null) {
            handler.removeCallbacks(enquiryTimeoutRunnable);
        }
    }

    private void sendFailureToTVM() {
        uartManager.sendErrorResponse(
                tranType,
                StatusConstants.FAILED,
                "Transaction failed",
                OPERATOR_ORDER_ID,
                ""
        );
    }

    private void sendApbTimeoutToTVM() {
        uartManager.sendErrorResponse(
                tranType,
                StatusConstants.APB_TIMEOUT,
                "No response from APB application",
                OPERATOR_ORDER_ID,
                ""
        );
    }


    private void patchNativeLibrarySearchPath() {
        try {
            ApplicationInfo appInfo = getApplicationInfo();
            Field nativeLibraryDirField = appInfo.getClass().getDeclaredField("nativeLibraryDir");
            nativeLibraryDirField.setAccessible(true);
            nativeLibraryDirField.set(appInfo, getFilesDir().getAbsolutePath());
            Log.d("NATIVE_PATCH", "Patched nativeLibraryDir to: " + getFilesDir().getAbsolutePath());
        } catch (Exception e) {
            Log.e("NATIVE_PATCH", "Failed to patch native lib path: " + e.getMessage());
        }
    }

    private void copyLibDeviceConfigSoToInternalStorage() {
        try {
            File targetFile = new File(getFilesDir(), "libDeviceConfig.so");
            if (!targetFile.exists()) {
                InputStream input = getAssets().open("libDeviceConfig.so");
                FileOutputStream output = new FileOutputStream(targetFile);

                byte[] buffer = new byte[1024];
                int length;
                while ((length = input.read(buffer)) != -1) {
                    output.write(buffer, 0, length);
                }

                input.close();
                output.flush();
                output.close();

                Log.d("SO_COPY", "libDeviceConfig.so copied to " + targetFile.getAbsolutePath());
            } else {
                Log.d("SO_COPY", "Already exists: " + targetFile.getAbsolutePath());
            }

        } catch (IOException e) {
            Log.e("SO_COPY", "Error copying .so: " + e.getMessage());
        }
    }

    /**
     * Show "Tap your card" dialog
     */
    @SuppressLint("SetTextI18n")
    private void showTapCardDialog() {
        Log.d(TAG, "Before While : " + timeStamp());
        if (tapCardDialog != null && tapCardDialog.isShowing()) return;

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_tap_card, null);
        tvTapMessage = dialogView.findViewById(R.id.tvTapMessage);
        loader = dialogView.findViewById(R.id.progressTap);

        tvTapMessage.setText("Please tap your card");
        loader.setVisibility(View.VISIBLE);
        tapCardDialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(false)
                .create();

        tapCardDialog.show();

        Log.d(TAG, "Before While : " + timeStamp());

        // Timeout after 30 seconds if no card is tapped
        timeoutRunnable = () -> {
            if (tapCardDialog != null && tapCardDialog.isShowing()) {
                dismissTapCardDialog();
                if (myCardDetector != null) myCardDetector.stopPolling();
                Log.d(TAG, "Transaction time out");
            }
        };
        handler.postDelayed(timeoutRunnable, AppConstants.TAP_CARD_TIMEOUT_MS);
    }

    private void dismissTapCardDialog() {
        if (tapCardDialog != null && tapCardDialog.isShowing()) {
            tapCardDialog.dismiss();
        }
    }

    private void showExitConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Exit App")
                .setMessage("Are you sure you want to close the app?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    finish();
                })
                .setNegativeButton("No", (dialog, which) -> {
                    dialog.dismiss();
                })
                .create()
                .show();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (myCardDetector != null) myCardDetector.stopPolling();
        if (flowState == FlowState.IDLE) {
            clearPaymentEnquiryTimers();
        }
        unregisterReceiver(usbReceiver);
        unregisterNetworkCallback();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        backgroundExecutor.shutdownNow();
        if (uartManager != null) uartManager.disconnect();
        if (myCardDetector != null) myCardDetector.stopPolling();
        if (timeoutRunnable != null) handler.removeCallbacks(timeoutRunnable);
        handler.removeCallbacks(authRetryRunnable);
        clearPaymentEnquiryTimers();
        awaitingEnquiryAfterPaymentTimeout = false;
        hardAbortSentToTvm = false;
        bankLaunchPending = false;
        flowState = FlowState.IDLE;
        unregisterNetworkCallback();
        if (mqttMessageDialog != null && mqttMessageDialog.isShowing()) {
            mqttMessageDialog.dismiss();
        }
        if (mqttManager != null) {
            mqttManager.disconnect();
        }
    }

    private String getMqttBrokerUrl() {
        return MqttConstants.BROKER_URL;
    }

    /**
     * Exclusive host channel: USB → UART, CLOUD → MQTT.
     */
    private void applyIntegrationMode(String type, boolean fromServer) {
        String next = IntegrationConstants.orDefault(type);
        String prev = IntegrationModeStore.get(this);
        if (fromServer && prev.equals(next)) {
            Log.d(TAG, "integration_type unchanged=" + next);
            return;
        }

        IntegrationModeStore.set(this, next);
        Log.i(TAG, "APPLY integration_type=" + next
                + " previous=" + prev
                + " fromServer=" + fromServer);

        if (IntegrationConstants.CLOUD.equals(next)) {
            stopUartChannel();
            startMqttChannel();
        } else {
            mqttTxnPending = false;
            stopMqttChannel();
            startUartChannel();
        }
    }

    private void startUartChannel() {
        if (uartManager == null) {
            uartManager = UartManager.getInstance(this);
        }
        if (uartManager.connect()) {
            uartManager.startListening();
            Log.i(TAG, "UART channel started");
        } else {
            Log.e(TAG, "UART channel failed to start");
        }
    }

    private void stopUartChannel() {
        if (uartManager != null) {
            uartManager.disconnect();
            Log.i(TAG, "UART channel stopped");
        }
    }

    private void startMqttChannel() {
        if (mqttManager != null && mqttManager.isConnected()) {
            Log.i(TAG, "MQTT already connected");
            return;
        }
        initMqttConnection();
        Log.i(TAG, "MQTT channel starting");
    }

    private void stopMqttChannel() {
        try {
            if (mqttManager != null) {
                mqttManager.disconnect();
                Log.i(TAG, "MQTT channel stopped");
            }
        } catch (Exception e) {
            Log.w(TAG, "MQTT stop error: " + e.getMessage());
        }
    }

    /**
     * Bootstraps MQTT using device serial as clientId.
     * Ported from mqtt.MainFragment#initMqttConnection for PayGo MainActivity.
     */
    private void initMqttConnection() {
        mqttManager = MqttManager.getInstance();
        String clientId = deviceSerialNumber;
        if (clientId == null || clientId.trim().isEmpty()) {
            clientId = "PayGo_" + System.currentTimeMillis();
        }

        mqttManager.initialize(this, getMqttBrokerUrl(), clientId);
        mqttManager.setMqttEventListener(new MqttManager.MqttEventListener() {
            @Override
            public void onConnected() {
                MqttLog.d("MQTT: Connected successfully");
                runOnUiThread(() -> Toast.makeText(
                        MainActivity.this,
                        "Connected with MQTT",
                        Toast.LENGTH_SHORT
                ).show());
                mqttManager.startStatusUpdate();
            }

            @Override
            public void onConnectionFailed(Throwable exception) {
                MqttLog.e("MQTT: Connection failed", exception);
                runOnUiThread(() -> Toast.makeText(
                        MainActivity.this,
                        "MQTT: Connection Failed",
                        Toast.LENGTH_SHORT
                ).show());
            }

            @Override
            public void onConnectionLost(Throwable cause) {
                MqttLog.e("MQTT: Connection lost", cause);
                runOnUiThread(() -> Toast.makeText(
                        MainActivity.this,
                        "MQTT: Connection Lost",
                        Toast.LENGTH_SHORT
                ).show());
            }

            @Override
            public void onSubscribed(String topic) {
                MqttLog.i("MQTT: Subscriber is listening on: " + topic);
                runOnUiThread(() -> Toast.makeText(
                        MainActivity.this,
                        "MQTT listening:\n" + topic,
                        Toast.LENGTH_LONG
                ).show());
            }

            @Override
            public void onMessageReceived(String topic, String message) {
                MqttLog.i("MQTT: Message received on topic [" + topic + "]: " + message);
                runOnUiThread(() -> handleMqttInboundMessage(topic, message));
            }
        });

        if (!mqttManager.isConnected()) {
            mqttManager.connect(
                    MqttConstants.USERNAME,
                    MqttConstants.PASSWORD
            );
        }
    }

    /**
     * MQTT → normalize card request → show data → BANK Sale → later MQTT response.
     */
    private void handleMqttInboundMessage(String topic, String rawMessage) {
        if (!IntegrationModeStore.isCloud(this)) {
            MqttLog.d("MQTT: Ignoring inbound — mode=" + IntegrationModeStore.get(this));
            return;
        }
        try {
            AppLogger.trxn_log(this, BankConstants.MQTT_INBOUND, rawMessage);
            MqttLog.i("MQTT_INBOUND: " + rawMessage);

            JSONObject root = new JSONObject(rawMessage);

            // Optional device filter from pushTo.deviceId
            JSONObject pushTo = root.optJSONObject("pushTo");
            if (pushTo != null) {
                String targetDeviceId = pushTo.optString("deviceId", "");
                if (!targetDeviceId.isEmpty()
                        && deviceSerialNumber != null
                        && !targetDeviceId.equals(deviceSerialNumber)) {
                    MqttLog.d("MQTT: Ignoring message for other deviceId=" + targetDeviceId);
                    return;
                }
            }

            String type = root.optString("type", root.optString("txnType", "")).trim();
            String requestId = root.optString("request_id",
                    root.optString("externalRefNumber", "")).trim();
            // MQTT amount comes as paise-style (send 1 -> get 100). Convert /100 for bank.
            String amountRaw = root.has("amount") ? String.valueOf(root.opt("amount")) : "";
            String amountForBank = convertMqttAmountForBank(amountRaw);
            String phone = root.optString("phone",
                    root.optString("customerMobileNumber", "")).trim();
            String terminalId = root.optString("terminalid",
                    root.optString("terminalId", "")).trim();

            JSONObject normalized = new JSONObject();
            normalized.put("type", type);
            normalized.put("request_id", requestId);
            normalized.put("amount_raw", amountRaw);
            normalized.put("amount", amountForBank);
            normalized.put("phone", phone);
            normalized.put("terminalid", terminalId);

            Toast.makeText(this, "MQTT data received", Toast.LENGTH_SHORT).show();
            showMqttReceivedPopup(topic, normalized.toString(2));

            if (!"card".equalsIgnoreCase(type)) {
                MqttLog.d("MQTT: Ignoring non-card txn type=" + type);
                return;
            }
            if (requestId.isEmpty() || amountForBank.isEmpty()) {
                MqttLog.e("MQTT: Missing request_id or amount for card sale");
                Toast.makeText(this, "MQTT card sale missing request_id/amount", Toast.LENGTH_LONG).show();
                return;
            }
            if (mqttTxnPending || bankLaunchPending) {
                MqttLog.e("MQTT: Sale already in progress, ignoring new request");
                Toast.makeText(this, "MQTT sale already in progress", Toast.LENGTH_SHORT).show();
                return;
            }

            MqttLog.i("MQTT: Amount " + amountRaw + " -> bank amount " + amountForBank);
            startMqttCardSale(requestId, amountForBank, phone, terminalId);
        } catch (Exception e) {
            MqttLog.e("MQTT: Failed to handle inbound message", e);
            Toast.makeText(this, "Invalid MQTT JSON", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * MQTT gives amount already ×100 (send 1 -> receive 100).
     * Divide by 100 before bank call so bank gets 1.
     */
    private String convertMqttAmountForBank(String amountRaw) {
        if (amountRaw == null || amountRaw.trim().isEmpty()) {
            return "";
        }
        try {
            java.math.BigDecimal raw = new java.math.BigDecimal(amountRaw.trim());
            java.math.BigDecimal bankAmount = raw
                    .divide(new java.math.BigDecimal("100"), 0, java.math.RoundingMode.HALF_UP);
            if (bankAmount.compareTo(java.math.BigDecimal.ZERO) <= 0) {
                return "";
            }
            return bankAmount.toPlainString();
        } catch (Exception e) {
            MqttLog.e("MQTT: Invalid amount: " + amountRaw, e);
            return "";
        }
    }

    private void startMqttCardSale(String requestId, String amount, String phone, String terminalId) {
        mqttTxnPending = true;
        mqttRequestId = requestId;
        mqttAmount = amount;
        mqttPhone = phone;
        mqttTerminalId = terminalId;
        mqttReplyTopic = "mqtt/" + deviceSerialNumber + "/response";

        tranType = TxnConstants.SALE;
        topupAmount = parseAmountSafe(amount);
        OPERATOR_ORDER_ID = requestId;
        SOURCE_TXN_ID = requestId;
        UDF1 = phone;
        UDF2 = terminalId;
        UDF3 = deviceSerialNumber;

        try {
            clearPaymentEnquiryTimers();
            awaitingEnquiryAfterPaymentTimeout = false;
            hardAbortSentToTvm = false;

            Intent bankIntent = abpBank.createBankAppIntent(
                    TxnConstants.SALE,
                    amount,
                    SOURCE_TXN_ID,
                    "",
                    OPERATOR_ORDER_ID,
                    "NA",
                    "NA",
                    "0",
                    "0",
                    "NA",
                    "NA",
                    "NA",
                    "NA",
                    UDF1,
                    UDF2,
                    UDF3,
                    "",
                    ""
            );

            if (bankIntent == null) {
                MqttLog.e("MQTT: Bank Intent NULL for card sale");
                AppLogger.trxn_log(this, StatusConstants.ERR_BANK_INTENT_NULL_LOG,
                        "MQTT Bank Intent NULL for request_id=" + requestId);
                publishMqttSaleResult(false, "Bank Intent NULL", null);
                return;
            }

            MqttLog.i("MQTT: Launching BANK Sale request_id=" + requestId + " amount=" + amount);
            startPayment(bankIntent);
        } catch (Exception e) {
            MqttLog.e("MQTT: Failed to start card sale", e);
            publishMqttSaleResult(false, e.getMessage(), null);
        }
    }

    private long parseAmountSafe(String amount) {
        try {
            if (amount == null || amount.trim().isEmpty()) {
                return 0L;
            }
            return new java.math.BigDecimal(amount.trim())
                    .setScale(0, java.math.RoundingMode.HALF_UP)
                    .longValue();
        } catch (Exception e) {
            return 0L;
        }
    }

    private void publishMqttSaleResult(boolean success, String message, Intent bankData) {
        try {
            JSONObject response = new JSONObject();
            response.put("request_id", mqttRequestId);
            response.put("amount", mqttAmount);
            response.put("phone", mqttPhone);
            response.put("terminalid", mqttTerminalId);
            response.put("status", success ? "SUCCESS" : "FAILED");
            response.put("message", message == null ? "" : message);
            response.put("device_serial", deviceSerialNumber);

            if (bankData != null && bankData.getExtras() != null) {
                String result = bankData.getExtras().getString(JsonKeys.RESULT);
                if (result != null) {
                    String clean = result
                            .replace(BankConstants.STX, "")
                            .replace(BankConstants.ETX, "")
                            .trim();
                    try {
                        response.put("bank_result", new JSONObject(clean));
                    } catch (Exception ignore) {
                        response.put("bank_result_raw", clean);
                    }
                }
            }

            String payload = response.toString();
            AppLogger.trxn_log(this, BankConstants.MQTT_OUTBOUND, payload);
            MqttLog.i("MQTT_OUTBOUND: " + payload);

            if (mqttManager != null && mqttManager.isConnected()) {
                boolean published = mqttManager.publish(mqttReplyTopic, payload);
                MqttLog.i("MQTT: Published sale response to " + mqttReplyTopic + " ok=" + published);
                Toast.makeText(this,
                        published ? "MQTT response published" : "MQTT publish failed",
                        Toast.LENGTH_SHORT).show();
            } else {
                MqttLog.e("MQTT: Cannot publish response, client not connected");
            }
        } catch (Exception e) {
            MqttLog.e("MQTT: Failed to publish sale response", e);
        } finally {
            mqttTxnPending = false;
        }
    }

    private void maybePublishMqttBankResult(boolean success, Intent bankData, String fallbackMessage) {
        if (!IntegrationModeStore.isCloud(this) || !mqttTxnPending) {
            return;
        }
        String msg = fallbackMessage;
        if (bankData != null && bankData.getExtras() != null) {
            try {
                String result = bankData.getExtras().getString(JsonKeys.RESULT);
                if (result != null) {
                    String clean = result
                            .replace(BankConstants.STX, "")
                            .replace(BankConstants.ETX, "")
                            .trim();
                    JSONObject root = new JSONObject(clean);
                    msg = root.optString(JsonKeys.STATUS_MSG, fallbackMessage);
                    String statusCode = root.optString(JsonKeys.STATUS_CODE, "");
                    if (!StatusConstants.STATUS_OK.equals(statusCode)) {
                        success = false;
                    }
                }
            } catch (Exception ignore) {
                // keep fallback
            }
        }
        publishMqttSaleResult(success, msg, bankData);
    }

    private void showMqttReceivedPopup(String topic, String message) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        if (mqttMessageDialog != null && mqttMessageDialog.isShowing()) {
            mqttMessageDialog.dismiss();
        }

        String displayData = message == null || message.trim().isEmpty()
                ? "(empty message)"
                : message;

        mqttMessageDialog = new AlertDialog.Builder(this)
                .setTitle("MQTT Card Request")
                .setMessage("Topic:\n" + topic + "\n\nNormalized Data:\n" + displayData)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> dialog.dismiss())
                .setCancelable(true)
                .create();
        mqttMessageDialog.show();
    }

    private String timeStamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
    }

    @Override
    protected void onResume() {
        super.onResume();

        iDalSetting(false);
        hideSystemUI();
        registerNetworkCallback();
        onNetworkStatusChanged(isInternetAvailable());
        if (isInternetAvailable()) {
            initAuthFlow();
        }

        try {
            IntentFilter filter = new IntentFilter();
            filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
            filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
            registerReceiver(usbReceiver, filter);

            if (IntegrationModeStore.isUsb(this) && uartManager != null) {
                uartManager.connect();
            }
        } catch (Exception e) {
            Log.e("UART", "UART open/send error", e);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);

        if (hasFocus) {
            hideSystemUI();
        }
    }
}