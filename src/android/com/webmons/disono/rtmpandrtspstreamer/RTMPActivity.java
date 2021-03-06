package com.webmons.disono.rtmpandrtspstreamer;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.view.SurfaceView;
import android.widget.ImageButton;
import android.widget.Toast;

import com.pedro.encoder.input.video.Camera1ApiManager;
import com.pedro.encoder.input.video.CameraOpenException;
import com.pedro.rtplibrary.rtmp.RtmpCamera1;

import net.ossrs.rtmp.ConnectCheckerRtmp;

import org.apache.cordova.CordovaActivity;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Author: Archie, Disono (webmonsph@gmail.com)
 * Website: http://www.webmons.com
 *
 * Created at: 1/09/2018
 */

public class RTMPActivity extends CordovaActivity implements ConnectCheckerRtmp {
    SurfaceView surfaceView;
    private RtmpCamera1 rtmpCameral;
    private Activity activity;

    private String _url = null;
    private String _username = null;
    private String _password = null;

    private ImageButton ic_torch;
    private ImageButton ic_switch_camera;
    private ImageButton ic_broadcast;
    private Camera1ApiManager camera1ApiManager;
    private boolean isFlashOn = false;
    private boolean isStreamingOn = false;

    BroadcastReceiver br = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null) {
                String method = intent.getStringExtra("method");

                if (method != null) {
                    switch (method) {
                        case "stop":
                            _stopStreaming();
                            break;
                    }
                }
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activity = this.cordovaInterface.getActivity();
        setContentView(_getResource("rtsp_rtmp_streamer", "layout"));

        Intent intent = getIntent();
        _username = intent.getStringExtra("username");
        _password = intent.getStringExtra("password");
        _url = intent.getStringExtra("url");

        _UIListener();
        _broadcastRCV();
    }

    @Override
    public void onPause() {
        super.onPause();
        _stopStreaming();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        activity.unregisterReceiver(br);
        _stopStreaming();
    }

    @Override
    public void onConnectionSuccessRtmp() {
        VideoStream.sendBroadCast(activity, "onConnectionSuccess");
        runOnUiThread(() -> Toast.makeText(RTMPActivity.this, "Connection success", Toast.LENGTH_SHORT)
                .show());
    }

    @Override
    public void onConnectionFailedRtmp(final String reason) {
        VideoStream.sendBroadCast(activity, "onConnectionFailed");
        runOnUiThread(() -> {
            Toast.makeText(RTMPActivity.this, "Connection failed. " + reason,
                    Toast.LENGTH_SHORT).show();
            _stopStreaming();
        });
    }

    @Override
    public void onDisconnectRtmp() {
        VideoStream.sendBroadCast(activity, "onDisconnect");
        runOnUiThread(() -> {
            Toast.makeText(RTMPActivity.this, "Disconnected", Toast.LENGTH_SHORT).show();
            _stopStreaming();
        });
    }

    @Override
    public void onAuthErrorRtmp() {
        VideoStream.sendBroadCast(activity, "onAuthError");
        runOnUiThread(() -> {
            Toast.makeText(RTMPActivity.this, "Auth error", Toast.LENGTH_SHORT).show();
            _stopStreaming();
        });
    }

    @Override
    public void onAuthSuccessRtmp() {
        VideoStream.sendBroadCast(activity, "onAuthSuccess");
        runOnUiThread(() -> Toast.makeText(RTMPActivity.this, "Auth success", Toast.LENGTH_SHORT).show());
    }

    private void _broadcastRCV() {
        IntentFilter filter = new IntentFilter(VideoStream.BROADCAST_FILTER);
        activity.registerReceiver(br, filter);
    }

    private void _UIListener() {
        surfaceView = findViewById(_getResource("rtmp_rtsp_stream_surfaceView", "id"));
        rtmpCameral = new RtmpCamera1(surfaceView, this);
        camera1ApiManager = new Camera1ApiManager(surfaceView, rtmpCameral);

        ic_torch = findViewById(_getResource("ic_torch", "id"));
        ic_torch.setOnClickListener(v -> _toggleFlash());

        ic_switch_camera = findViewById(_getResource("ic_switch_camera", "id"));
        ic_switch_camera.setOnClickListener(v -> _toggleCameraFace());

        ic_broadcast = findViewById(_getResource("ic_broadcast", "id"));
        ic_broadcast.setOnClickListener(v -> _toggleStreaming());
    }

    private void _toggleStreaming() {
        if (!rtmpCameral.isStreaming()) {
            _startStreaming();
        } else {
            _stopStreaming();
        }
    }

    private void _startStreaming() {
        rtmpCameral.setAuthorization(_username, _password);

        if (rtmpCameral.prepareAudio() && rtmpCameral.prepareVideo()) {
            rtmpCameral.startStream(_url);
            isStreamingOn = true;

            VideoStream.sendBroadCast(activity, "onStartStream");
        } else {
            Toast.makeText(activity, "Error preparing stream, This device cant do it.", Toast.LENGTH_SHORT)
                    .show();
            isStreamingOn = false;

            JSONObject obj = new JSONObject();
            try {
                obj.put("message", "Error preparing stream, This device cant do it.");
                VideoStream.sendBroadCast(activity, "onError", obj);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        _toggleBtnImgVideo();
    }

    private void _stopStreaming() {
        VideoStream.sendBroadCast(activity, "onStopStream");

        if (rtmpCameral.isStreaming()) {
            rtmpCameral.stopStream();
            rtmpCameral.stopPreview();

            isStreamingOn = false;
            _toggleBtnImgVideo();

            if (camera1ApiManager.isLanternEnable()) {
                camera1ApiManager.disableLantern();

                isFlashOn = false;
                _toggleBtnImgFlash();
            }
        }
    }

    private void _toggleFlash() {
        if (!isFlashOn && !camera1ApiManager.isLanternEnable()) {
            camera1ApiManager.enableLantern();
            isFlashOn = true;
        } else {
            camera1ApiManager.disableLantern();
            isFlashOn = false;
        }

        // changing button/switch image
        _toggleBtnImgFlash();
    }

    private void _toggleBtnImgFlash() {
        String icon = (!isFlashOn) ? "ic_flash_on_white_36dp" : "ic_flash_off_white_36dp";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ic_torch.setImageDrawable(getDrawable(_getResource(icon, "drawable")));
        } else {
            ic_torch.setImageResource(_getResource(icon, "drawable"));
        }
    }

    private void _toggleBtnImgVideo() {
        String icon = (!isStreamingOn) ? "ic_videocam_white_36dp" : "ic_videocam_off_white_36dp";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ic_broadcast.setImageDrawable(getDrawable(_getResource(icon, "drawable")));
        } else {
            ic_broadcast.setImageResource(_getResource(icon, "drawable"));
        }
    }

    private void _toggleCameraFace() {
        try {
            rtmpCameral.switchCamera();
        } catch (CameraOpenException e) {
            Toast.makeText(activity, e.getMessage(), Toast.LENGTH_SHORT).show();
            rtmpCameral.switchCamera();
        }
    }

    private int _getResource(String name, String type) {
        String package_name = getApplication().getPackageName();
        Resources resources = getApplication().getResources();
        return resources.getIdentifier(name, type, package_name);
    }
}
