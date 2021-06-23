package com.mxcsyounes.facerecognition2;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.YuvImage;
import android.media.Image;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraInfo;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.core.impl.CameraInfoInternal;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.common.util.concurrent.ListenableFuture;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {


    private final String[] REQUIRED_PERMISSIONS = new String[]{"android.permission.CAMERA"};
    private final int REQUEST_CODE_PERMISSIONS = 101;
    CascadeClassifier faceDetector;
    PreviewView textureView;
    //PreviewView previewView;
    ImageView ivBitmap;
    LinearLayout llBottom;
    Camera camera;
    GraphicOverlay graphicOverlay;
    int currentImageType = Imgproc.COLOR_RGB2GRAY;
    ImageAnalysis imageAnalysis;
    FloatingActionButton btnCapture, btnOk, btnCancel;
    private ImageCapture imageCapture;
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private Executor cameraExecutor;
    private boolean needUpdateGraphicOverlayImageSourceInfo;

    public static Bitmap convertJPEGtoBitmap(Image image) {
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer planeBuffer = planes[0].getBuffer();
        byte[] bytes = new byte[planeBuffer.remaining()];
        planeBuffer.get(bytes);
        Bitmap original = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        return Bitmap.createScaledBitmap(original, 1000, 1000, true);
    }

    public static Bitmap toBitmap(Image image) {
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer yBuffer = planes[0].getBuffer();
        ByteBuffer uBuffer = planes[1].getBuffer();
        ByteBuffer vBuffer = planes[2].getBuffer();

        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();

        byte[] nv21 = new byte[ySize + uSize + vSize];
        //U and V are swapped
        yBuffer.get(nv21, 0, ySize);
        vBuffer.get(nv21, ySize, vSize);
        uBuffer.get(nv21, ySize + vSize, uSize);

        YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, image.getWidth(), image.getHeight(), null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new android.graphics.Rect(0, 0, yuvImage.getWidth(), yuvImage.getHeight()), 75, out);

        byte[] imageBytes = out.toByteArray();
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        cameraExecutor = Executors.newFixedThreadPool(8);
        btnCapture = findViewById(R.id.btnCapture);
        graphicOverlay = findViewById(R.id.graphicOverlay);
        btnOk = findViewById(R.id.btnAccept);
        btnCancel = findViewById(R.id.btnReject);

        llBottom = findViewById(R.id.llBottom);
        textureView = findViewById(R.id.textureView);

        if (!OpenCVLoader.initDebug()) {
            Log.d("ERROR", "Unable to load OpenCV");
        } else {
            Log.d("SUCCESS", "OpenCV loaded");
            createClassifier();
        }

        if (allPermissionsGranted()) {
            Log.d("TAG", "allPermissionsGranted: ");
            setupCamera();
        } else
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);

        btnCapture.setOnClickListener(v -> takePictureForLearning());
    }

    private void setupCamera() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindCameraPreview(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    void bindCameraPreview(@NonNull ProcessCameraProvider cameraProvider) {
        cameraProvider.unbindAll();
        Preview preview = new Preview.Builder()
                .build();

        @SuppressLint("RestrictedApi") CameraSelector cameraSelector = new CameraSelector.Builder()

                .addCameraFilter(cameraInfos -> {
                    List<CameraInfo> resultCameras = new ArrayList<>();
                    for (CameraInfo cameraInfo : cameraInfos) {
                        CameraInfoInternal cameraInfoInternal = (CameraInfoInternal) cameraInfo;
                        if (cameraInfoInternal.getCameraId().equals("1"))
                            resultCameras.add(cameraInfo);

                    }

                    return resultCameras;
                })
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        imageCapture = new ImageCapture.Builder()
                .setTargetRotation(textureView.getDisplay().getRotation())
                .build();

        imageAnalysis = new ImageAnalysis.Builder()
                .setTargetRotation(textureView.getDisplay().getRotation())
                .build();

/*
        imageAnalysis.setAnalyzer(cameraExecutor, this::analyzeImage);
*/
        setImageAnalysis();
        preview.setSurfaceProvider(textureView.getSurfaceProvider());
        camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture, imageAnalysis);
    }

    private ImageAnalysis setImageAnalysis() {

        needUpdateGraphicOverlayImageSourceInfo = true;

        imageAnalysis.setAnalyzer(cameraExecutor, image -> {
            @SuppressLint("UnsafeOptInUsageError") Bitmap bitmap = toBitmap(image.getImage());
            if (needUpdateGraphicOverlayImageSourceInfo) {
                boolean isImageFlipped = true;
                int rotationDegrees = image.getImageInfo().getRotationDegrees();
                if (rotationDegrees == 0 || rotationDegrees == 180)
                    graphicOverlay.setImageSourceInfo(
                            image.getWidth(), image.getHeight(), isImageFlipped);
                else
                    graphicOverlay.setImageSourceInfo(
                            image.getHeight(), image.getWidth(), isImageFlipped);

                needUpdateGraphicOverlayImageSourceInfo = false;
            }
            Mat mat = new Mat();
            Utils.bitmapToMat(bitmap, mat);
            image.close();

            MatOfRect faceDetections = new MatOfRect();
            faceDetector.detectMultiScale(mat, faceDetections);

            Rect[] faces = faceDetections.toArray();
            for (Rect rect : faces) {
                System.out.println(rect);
            }

            if (faces.length != 0) {
                //android.graphics.Rect rect = new android.graphics.Rect(0, 0, 100, 100);
                android.graphics.Rect rect = new android.graphics.Rect(faces[0].x, faces[0].y, faces[0].x + faces[0].width + 20, faces[0].y + faces[0].height + 20);
                System.out.println("Rect " + rect.toString());
                System.out.println("Image " + image.getCropRect().toString());
                Imgproc.rectangle(mat, new Point(faces[0].x, faces[0].y), new Point(faces[0].x + faces[0].width, faces[0].y + faces[0].height), new Scalar(255, 0, 0));
                graphicOverlay.clear();
                BoundingBoxContourGraphic contourGraphic = new BoundingBoxContourGraphic(graphicOverlay, rect, image.getCropRect());
                graphicOverlay.add(contourGraphic);
                graphicOverlay.postInvalidate();

            }

        });


        return imageAnalysis;

    }

    private void updateTransform() {
        Matrix mx = new Matrix();
        float w = textureView.getMeasuredWidth();
        float h = textureView.getMeasuredHeight();

        float cX = w / 2f;
        float cY = h / 2f;

        int rotationDgr;
        int rotation = (int) textureView.getRotation();

        switch (rotation) {
            case Surface.ROTATION_0:
                rotationDgr = 0;
                break;
            case Surface.ROTATION_90:
                rotationDgr = 90;
                break;
            case Surface.ROTATION_180:
                rotationDgr = 180;
                break;
            case Surface.ROTATION_270:
                rotationDgr = 270;
                break;
            default:
                return;
        }

        mx.postRotate((float) rotationDgr, cX, cY);
        //textureView.setTransform(mx);
    }


/*    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }*/

/*    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case R.id.black_white:
                currentImageType = Imgproc.COLOR_RGB2GRAY;
                startCamera();
                return true;

            case R.id.hsv:
                currentImageType = Imgproc.COLOR_RGB2HSV;
                startCamera();
                return true;

            case R.id.lab:
                currentImageType = Imgproc.COLOR_RGB2Lab;
                startCamera();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }*/

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                Log.d("TAG", "onRequestPermissionsResult: ");
                setupCamera();
            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    private boolean allPermissionsGranted() {

        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void takePictureForLearning() {
        imageCapture.takePicture(cameraExecutor, new ImageCapture.OnImageCapturedCallback() {

            @SuppressLint("UnsafeOptInUsageError")
            @Override
            public void onCaptureSuccess(@NonNull ImageProxy image) {
                Bitmap bitmap = convertJPEGtoBitmap(image.getImage());
                Mat mat = new Mat();
                Bitmap bmp32 = bitmap.copy(Bitmap.Config.ARGB_8888, true);
                Utils.bitmapToMat(bmp32, mat);
                image.close();

                MatOfRect faceDetections = new MatOfRect();
                faceDetector.detectMultiScale(mat, faceDetections);

                Rect[] faces = faceDetections.toArray();
                for (Rect rect : faces) {
                    System.out.println(rect);

                }
                if (faces.length != 0) {
                    //android.graphics.Rect rect = new android.graphics.Rect(0, 0, 100, 100);
                    android.graphics.Rect rect = new android.graphics.Rect(faces[0].x, faces[0].y, faces[0].x + faces[0].width, faces[0].y + faces[0].height);
                    System.out.println(rect.toString());
                    System.out.println(image.getCropRect().toString());
                    graphicOverlay.clear();
                    BoundingBoxContourGraphic contourGraphic = new BoundingBoxContourGraphic(graphicOverlay, rect, image.getCropRect());
                    graphicOverlay.add(contourGraphic);
                }
            }
        });
    }

    private void createClassifier() {
        Log.d("TAG", "onManagerConnected: called succes");
        InputStream inputStream = getResources().openRawResource(R.raw.haarcascade_frontalface_alt2);
        File cascadeDir = getDir("cascade", Context.MODE_PRIVATE);
        File casFile = new File(cascadeDir, "haarcascade_frontalface_alt2.xml");
        try {
            FileOutputStream fos = new FileOutputStream(casFile);
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }
            inputStream.close();
            fos.close();
            Log.d("TAG", "onManagerConnected: file" + casFile.getAbsolutePath());
            faceDetector = new CascadeClassifier(casFile.getAbsolutePath());
            if (faceDetector.empty()) {
                faceDetector = null;
            } else {
                cascadeDir.delete();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}