package com.example.cameraxjava;

import static java.nio.charset.StandardCharsets.UTF_8;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.extensions.HdrImageCaptureExtender;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.Image;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Size;

import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.AdvertisingOptions;
import com.google.android.gms.nearby.connection.ConnectionInfo;
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback;
import com.google.android.gms.nearby.connection.ConnectionResolution;
import com.google.android.gms.nearby.connection.ConnectionsClient;
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo;
import com.google.android.gms.nearby.connection.DiscoveryOptions;
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback;
import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.PayloadCallback;
import com.google.android.gms.nearby.connection.PayloadTransferUpdate;
import com.google.android.gms.nearby.connection.Strategy;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    public String transfer = "File Received!!";
    private boolean isConnected = false;
    private static final String TAG = "RockPaperScissors";
    private static final Strategy STRATEGY = Strategy.P2P_STAR;
    TextView textView;
    public int second = 0;
    public int frameRate = 1;
    public String endpoint ="";
    int totalFramesSent = 0;


    public static final String SERVICE_ID = "120001";

    private ConnectionsClient connectionsClient;

    private Executor executor = Executors.newSingleThreadExecutor();
    private int REQUEST_CODE_PERMISSIONS = 1001;
    private final String[] REQUIRED_PERMISSIONS = new String[]{"android.permission.CAMERA", "android.permission.WRITE_EXTERNAL_STORAGE", "android.permission.BLUETOOTH",
            "android.permission.BLUETOOTH_ADMIN",
            "android.permission.ACCESS_WIFI_STATE",
            "android.permission.CHANGE_WIFI_STATE",
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.READ_EXTERNAL_STORAGE"};


    PreviewView mPreviewView;
    ImageView captureImage;

    private final PayloadCallback payloadCallback =
            new PayloadCallback() {
                @Override
                public void onPayloadReceived(String endpointId, Payload payload) {
                    if (payload.getType() == Payload.Type.BYTES) {
                        String payloadFilenameMessage = new String(payload.asBytes(), StandardCharsets.UTF_8);
                        Log.i(TAG, "Bytes received are:" + payloadFilenameMessage);
                        if (payloadFilenameMessage.length() >= 9) {
                            if(payloadFilenameMessage.substring(0,9).equals("FrameRate")){
                                String[] splitArr = payloadFilenameMessage.split(":");
                                frameRate = Integer.parseInt(splitArr[1]);
                                second++;
                                Log.i(TAG,"second is:"+second+"framerate is"+frameRate);
                                loadImageFromStorage("/storage/emulated/0/Pictures/Data/b3a84a0f-0fb529e1-opencv",second,frameRate);
                            }

                        }

                    }
                }

                @Override
                public void onPayloadTransferUpdate(String endpointId, PayloadTransferUpdate update) {
                    if (update.getStatus() == PayloadTransferUpdate.Status.SUCCESS) {
                        //Log.i(TAG, "Payload sent successfully");
                    }
                    if (update.getStatus() == PayloadTransferUpdate.Status.FAILURE) {
                        //Log.i(TAG, "Transfer Failed");
                    }
                    if (update.getStatus() == PayloadTransferUpdate.Status.IN_PROGRESS) {
                        //Log.i(TAG, "Transfer in progress");
                    }
                    if (update.getStatus() == PayloadTransferUpdate.Status.CANCELED) {
                        //Log.i(TAG, "Transfer cancelled");
                    }

                }
            };

    private final EndpointDiscoveryCallback endpointDiscoveryCallback =
            new EndpointDiscoveryCallback() {
                @Override
                public void onEndpointFound(String endpointId, DiscoveredEndpointInfo info) {
                    Log.i(TAG, "onEndpointFound: endpoint found, connecting");

                    connectionsClient.requestConnection("Dashcam", endpointId, connectionLifecycleCallback);
                }

                @Override
                public void onEndpointLost(String endpointId) {
                }
            };

    private final ConnectionLifecycleCallback connectionLifecycleCallback =
            new ConnectionLifecycleCallback() {
                @Override
                public void onConnectionInitiated(String endpointId, ConnectionInfo connectionInfo) {

                    Log.i(TAG, "onConnectionInitiated: accepting connection");
                    connectionsClient.acceptConnection(endpointId, payloadCallback);
                    //opponentName = connectionInfo.getEndpointName();
                }

                @Override
                public void onConnectionResult(String endpointId, ConnectionResolution result) {
                    if (result.getStatus().isSuccess()) {
                        Log.i(TAG, "onConnectionResult: connection successful, connected to" + endpointId);
                        startCamera(endpointId);
                        connectionsClient.stopDiscovery();
                        connectionsClient.stopAdvertising();
                        isConnected = true;
                        endpoint = endpointId;

                    } else {
                        Log.i(TAG, "onConnectionResult: connection failed");
                    }
                }

                @Override
                public void onDisconnected(@NonNull String endPointId) {
                    Log.i(TAG, "onDisconnected: disconnected from the opponent");
                    isConnected = false;
                }
            };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        connectionsClient = Nearby.getConnectionsClient(this);
        mPreviewView = findViewById(R.id.previewView);
        captureImage = findViewById(R.id.captureImg);

        if (allPermissionsGranted()) {
            startDiscovery();

            //start camera if permission has been granted by user
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }

    }

    @Override
    protected void onStop() {
        connectionsClient.stopAllEndpoints();

        super.onStop();
    }

    private void startDiscovery() {
        // Note: Discovery may fail. To keep this demo simple, we don't handle failures.
        connectionsClient.startDiscovery(
                SERVICE_ID, endpointDiscoveryCallback,
                new DiscoveryOptions.Builder().setStrategy(STRATEGY).build());

        Log.i(TAG, "Discovery started");
    }

    private void startAdvertising() {
        // Note: Advertising may fail. To keep this demo simple, we don't handle failures.
        connectionsClient.startAdvertising(
                "DeviceA", SERVICE_ID, connectionLifecycleCallback,
                new AdvertisingOptions.Builder().setStrategy(STRATEGY).build());
    }

    private void startCamera(String endPointId) {

        final ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(new Runnable() {
            @Override
            public void run() {
                try {

                    ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                    bindPreview(cameraProvider, endPointId);

                } catch (ExecutionException | InterruptedException e) {
                    // No errors need to be handled for this Future.
                    // This should never be reached.
                }
            }
        }, ContextCompat.getMainExecutor(this));
    }

    void bindPreview(@NonNull ProcessCameraProvider cameraProvider, String endPointId) {

        Preview preview = new Preview.Builder()
                .build();

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .build();

        ImageCapture.Builder builder = new ImageCapture.Builder();


        final ImageCapture imageCapture = builder
                .setTargetRotation(this.getWindowManager().getDefaultDisplay().getRotation())
                .setTargetResolution(new Size(640, 640))
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build();

        preview.setSurfaceProvider(mPreviewView.createSurfaceProvider());

        Camera camera = cameraProvider.bindToLifecycle((LifecycleOwner) this, cameraSelector, preview, imageAnalysis, imageCapture);


        captureImage.setOnClickListener(v ->
        {
            Date date = new Date();
            File file = new File(getBatchDirectoryName(), date.getTime() + ".jpg");

            ImageCapture.OutputFileOptions outputFileOptions = new ImageCapture.OutputFileOptions.Builder(file).build();
            imageCapture.takePicture(outputFileOptions, executor, new ImageCapture.OnImageSavedCallback() {
                        @Override
                        public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                            new Handler(Looper.getMainLooper()).post(new Runnable() {
                                @Override
                                public void run() {
                                    //Toast.makeText(MainActivity.this, "Image Saved successfully", Toast.LENGTH_SHORT).show();
                                    sendFile(file, endPointId);
                                }
                            });
                        }

                        @Override
                        public void onError(@NonNull ImageCaptureException error) {
                            error.printStackTrace();
                        }
                    }
            );
        });

    }

    public String getBatchDirectoryName() {

        String app_folder_path = "";
        app_folder_path = Environment.getExternalStorageDirectory().toString() + "/images";
        File dir = new File(app_folder_path);
        if (!dir.exists() && !dir.mkdirs()) {

        }

        return app_folder_path;
    }

    private boolean allPermissionsGranted() {

        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                super.onStart();

            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show();
                this.finish();
            }
        }
    }

    private void sendFile(File fileToSend, String endPointId) {
        //Uri uri = Uri.parse(new File("/storage/emulated/0/DCIM/Camera/PXL_20220817_233127382.MP.jpg").toString());

        Uri uri = Uri.fromFile(fileToSend);
        Payload filePayload = null;


        try {
            ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(uri, "r");
            if (pfd != null) {
                filePayload = Payload.fromFile(pfd);
            }
        } catch (FileNotFoundException e) {
            Log.i(TAG, String.format("sendFile ParcelFileDescriptor error: \n%s", e.getMessage()));
        }

        if (filePayload == null) {
            Log.i(TAG, String.format("Could not create file payload for %s"));
            return;
        }
        // Construct a simple message mapping the ID of the file payload to the desired filename.
        String filenameMessage = filePayload.getId() + ":" + uri.getLastPathSegment();


        // Send the filename message as a bytes payload.
        Payload filenameBytesPayload =
                Payload.fromBytes(filenameMessage.getBytes(StandardCharsets.UTF_8));
        connectionsClient.sendPayload(endPointId, filenameBytesPayload);

        // Finally, send the file payload.
        connectionsClient.sendPayload(endPointId, filePayload);

        // Log.i(TAG, "Send file method called to " + endPointId + " at " + System.currentTimeMillis());

    }

    public void testFrames(ArrayList<File> list,int frameRate) {
        totalFramesSent +=frameRate;
        Log.i(TAG,"Total frames sent:"+totalFramesSent);
        int[] i = {0};
        int frameSkipRate = list.size()/frameRate;
        int x = 1000/frameRate;
        Timer timer = new Timer();
        TimerTask DynamicFrameRate = new TimerTask(){
            @Override
            public void run(){
                if(i[0]<list.size()) {
                    sendFile(list.get(i[0]), endpoint);
                    i[0] = i[0] + frameSkipRate;
                }
            }
        };
        timer.schedule(DynamicFrameRate,0,x);
    }

    private void loadImageFromStorage(String path,int second,int frameRate) {
        ArrayList<File> framesToSend = new ArrayList<>();
        float frameModulus = 20/frameRate;
        int frameSkipRate = Math.round(frameModulus);
        final File file = new File(path);
        for ( File child : file.listFiles()) {
            String name = child.getName();
            String[] splitArr = name.split("-");
            String temp = splitArr[2];
            String[] secondArray = temp.split("\\.");

            if(Integer.parseInt(secondArray[0]) == second){
                framesToSend.add(child);
                Log.i(TAG,child.getName()+"is added");
            }

        }
        Log.i(TAG,"size of list is"+framesToSend.size());
        testFrames(framesToSend,frameRate);
    }
}
/***
 try {
 File f=new File(path, "profile.jpg");

 }
 catch (FileNotFoundException e)
 {
 e.printStackTrace();
 }
 ***/




/***
 * new Handler(Looper.getMainLooper()).post(new Runnable() {
 *                             @Override
 *                             public void run() {
 *                                 Uri uri = outputFileResults.getSavedUri();
 *                                 if(isConnected){
 *                                     sendFile(uri);
 *                                 }
 *                                 Toast.makeText(MainActivity.this, "Image Saved successfully", Toast.LENGTH_SHORT).show();
 *                             }
 *                         });
 *
 *
 *
 *
 *
 *
 *
 *   Timer timer = new Timer();
 *         TimerTask tt = new TimerTask() {
 *             @Override
 *             public void run() {
 *                 Date date = new Date();
 *                 File file = new File(getBatchDirectoryName(), date.getTime() + ".jpg");
 *
 *                 ImageCapture.OutputFileOptions outputFileOptions = new ImageCapture.OutputFileOptions.Builder(file).build();
 *                 imageCapture.takePicture(outputFileOptions, executor, new ImageCapture.OnImageSavedCallback() {
 *                             @Override
 *                             public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
 *                                 new Handler(Looper.getMainLooper()).post(new Runnable() {
 *                                     @Override
 *                                     public void run() {
 *                                         //Toast.makeText(MainActivity.this, "Image Saved successfully", Toast.LENGTH_SHORT).show();
 *                                         sendFile(file, endPointId);
 *                                     }
 *                                 });
 *                             }
 *
 *                             @Override
 *                             public void onError(@NonNull ImageCaptureException error) {
 *                                 error.printStackTrace();
 *                             }
 *                         }
 *                 );
 *             }
 *         };
 *
 *         timer.schedule(tt, 0, 100);
 */
