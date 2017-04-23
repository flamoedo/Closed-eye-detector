package com.amoedo.closed_eye_detector;

import android.content.Context;
import android.graphics.Camera;
import android.hardware.SensorManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MenuItem;
import android.view.OrientationEventListener;
import android.view.SurfaceView;
import android.view.WindowManager;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.JavaCameraView;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;


public class Show_camera extends AppCompatActivity  implements CameraBridgeViewBase.CvCameraViewListener2 {



    // Used for logging success or failure messages
    private static final String TAG = "OCVSample::Activity";

    // Loads camera view of OpenCV for us to use. This lets us see using OpenCV
    private CameraBridgeViewBase mOpenCvCameraView;

    // Used in Camera selection from menu (when implemented)
    private boolean              mIsJavaCamera = true;
    private MenuItem mItemSwitchCamera = null;

    // These variables are used (at the moment) to fix camera orientation from 270degree to 0degree
    Mat mRgba;
    Mat mRgbaF;
    Mat mRgbaT;

    Mat mGray;

    private Camera mCamera;
    private OrientationEventListener mOrientationEventListener;
    private int mOrientation;

    private static final int ORIENTATION_PORTRAIT_NORMAL =  270;
    private static final int ORIENTATION_PORTRAIT_INVERTED =  90;
    private static final int ORIENTATION_LANDSCAPE_NORMAL =   0;
    private static final int ORIENTATION_LANDSCAPE_INVERTED =  180;

    private CascadeClassifier mFace_cascade;
    private CascadeClassifier mEye_cascade;

    private float                  mRelativeFaceSize   = 0.2f;
    private int                    mAbsoluteFaceSize   = 0;

    private int mFrameCount;
    private int mAlarmWait;


    static {
        if (!OpenCVLoader.initDebug()) {
            // Handle initialization error
        }
    }


    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i(TAG, "OpenCV loaded successfully");

                    initFaceCascade();
                    initEyeCascade();

                    mOpenCvCameraView.enableView();
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "called onCreate");
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);



        setContentView(R.layout.activity_show_camera);

        mOpenCvCameraView = (JavaCameraView) findViewById(R.id.show_camera_activity_java_surface_view);

        //Escolher qual câmera será utilizada
        mOpenCvCameraView.setCameraIndex(getIntent().getExtras().getInt("Camera"));

        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);

        mOpenCvCameraView.setCvCameraViewListener(this);

        MobileAds.initialize(this, getResources().getString(R.string.banner_ad_app_id));

        AdView mAdView = (AdView) findViewById(R.id.AdView2);
        AdRequest adRequest = new AdRequest.Builder().build();
        mAdView.loadAd(adRequest);

    }

    @Override
    public void onPause()
    {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onResume()
    {
        super.onResume();

        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }

        if (mOrientationEventListener == null) {
            mOrientationEventListener = new OrientationEventListener(this, SensorManager.SENSOR_DELAY_NORMAL) {

                @Override
                public void onOrientationChanged(int orientation) {

                    // determine our orientation based on sensor response
                    int lastOrientation = mOrientation;

                    if (orientation >= 315 || orientation < 45) {
                        if (mOrientation != ORIENTATION_PORTRAIT_NORMAL) {
                            mOrientation = ORIENTATION_PORTRAIT_NORMAL;
                        }
                    }
                    else if (orientation < 315 && orientation >= 225) {
                        if (mOrientation != ORIENTATION_LANDSCAPE_NORMAL) {
                            mOrientation = ORIENTATION_LANDSCAPE_NORMAL;
                        }
                    }
                    else if (orientation < 225 && orientation >= 135) {
                        if (mOrientation != ORIENTATION_PORTRAIT_INVERTED) {
                            mOrientation = ORIENTATION_PORTRAIT_INVERTED;
                        }
                    }
                    else { // orientation <135 && orientation > 45
                        if (mOrientation != ORIENTATION_LANDSCAPE_INVERTED) {
                            mOrientation = ORIENTATION_LANDSCAPE_INVERTED;
                        }
                    }

//
                }
            };
        }

        if (mOrientationEventListener.canDetectOrientation()) {
            mOrientationEventListener.enable();
        }

    }

    public void onDestroy() {
        super.onDestroy();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    public void onCameraViewStarted(int width, int height) {

        //mRgba = new Mat(height, width, CvType.CV_8UC4);
        //mRgbaF = new Mat(height, width, CvType.CV_8UC4);
        //mRgbaT = new Mat(height, width, CvType.CV_8UC4);
    }

    public void onCameraViewStopped() {
        mRgba.release();
    }

    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {


        Mat matFaceGray;
        Mat matFaceRgba;


        mRgba = inputFrame.rgba();
        mGray = inputFrame.gray();

        if (mAbsoluteFaceSize == 0) {
            int height = mGray.rows();
            if (Math.round(height * mRelativeFaceSize) > 0) {
                mAbsoluteFaceSize = Math.round(height * mRelativeFaceSize);
            }
            //mNativeDetector.setMinFaceSize(mAbsoluteFaceSize);
        }

        /////////////////////////////////////////////////
        // Detecta o Rosto
/////////////////////////////////////////////////


        MatOfRect faces = new MatOfRect();

        mFace_cascade.detectMultiScale(mGray, faces, 1.1, 2,2,
                new Size(mAbsoluteFaceSize, mAbsoluteFaceSize), new Size());

        Rect[] facesArray = faces.toArray();

        // Desenha o retângulo do rosto
        if(!faces.empty()){

            for (Rect face:facesArray
                 ) {

                Imgproc.rectangle(mRgba,face.br(),face.tl(), new Scalar(255,0,0),2);

            }
        }




/////////////////////////////////////////////////
        // Detecta os olhos
/////////////////////////////////////////////////


        if(!faces.empty()){

            for (Rect face:facesArray
                    ) {

                matFaceGray = mGray.submat(face);
                matFaceRgba = mRgba.submat(face);

                MatOfRect eyes = new MatOfRect();

                mEye_cascade.detectMultiScale(matFaceGray, eyes, 1.1, 2, 2,
                        new Size(mAbsoluteFaceSize/4, mAbsoluteFaceSize/3), new Size());

                Rect[] eyesArray = eyes.toArray();

                //Desenha o retangulo dos olhos.
                if(!eyes.empty()){

                    if (eyesArray.length == 2){
                    setText(getResources().getString(R.string.open));
                    mFrameCount = 0;}
                    else
                        countTimerEyesClosed();

                    for (Rect eye: eyesArray
                         ) {

                        Imgproc.rectangle(matFaceRgba,eye.br(),eye.tl(), new Scalar(0,255,0),5);

                    }

                }
                else
                    countTimerEyesClosed();
            }
        }

        return mRgba;

    }


    private void setText(String texto){

        Imgproc.putText(mRgba, texto, new Point(mRgba.size().width/18, mRgba.size().height/5), Core.FONT_HERSHEY_SIMPLEX, 4, new Scalar(0,255,0),5);

    }



    private void countTimerEyesClosed() {



        if (mFrameCount >= 4){

            if (mAlarmWait == 4 | mAlarmWait == 0) {

                setText(getResources().getString(R.string.warning));

                try {
                    Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                    Ringtone r = RingtoneManager.getRingtone(getApplicationContext(), notification);
                    r.play();
                } catch (Exception e) {
                    e.printStackTrace();
                }

                mAlarmWait = 0;
            }

            mAlarmWait++;
        }
        else{

            setText( getResources().getString(R.string.closed) + ": " + String.valueOf(mFrameCount));

        }

        mFrameCount++;

    }

    public void initFaceCascade(){

        //Carrega o classificador

        try {
            InputStream is = getResources().openRawResource(R.raw.lbpcascade_frontalface);
            File cascadeDir = getDir("haarcascade", Context.MODE_PRIVATE);
            File mCascadeFile = new File(cascadeDir, "lbpcascade_frontalface.xml");
            FileOutputStream os = new FileOutputStream(mCascadeFile);

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
            is.close();
            os.close();

            mFace_cascade = new CascadeClassifier(mCascadeFile.getAbsolutePath());
            mFace_cascade.load( mCascadeFile.getAbsolutePath() );
            if (mFace_cascade.empty()) {
                Log.e(TAG, "Failed to load cascade classifier");
                mFace_cascade = null;
            } else
                Log.i(TAG, "Loaded cascade classifier from " + mCascadeFile.getAbsolutePath());


        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "Failed to load cascade. Exception thrown: " + e);
        }




    }

    public void initEyeCascade(){

//Carrega o classificador
        try {
            InputStream is = getResources().openRawResource(R.raw.haarcascade_eye_tree_eyeglasses);
            File cascadeDir = getDir("haarcascade", Context.MODE_PRIVATE);
            File mCascadeFile = new File(cascadeDir, "haarcascade_lefteye_2splits.xml");
            FileOutputStream os = new FileOutputStream(mCascadeFile);

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
            is.close();
            os.close();

            mEye_cascade = new CascadeClassifier(mCascadeFile.getAbsolutePath());
            mEye_cascade.load( mCascadeFile.getAbsolutePath() );
            if (mEye_cascade.empty()) {
                Log.e(TAG, "Failed to load cascade classifier");
                mEye_cascade = null;
            } else
                Log.i(TAG, "Loaded cascade classifier from " + mCascadeFile.getAbsolutePath());


        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "Failed to load cascade. Exception thrown: " + e);
        }

    }

}
