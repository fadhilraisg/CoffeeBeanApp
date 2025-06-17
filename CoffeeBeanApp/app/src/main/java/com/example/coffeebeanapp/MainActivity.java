package com.example.coffeebeanapp;

import android.content.Intent;
import android.graphics.ImageFormat;
import android.os.Bundle;
import android.view.Surface;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;

import android.Manifest;
import android.content.pm.PackageManager;
import androidx.annotation.NonNull;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.camera.core.ExperimentalGetImage;
import androidx.core.content.ContextCompat;
import com.google.common.util.concurrent.ListenableFuture;
import android.media.Image;

import org.tensorflow.lite.support.common.ops.NormalizeOp;
import org.tensorflow.lite.task.vision.classifier.Classifications;
import org.tensorflow.lite.task.vision.classifier.ImageClassifier;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.ops.ResizeOp;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.FileUtil;
import androidx.camera.core.ImageAnalysis;
import android.graphics.Bitmap;
import android.graphics.Matrix;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.ByteOrder;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import org.tensorflow.lite.DataType;

import android.net.Uri;
import androidx.appcompat.app.AlertDialog;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.io.ByteArrayOutputStream;
import android.graphics.BitmapFactory;
import android.graphics.YuvImage;
import android.graphics.Rect;
import androidx.core.app.ActivityCompat;
import com.google.android.material.button.MaterialButton;
import android.widget.ImageView;
import android.provider.MediaStore;


public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private FirebaseAuth mAuth;
    private GoogleSignInClient mGoogleSignInClient;

    private PreviewView viewFinder;
    private ImageCapture imageCapture;
    private ExecutorService cameraExecutor;
    private ProcessCameraProvider cameraProvider;

    private ImageClassifier imageClassifier;
    private OverlayView overlayView;
    private ImageAnalysis imageAnalysis;

    private FloatingActionButton cameraCaptureButton;
    private FloatingActionButton btnProfileInfo;
    private FloatingActionButton btnImportGallery;
    private FloatingActionButton btnBackToCamera;
    private ImageView capturedImageView;
    private List<String> labels;
    private Interpreter tflite;

    private static final int REQUEST_CODE_PERMISSIONS = 10;
    private final String[] REQUIRED_PERMISSIONS = new String[]{
            Manifest.permission.CAMERA,
    };

    private ActivityResultLauncher<String[]> requestPermissionLauncher;
    private ActivityResultLauncher<Intent> pickImageLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        mAuth = FirebaseAuth.getInstance();

        mGoogleSignInClient = GoogleSignIn.getClient(this, GoogleSignInOptions.DEFAULT_SIGN_IN);

        viewFinder = findViewById(R.id.viewFinder);
        cameraCaptureButton = findViewById(R.id.camera_capture_button);
        btnProfileInfo = findViewById(R.id.btnProfileInfo);

        btnProfileInfo.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, ProfileActivity.class);
            startActivity(intent);
        });

        btnImportGallery = findViewById(R.id.btnImportGallery);
        btnBackToCamera = findViewById(R.id.btnBackToCamera);
        capturedImageView = findViewById(R.id.capturedImageView);

        overlayView = findViewById(R.id.overlayView);

        cameraExecutor = Executors.newSingleThreadExecutor();

        setupResultLaunchers();

        labels = loadLabelList();
        initInterpreter();

        if (allPermissionsGranted()) {
            startCamera();
        } else {
            String[] permissionToRequest;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                permissionToRequest = new String[]{Manifest.permission.CAMERA, Manifest.permission.READ_MEDIA_IMAGES};
            } else {
                permissionToRequest = new String[]{Manifest.permission.CAMERA, Manifest.permission.READ_EXTERNAL_STORAGE};
            }
            requestPermissionLauncher.launch(permissionToRequest);
        }

        cameraCaptureButton.setOnClickListener(v -> takePhoto());
        btnImportGallery.setOnClickListener(v -> dispatchPickImageIntent());
        btnBackToCamera.setOnClickListener(v -> showCameraPreview());
    }

    private List<String> loadLabelList() {
        List<String> labelList = new ArrayList<>();
        try {
            InputStream is = getAssets().open("Labels.txt");
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            String line;
            while ((line = reader.readLine()) != null) {
                labelList.add(line);
            }
            reader.close();
            Log.d(TAG, "Labels.txt berhasil dimuat. Jumlah Label : " + labelList.size());
        } catch (IOException e) {
            Log.e("MainActivity", "Gagal Memuat Daftar Label" + e.getMessage());
            Toast.makeText(this, "Gagal memuat daftar label", Toast.LENGTH_SHORT).show();
        }
        return labelList;
    }

    private void initInterpreter() {
        try {
            MappedByteBuffer tfliteModel = FileUtil.loadMappedFile(this, "efficientnetb0.tflite");
            tflite = new Interpreter(tfliteModel);
            Log.d(TAG, "Interpreter berhasil diinisialisasi.");
        } catch (IOException e) {
            Log.e(TAG, "Gagal memuat model: " + e.getMessage());
            Toast.makeText(this, "Gagal memuat model", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Intent intent = new Intent(MainActivity.this, LoginActivity.class);
            startActivity(intent);
        }
    }

    private boolean allPermissionsGranted() {
        boolean hasCameraPermission = ContextCompat.checkSelfPermission(this,  Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
        boolean hasStoragePermission = false;

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            hasStoragePermission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED;
        } else {
            hasStoragePermission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
        return hasCameraPermission && hasStoragePermission;
    }

    private void setupResultLaunchers() {
        requestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                permissions -> {
                    boolean cameraGranted = permissions.getOrDefault(Manifest.permission.CAMERA, false);
                    boolean storageGranted = false;
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        storageGranted = permissions.getOrDefault(Manifest.permission.READ_MEDIA_IMAGES, false);
                    } else {
                        storageGranted = permissions.getOrDefault(Manifest.permission.READ_EXTERNAL_STORAGE, false);
                    }
                    if (cameraGranted && storageGranted) {
                        Toast.makeText(MainActivity.this, "Izin kamera dan penyimpanan diberikan!", Toast.LENGTH_SHORT).show();
                        startCamera();
                    } else {
                        Toast.makeText(MainActivity.this, "Izin kamera dan penyimpanan ditolak!", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                }
        );

        pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri imageUri = result.getData().getData();
                        if (imageUri != null) {
                            try {
                                Bitmap selectedBitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), imageUri);
                                showCapturedImage(selectedBitmap);
                                runClassificationOnBitmap(selectedBitmap);
                            } catch (IOException e) {
                                Log.e(TAG, "Gagal mengambil gambar dari galeri." + e.getMessage());
                                Toast.makeText(this, "Gagal memuat gambar", Toast.LENGTH_SHORT).show();
                            }
                        }
                    } else {
                        Toast.makeText(this, "Pemilihan gambar dibatalkan", Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    private void dispatchPickImageIntent() {
        Intent galleryIntent = new Intent(Intent.ACTION_GET_CONTENT);
        galleryIntent.setType("image/*");
        pickImageLauncher.launch(galleryIntent);
    }

    private void showCapturedImage(Bitmap bitmap) {
        if (imageClassifier != null) {
            imageClassifier.close();
            imageClassifier = null;
        }

        if (imageAnalysis != null) {
            imageAnalysis.clearAnalyzer();
            imageAnalysis = null;
        }

        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
            cameraExecutor = null;
        }

        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }

        capturedImageView.setImageBitmap(bitmap);
        capturedImageView.setVisibility(View.VISIBLE);
        viewFinder.setVisibility(View.GONE);
        overlayView.clear();
        overlayView.setVisibility(View.VISIBLE);

        cameraCaptureButton.setVisibility(View.GONE);
        btnImportGallery.setVisibility(View.GONE);
        btnBackToCamera.setVisibility(View.VISIBLE);
    }

    private void showCameraPreview() {
        capturedImageView.setVisibility(View.GONE);
        viewFinder.setVisibility(View.VISIBLE);
        overlayView.setVisibility(View.VISIBLE);

        overlayView.clear();

        cameraCaptureButton.setVisibility(View.VISIBLE);
        btnImportGallery.setVisibility(View.VISIBLE);
        btnBackToCamera.setVisibility(View.GONE);

        cameraExecutor = Executors.newSingleThreadExecutor();
        initInterpreter();
        startCamera();
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(viewFinder.getSurfaceProvider());

                imageCapture = new ImageCapture.Builder()
                        .setTargetRotation(viewFinder.getDisplay().getRotation())
                        .build();

                imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setTargetRotation(viewFinder.getDisplay().getRotation())
                        .build();
                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                try{
                    cameraProvider.unbindAll();
                    cameraProvider.bindToLifecycle(
                            this,
                            cameraSelector,
                            preview,
                            imageCapture);
                } catch (Exception exc) {
                    Log.e(TAG, "Gagal Mengikat Use Case Kamera", exc);
                }
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Gagal Mengambil Provider Kamera", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private Bitmap mediaImageToBitmap(Image image) {
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer yBuffer = planes[0].getBuffer();
        ByteBuffer uvBuffer = planes[1].getBuffer();

        int ySize = yBuffer.remaining();
        int uvSize = uvBuffer.remaining();

        byte[] nv21 = new byte[ySize + uvSize];
        yBuffer.get(nv21, 0, ySize);
        uvBuffer.get(nv21, ySize, uvSize);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, image.getWidth(), image.getHeight(), null);
        yuvImage.compressToJpeg(new Rect(0, 0, image.getWidth(), image.getHeight()), 100, out);
        byte[] imageBytes = out.toByteArray();
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
    }

    private void takePhoto() {
        if(imageCapture == null) {
            Log.e(TAG, "Image Capture belum diinialisasi");
            return;
        }

        String fileName = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis()) + ".jpg";
        File photoFile = new File(getExternalFilesDir(null), fileName);

        ImageCapture.OutputFileOptions outputFileOptions = new ImageCapture.OutputFileOptions.Builder(photoFile).build();

        imageCapture.takePicture(
                outputFileOptions,
                ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                        String msg = "Foto berhasil diambil : " + photoFile.getAbsolutePath();
                        Toast.makeText(getBaseContext(), msg, Toast.LENGTH_SHORT).show();
                        Log.d(TAG, msg);

                        Bitmap capturedBitmap = BitmapFactory.decodeFile(photoFile.getAbsolutePath());
                        if (capturedBitmap != null) {
                            showCapturedImage(capturedBitmap);
                            runClassificationOnBitmap(capturedBitmap);
                        } else {
                            Toast.makeText(getBaseContext(), "Gagal menampilkan Foto", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        Log.e(TAG, "Pengambilan foto gagal : " + exception.getMessage(), exception);
                        Toast.makeText(getBaseContext(), "Pengambilan foto gagal : " + exception.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    private void runClassificationOnBitmap(Bitmap bitmap) {
        if (tflite == null) {
            Log.e(TAG, "Interpreter belum siap.");
            return;
        }

        Bitmap resized = Bitmap.createScaledBitmap(bitmap, 224, 224, true);
        ByteBuffer inputBuffer = ByteBuffer.allocateDirect(1 * 224 * 224 * 3 * 4);
        inputBuffer.order(ByteOrder.nativeOrder());

        int[] intValues = new int[224 * 224];
        resized.getPixels(intValues, 0, 224, 0, 0, 224, 224);
        for (int pixel : intValues) {
            float r = ((pixel >> 16) & 0xFF) / 255.0f;
            float g = ((pixel >> 8) & 0xFF) / 255.0f;
            float b = (pixel & 0xFF) / 255.0f;
            inputBuffer.putFloat(r);
            inputBuffer.putFloat(g);
            inputBuffer.putFloat(b);
        }

        float[][] output = new float[1][labels.size()];
        tflite.run(inputBuffer, output);
        Log.d(TAG, "Output Model : " + Arrays.toString(output[0]));

        int maxIndex = 0;
        float maxScore = -Float.MAX_VALUE;
        for (int i = 0; i < output[0].length; i++) {
            if (output[0][i] > maxScore) {
                maxScore = output[0][i];
                maxIndex = i;
            }
        }

        String label = labels.get(maxIndex);
        String result = String.format(Locale.getDefault(), "Hasil Klasifikasi: %s (%.2f%%)", label, maxScore * 100);
        runOnUiThread(() -> {
            overlayView.setClassificationResult(result);
        });
        Log.d(TAG, result);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            boolean allGranted = true;
            for (String permission : permissions) {
                if ((permission.equals(Manifest.permission.READ_EXTERNAL_STORAGE) && android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) ||
                        (permission.equals(Manifest.permission.READ_MEDIA_IMAGES) && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) ||
                        permission.equals(Manifest.permission.CAMERA)) {
                    if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                        allGranted = false;
                        break;
                    }
                }
            }

            if (allGranted) {
                Toast.makeText(this, "Semua izin diberikan!", Toast.LENGTH_SHORT).show();
                startCamera();
            } else {
                Toast.makeText(this, "Izin kamera atau penyimpanan tidak diberikan. Fungsi mungkin terbatas.", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }

        if (imageClassifier != null) {
            imageClassifier.close();
        }
    }
}