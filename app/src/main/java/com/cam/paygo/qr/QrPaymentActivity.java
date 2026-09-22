package com.cam.paygo.qr;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.cam.mqtt.MqttLog;
import com.cam.paygo.R;
import com.cam.paygo.constants.QrTypeConstants;
import com.google.android.material.button.MaterialButton;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import com.journeyapps.barcodescanner.BarcodeEncoder;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Full-screen QR payment UI (ANY qr_type).
 * Top: brand + amount, Center: QR / result icon, Bottom: configurable expiry timer.
 */
public class QrPaymentActivity extends AppCompatActivity {

    private static final String TAG = "QrPaymentActivity";

    public static final String EXTRA_ORDER_SN = "extra_order_sn";
    public static final String EXTRA_AMOUNT = "extra_amount";
    public static final String EXTRA_REQUEST_ID = "extra_request_id";
    public static final String EXTRA_DATETIME = "extra_datetime";
    public static final String EXTRA_CTIME = "extra_ctime";
    public static final String EXTRA_RESULT_STATUS = "extra_result_status";

    public static final String RESULT_SUCCESS = "success";
    public static final String RESULT_FAILED = "failed";

    /** True while this screen is in foreground (used by MQTT success routing). */
    private static volatile boolean sVisible = false;

    private TextView tvTitle;
    private TextView tvSubtitle;
    private TextView tvAmount;
    private TextView tvRequestId;
    private TextView tvTimer;
    private TextView tvTimerLabel;
    private TextView tvResultMessage;
    private TextView tvResultHint;
    private ImageView ivQrCode;
    private ImageView ivResultIcon;
    private ProgressBar pbTimer;
    private MaterialButton btnClose;
    private View qrCenterSection;
    private LinearLayout qrResultSection;

    @Nullable
    private CountDownTimer countDownTimer;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private boolean resultHandled = false;

    public static boolean isVisible() {
        return sVisible;
    }

    public static Intent createIntent(
            Context context,
            String orderSn,
            String amount,
            String requestId,
            String datetime,
            String ctime
    ) {
        Intent intent = new Intent(context, QrPaymentActivity.class);
        intent.putExtra(EXTRA_ORDER_SN, orderSn);
        intent.putExtra(EXTRA_AMOUNT, amount);
        intent.putExtra(EXTRA_REQUEST_ID, requestId);
        intent.putExtra(EXTRA_DATETIME, datetime);
        intent.putExtra(EXTRA_CTIME, ctime);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return intent;
    }

    public static Intent createResultIntent(Context context, String status, String requestId, String amount) {
        Intent intent = new Intent(context, QrPaymentActivity.class);
        intent.putExtra(EXTRA_RESULT_STATUS, status);
        intent.putExtra(EXTRA_REQUEST_ID, requestId);
        intent.putExtra(EXTRA_AMOUNT, amount);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_qr_payment);
        hideSystemUi();

        bindViews();
        bindCloseAction();
        applyIntentData(getIntent());
    }

    @Override
    protected void onStart() {
        super.onStart();
        sVisible = true;
    }

    @Override
    protected void onStop() {
        sVisible = false;
        super.onStop();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        applyIntentData(intent);
    }

    private void applyIntentData(@Nullable Intent intent) {
        if (intent == null) {
            Toast.makeText(this, R.string.qr_missing_data, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        String resultStatus = safeExtra(intent, EXTRA_RESULT_STATUS);
        if (RESULT_SUCCESS.equalsIgnoreCase(resultStatus)) {
            handlePaymentSuccess(safeExtra(intent, EXTRA_REQUEST_ID), safeExtra(intent, EXTRA_AMOUNT));
            return;
        }
        if (RESULT_FAILED.equalsIgnoreCase(resultStatus)) {
            handlePaymentFailed(false);
            return;
        }

        if (resultHandled) {
            MqttLog.d("QR Activity: ignoring new QR data after result already handled");
            return;
        }

        String orderSn = safeExtra(intent, EXTRA_ORDER_SN);
        String amount = safeExtra(intent, EXTRA_AMOUNT);
        String requestId = safeExtra(intent, EXTRA_REQUEST_ID);

        if (orderSn.isEmpty()) {
            MqttLog.e("QR Activity: missing order_sn");
            Toast.makeText(this, R.string.qr_missing_data, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        showWaitingUi(amount, requestId);

        Bitmap qrBitmap = generateQrBitmap(orderSn, QrTypeConstants.QR_BITMAP_SIZE_PX);
        if (qrBitmap == null) {
            MqttLog.e("QR Activity: encode failed for order_sn=" + orderSn);
            Toast.makeText(this, R.string.qr_generate_failed, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        ivQrCode.setImageBitmap(qrBitmap);

        MqttLog.i("QR Activity: showing QR order_sn=" + orderSn
                + " amount=" + amount
                + " request_id=" + requestId
                + " timeoutMs=" + QrTypeConstants.QR_PAYMENT_TIMEOUT_MS);
        startExpiryTimer();
    }

    private void showWaitingUi(String amount, String requestId) {
        tvTitle.setText(R.string.qr_scan_to_pay);
        tvSubtitle.setText(R.string.qr_scan_hint);
        tvTitle.setTextColor(getColor(R.color.text_dark_navy));
        tvAmount.setText(getString(R.string.qr_amount_format, amount.isEmpty() ? "--" : amount));
        tvRequestId.setText(getString(R.string.qr_request_format,
                requestId.isEmpty() ? "--" : requestId));

        if (qrCenterSection != null) {
            qrCenterSection.setVisibility(View.VISIBLE);
        }
        ivQrCode.setVisibility(View.VISIBLE);
        qrResultSection.setVisibility(View.GONE);

        pbTimer.setVisibility(View.VISIBLE);
        tvTimer.setVisibility(View.VISIBLE);
        tvTimerLabel.setVisibility(View.VISIBLE);
        btnClose.setEnabled(true);
    }

    private void handlePaymentSuccess(String requestId, String amount) {
        if (resultHandled) {
            return;
        }
        resultHandled = true;
        cancelTimer();
        uiHandler.removeCallbacksAndMessages(null);

        if (!amount.isEmpty()) {
            tvAmount.setText(getString(R.string.qr_amount_format, amount));
        }
        if (!requestId.isEmpty()) {
            tvRequestId.setText(getString(R.string.qr_request_format, requestId));
        }

        tvTitle.setText(R.string.qr_payment_success);
        tvTitle.setTextColor(getColor(R.color.colorGreen));
        tvSubtitle.setText(R.string.qr_payment_success_hint);

        showCenterResult(
                R.drawable.ic_qr_success_check,
                R.string.qr_payment_success,
                R.string.qr_payment_success_hint,
                R.color.colorGreen
        );

        MqttLog.i("QR Activity: payment SUCCESS request_id=" + requestId
                + " displayMs=" + QrTypeConstants.QR_RESULT_DISPLAY_MS);
        Toast.makeText(this, R.string.qr_payment_success, Toast.LENGTH_SHORT).show();
        uiHandler.postDelayed(this::finishSafely, QrTypeConstants.QR_RESULT_DISPLAY_MS);
    }

    private void handlePaymentFailed(boolean fromTimeout) {
        if (resultHandled) {
            return;
        }
        resultHandled = true;
        cancelTimer();
        uiHandler.removeCallbacksAndMessages(null);

        int hintRes = fromTimeout
                ? R.string.qr_payment_timeout_failed
                : R.string.qr_payment_failed_hint;

        tvTitle.setText(R.string.qr_payment_failed);
        tvTitle.setTextColor(getColor(R.color.colorRed));
        tvSubtitle.setText(hintRes);

        showCenterResult(
                R.drawable.ic_qr_failed_cross,
                R.string.qr_payment_failed,
                hintRes,
                R.color.colorRed
        );

        MqttLog.i("QR Activity: payment FAILED timeout=" + fromTimeout
                + " displayMs=" + QrTypeConstants.QR_RESULT_DISPLAY_MS);
        Toast.makeText(this,
                fromTimeout ? R.string.qr_payment_timeout_failed : R.string.qr_payment_failed,
                Toast.LENGTH_SHORT).show();
        uiHandler.postDelayed(this::finishSafely, QrTypeConstants.QR_RESULT_DISPLAY_MS);
    }

    private void showCenterResult(int iconRes, int messageRes, int hintRes, int messageColorRes) {
        if (qrCenterSection != null) {
            qrCenterSection.setVisibility(View.VISIBLE);
        }
        ivQrCode.setVisibility(View.GONE);
        qrResultSection.setVisibility(View.VISIBLE);

        ivResultIcon.setImageResource(iconRes);
        tvResultMessage.setText(messageRes);
        tvResultMessage.setTextColor(getColor(messageColorRes));
        tvResultHint.setText(hintRes);

        pbTimer.setVisibility(View.GONE);
        tvTimer.setVisibility(View.GONE);
        tvTimerLabel.setVisibility(View.GONE);
        btnClose.setEnabled(false);
    }

    private void finishSafely() {
        if (!isFinishing() && !isDestroyed()) {
            finish();
        }
    }

    private void bindViews() {
        tvTitle = findViewById(R.id.tv_qr_title);
        tvSubtitle = findViewById(R.id.tv_qr_subtitle);
        tvAmount = findViewById(R.id.tv_qr_amount);
        tvRequestId = findViewById(R.id.tv_qr_request_id);
        tvTimer = findViewById(R.id.tv_qr_timer);
        tvTimerLabel = findViewById(R.id.tv_qr_timer_label);
        tvResultMessage = findViewById(R.id.tv_qr_result_message);
        tvResultHint = findViewById(R.id.tv_qr_result_hint);
        ivQrCode = findViewById(R.id.iv_qr_code);
        ivResultIcon = findViewById(R.id.iv_qr_result_icon);
        pbTimer = findViewById(R.id.pb_qr_timer);
        btnClose = findViewById(R.id.btn_qr_close);
        qrCenterSection = findViewById(R.id.qr_center_section);
        qrResultSection = findViewById(R.id.qr_result_section);

        int totalSec = (int) (QrTypeConstants.QR_PAYMENT_TIMEOUT_MS / QrTypeConstants.QR_TIMER_TICK_MS);
        pbTimer.setMax(totalSec);
        pbTimer.setProgress(totalSec);
        tvTimer.setText(formatTime(QrTypeConstants.QR_PAYMENT_TIMEOUT_MS));
    }

    private void bindCloseAction() {
        btnClose.setOnClickListener(v -> {
            if (resultHandled) {
                return;
            }
            MqttLog.i("QR Activity: closed by user");
            finish();
        });
    }

    private void startExpiryTimer() {
        cancelTimer();
        resultHandled = false;
        final long timeoutMs = QrTypeConstants.QR_PAYMENT_TIMEOUT_MS;
        final long tickMs = QrTypeConstants.QR_TIMER_TICK_MS;

        countDownTimer = new CountDownTimer(timeoutMs, tickMs) {
            @Override
            public void onTick(long millisUntilFinished) {
                if (resultHandled) {
                    return;
                }
                tvTimer.setText(formatTime(millisUntilFinished));
                int remainingSec = (int) Math.ceil(millisUntilFinished / 1000.0);
                pbTimer.setProgress(remainingSec);

                if (millisUntilFinished <= 30_000L) {
                    tvTimer.setTextColor(getColor(R.color.colorDarkOrange));
                } else {
                    tvTimer.setTextColor(getColor(R.color.text_dark_navy));
                }
            }

            @Override
            public void onFinish() {
                if (resultHandled) {
                    return;
                }
                tvTimer.setText(formatTime(0));
                pbTimer.setProgress(0);
                MqttLog.i("QR Activity: expired after timeoutMs=" + timeoutMs + " — payment failed");
                handlePaymentFailed(true);
            }
        };
        countDownTimer.start();
    }

    private String formatTime(long millis) {
        long totalSec = Math.max(0, TimeUnit.MILLISECONDS.toSeconds(millis));
        long minutes = totalSec / 60;
        long seconds = totalSec % 60;
        return String.format(Locale.US, "%02d:%02d", minutes, seconds);
    }

    @Nullable
    private Bitmap generateQrBitmap(String data, int sizePx) {
        try {
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.MARGIN, 1);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            BitMatrix matrix = new MultiFormatWriter()
                    .encode(data, BarcodeFormat.QR_CODE, sizePx, sizePx, hints);
            return new BarcodeEncoder().createBitmap(matrix);
        } catch (Exception e) {
            Log.e(TAG, "QR encode failed", e);
            return null;
        }
    }

    private String safeExtra(Intent intent, String key) {
        if (intent == null) {
            return "";
        }
        String value = intent.getStringExtra(key);
        return value == null ? "" : value.trim();
    }

    private void hideSystemUi() {
        View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        );
    }

    private void cancelTimer() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
            countDownTimer = null;
        }
    }

    @Override
    protected void onDestroy() {
        cancelTimer();
        uiHandler.removeCallbacksAndMessages(null);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        sVisible = false;
        super.onDestroy();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUi();
        }
    }
}
