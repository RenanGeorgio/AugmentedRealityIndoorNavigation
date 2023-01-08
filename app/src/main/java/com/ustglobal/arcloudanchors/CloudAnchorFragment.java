package com.ustglobal.arcloudanchors;

import android.content.Context;
import android.net.Uri;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import com.google.ar.core.Anchor;
import com.google.ar.core.Anchor.CloudAnchorState;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Camera;
import com.google.ar.core.Config;
import com.google.ar.core.Config.CloudAnchorMode;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Point;
import com.google.ar.core.Point.OrientationMode;
import com.google.ar.core.PointCloud;
import com.google.ar.core.Session;
import com.google.ar.core.Trackable;
import com.google.ar.core.TrackingState;
import com.supplyfy.helpers.helpers.CameraPermissionHelper;
import com.supplyfy.helpers.helpers.CloudAnchorManager;
import com.supplyfy.helpers.helpers.FirebaseManager;
import com.supplyfy.helpers.helpers.ResolveDialogFragment;
import com.supplyfy.helpers.helpers.SnackbarHelper;
import com.supplyfy.helpers.helpers.StorageManager;
import com.supplyfy.helpers.helpers.TapHelper;
import com.supplyfy.helpers.helpers.TrackingStateHelper;
import com.supplyfy.helpers.rendering.BackgroundRenderer;
import com.supplyfy.helpers.rendering.ObjectRenderer;
import com.supplyfy.helpers.rendering.ObjectRenderer.BlendMode;
import com.supplyfy.helpers.rendering.PlaneRenderer;
import com.supplyfy.helpers.rendering.PointCloudRenderer;
import com.supplyfy.helpers.helpers.DisplayRotationHelper;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.ux.TransformableNode;

import java.io.IOException;
import java.util.ArrayList;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class CloudAnchorFragment extends Fragment implements GLSurfaceView.Renderer {

    private CustomArFragment fragment;
    private static final String TAG = CloudAnchorFragment.class.getSimpleName();

    // Rendering. The Renderers are created here, and initialized when the GL surface is created.
    private GLSurfaceView surfaceView;

    private boolean installRequested;

    private Session session;
    private final SnackbarHelper messageSnackbarHelper = new SnackbarHelper();
    private final CloudAnchorManager cloudAnchorManager = new CloudAnchorManager();
    private DisplayRotationHelper displayRotationHelper;
    private TrackingStateHelper trackingStateHelper;
    private TapHelper tapHelper;
    private FirebaseManager firebaseManager;

    private ArrayList anchorList;
    private AnchorNode anchorNode;
    private MainActivity.AppAnchorState appAnchorState = MainActivity.AppAnchorState.NONE;

    private final BackgroundRenderer backgroundRenderer = new BackgroundRenderer();
    private final ObjectRenderer virtualObject = new ObjectRenderer();
    private final ObjectRenderer virtualObjectShadow = new ObjectRenderer();
    private final PlaneRenderer planeRenderer = new PlaneRenderer();
    private final PointCloudRenderer pointCloudRenderer = new PointCloudRenderer();

    // Temporary matrix allocated here to reduce number of allocations for each frame.
    private final float[] anchorMatrix = new float[16];
    private static final String SEARCHING_PLANE_MESSAGE = "Searching for surfaces...";
    private final float[] andyColor = {139.0f, 195.0f, 74.0f, 255.0f};
    private Spinner modelOptionsSpinner;
    private static final String[] paths = {"Straight Arrow", "Right Arrow", "Left Arrow"};
    private String ELECTRONICS = "electronics_DB";


    @Nullable
    private Anchor currentAnchor = null;

    private Button resolve;
    private String mode, from = null;
    private TinyDB tinydb = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        anchorList = new ArrayList();

        fragment = getArguments().getParcelable("customFragment");
        mode = getArguments().getString("mode");
        from = getArguments().getString("from");
        tinydb = getArguments().getParcelable("tinydb");

        fragment.setOnTapArPlaneListener((hitResult, plane, motionEvent) -> {
            if(mode.equalsIgnoreCase("admin")) {
                Frame frame = null;
                try {
                    frame = session.update();

                    Camera camera = frame.getCamera();

                    handleTap(frame, camera, hitResult, plane, motionEvent);
                } catch (CameraNotAvailableException e) {
                    e.printStackTrace();
                }
            } else {
                showToast("Anchor can be hosted only in Admin mode");
            }

        });

        fragment.getArSceneView().getScene().addOnUpdateListener(frameTime -> {

            if (appAnchorState != MainActivity.AppAnchorState.HOSTING)
                return;

            Anchor.CloudAnchorState cloudAnchorState = currentAnchor.getCloudAnchorState();

            if (cloudAnchorState.isError()) {
                showToast(cloudAnchorState.toString());
            } else if (cloudAnchorState == Anchor.CloudAnchorState.SUCCESS) {
                appAnchorState = MainActivity.AppAnchorState.HOSTED;

                String anchorId = currentAnchor.getCloudAnchorId();
                anchorList.add(anchorId);

                if (from.equalsIgnoreCase(LauncherActivity.ELECTRONICS)) {
                    tinydb.putListString(ELECTRONICS, anchorList);
                }

                showToast("Anchor hosted successfully. Anchor Id: " + anchorId);
            }

        });

        if (mode.equalsIgnoreCase("user")) {
            modelOptionsSpinner.setVisibility(View.GONE);
        } else {
            modelOptionsSpinner.setVisibility(View.VISIBLE);
            resolve.setVisibility(View.VISIBLE);
        }


    }



    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        tapHelper = new TapHelper(context);
        trackingStateHelper = new TrackingStateHelper(requireActivity());
        firebaseManager = new FirebaseManager(context);
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Inflate from the Layout XML file.
        View rootView = inflater.inflate(R.layout.activity_main, container, false);
        GLSurfaceView surfaceView = rootView.findViewById(R.id.surfaceView);
        this.surfaceView = surfaceView;
        displayRotationHelper = new DisplayRotationHelper(requireContext());
        surfaceView.setOnTouchListener(tapHelper);

        surfaceView.setPreserveEGLContextOnPause(true);
        surfaceView.setEGLContextClientVersion(2);
        surfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0); // Alpha used for plane blending.
        surfaceView.setRenderer(this);
        surfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        surfaceView.setWillNotDraw(false);

        resolve = rootView.findViewById(R.id.resolve);
        resolve.setOnClickListener(view -> resolveAction());

        modelOptionsSpinner = rootView.findViewById(R.id.modelOptions);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(getActivity(),
                android.R.layout.simple_spinner_item, paths);

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        modelOptionsSpinner.setAdapter(adapter);

        return rootView;
    }

    @Override
    public void onResume() {
        super.onResume();

        if (session == null) {
            Exception exception = null;
            String message = null;
            try {
                switch (ArCoreApk.getInstance().requestInstall(requireActivity(), !installRequested)) {
                    case INSTALL_REQUESTED:
                        installRequested = true;
                        return;
                    case INSTALLED:
                        break;
                }

                // ARCore requires camera permissions to operate. If we did not yet obtain runtime
                // permission on Android M and above, now is a good time to ask the user for it.
                if (!CameraPermissionHelper.hasCameraPermission(requireActivity())) {
                    CameraPermissionHelper.requestCameraPermission(requireActivity());
                    return;
                }

                // Create the session.
                session = new Session(requireActivity());

                // Configure the session.
                Config config = new Config(session);
                config.setCloudAnchorMode(CloudAnchorMode.ENABLED);
                session.configure(config);

            } catch (UnavailableArcoreNotInstalledException
                    | UnavailableUserDeclinedInstallationException e) {
                message = "Please install ARCore";
                exception = e;
            } catch (UnavailableApkTooOldException e) {
                message = "Please update ARCore";
                exception = e;
            } catch (UnavailableSdkTooOldException e) {
                message = "Please update this app";
                exception = e;
            } catch (UnavailableDeviceNotCompatibleException e) {
                message = "This device does not support AR";
                exception = e;
            } catch (Exception e) {
                message = "Failed to create AR session";
                exception = e;
            }

            if (message != null) {
                messageSnackbarHelper.showError(requireActivity(), message);
                Log.e(TAG, "Exception creating session", exception);
                return;
            }
        }

        // Note that order matters - see the note in onPause(), the reverse applies here.
        try {
            session.resume();
        } catch (CameraNotAvailableException e) {
            messageSnackbarHelper
                    .showError(requireActivity(), "Camera not available. Try restarting the app.");
            session = null;
            return;
        }

        surfaceView.onResume();
        displayRotationHelper.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (session != null) {
            // Note that the order matters - GLSurfaceView is paused first so that it does not try
            // to query the session. If Session is paused before GLSurfaceView, GLSurfaceView may
            // still call session.update() and get a SessionPausedException.
            displayRotationHelper.onPause();
            surfaceView.onPause();
            session.pause();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] results) {
        if (!CameraPermissionHelper.hasCameraPermission(requireActivity())) {
            Toast.makeText(requireActivity(), "Camera permission is needed to run this application",
                            Toast.LENGTH_LONG)
                    .show();
            if (!CameraPermissionHelper.shouldShowRequestPermissionRationale(requireActivity())) {
                // Permission denied with checking "Do not ask again".
                CameraPermissionHelper.launchPermissionSettings(requireActivity());
            }
            requireActivity().finish();
        }
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f);

        // Prepare the rendering objects. This involves reading shaders, so may throw an IOException.
        try {
            // Create the texture and pass it to ARCore session to be filled during update().
            backgroundRenderer.createOnGlThread(getContext());
            planeRenderer.createOnGlThread(getContext(), "models/trigrid.png");
            pointCloudRenderer.createOnGlThread(getContext());

            virtualObject.createOnGlThread(getContext(), "models/andy.obj", "models/andy.png");
            virtualObject.setMaterialProperties(0.0f, 2.0f, 0.5f, 6.0f);

            virtualObjectShadow
                    .createOnGlThread(getContext(), "models/andy_shadow.obj", "models/andy_shadow.png");
            virtualObjectShadow.setBlendMode(BlendMode.Shadow);
            virtualObjectShadow.setMaterialProperties(1.0f, 0.0f, 0.0f, 1.0f);

        } catch (IOException e) {
            Log.e(TAG, "Failed to read an asset file", e);
        }
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        displayRotationHelper.onSurfaceChanged(width, height);
        GLES20.glViewport(0, 0, width, height);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        // Clear screen to notify driver it should not load any pixels from previous frame.
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        if (session == null) {
            return;
        }
        // Notify ARCore session that the view size changed so that the perspective matrix and
        // the video background can be properly adjusted.
        displayRotationHelper.updateSessionIfNeeded(session);

        try {
            session.setCameraTextureName(backgroundRenderer.getTextureId());

            // Obtain the current frame from ARSession. When the configuration is set to
            // UpdateMode.BLOCKING (it is by default), this will throttle the rendering to the
            // camera framerate.
            Frame frame = session.update();
            cloudAnchorManager.onUpdate();

            Camera camera = frame.getCamera();

            // Handle one tap per frame.
            //handleTap(frame, camera);

            // If frame is ready, render camera preview image to the GL surface.
            backgroundRenderer.draw(frame);

            // Keep the screen unlocked while tracking, but allow it to lock when tracking stops.
            trackingStateHelper.updateKeepScreenOnFlag(camera.getTrackingState());

            // If not tracking, don't draw 3D objects, show tracking failure reason instead.
            if (camera.getTrackingState() == TrackingState.PAUSED) {
                messageSnackbarHelper.showMessage(
                        getActivity(), TrackingStateHelper.getTrackingFailureReasonString(camera));
                return;
            }

            // Get projection matrix.
            float[] projmtx = new float[16];
            camera.getProjectionMatrix(projmtx, 0, 0.1f, 100.0f);

            // Get camera matrix and draw.
            float[] viewmtx = new float[16];
            camera.getViewMatrix(viewmtx, 0);

            // Compute lighting from average intensity of the image.
            // The first three components are color scaling factors.
            // The last one is the average pixel intensity in gamma space.
            final float[] colorCorrectionRgba = new float[4];
            frame.getLightEstimate().getColorCorrection(colorCorrectionRgba, 0);

            // Visualize tracked points.
            // Use try-with-resources to automatically release the point cloud.
            try (PointCloud pointCloud = frame.acquirePointCloud()) {
                pointCloudRenderer.update(pointCloud);
                pointCloudRenderer.draw(viewmtx, projmtx);
            }

            // No tracking error at this point. If we didn't detect any plane, show searchingPlane message.
            if (!hasTrackingPlane()) {
                messageSnackbarHelper.showMessage(getActivity(), SEARCHING_PLANE_MESSAGE);
            }

            // Visualize planes.
            planeRenderer.drawPlanes(
                    session.getAllTrackables(Plane.class), camera.getDisplayOrientedPose(), projmtx);

            if (currentAnchor != null && currentAnchor.getTrackingState() == TrackingState.TRACKING) {
                currentAnchor.getPose().toMatrix(anchorMatrix, 0);
                // Update and draw the model and its shadow.
                virtualObject.updateModelMatrix(anchorMatrix, 1f);
                virtualObjectShadow.updateModelMatrix(anchorMatrix, 1f);

                virtualObject.draw(viewmtx, projmtx, colorCorrectionRgba, andyColor);
                virtualObjectShadow.draw(viewmtx, projmtx, colorCorrectionRgba, andyColor);
            }
        } catch (Throwable t) {
            // Avoid crashing the application due to unhandled exceptions.
            Log.e(TAG, "Exception on the OpenGL thread", t);
        }
    }

    // Handle only one tap per frame, as taps are usually low frequency compared to frame rate.
    private void handleTap(Frame frame, Camera camera, HitResult hitResult, Plane plane, MotionEvent motionEvent) {
        if (motionEvent != null && camera.getTrackingState() == TrackingState.TRACKING) {
            Trackable trackable = hitResult.getTrackable();
            // Creates an anchor if a plane or an oriented point was hit.
            if ((trackable instanceof Plane
                    && plane.isPoseInPolygon(hitResult.getHitPose())
                    && (PlaneRenderer.calculateDistanceToPlane(hitResult.getHitPose(), camera.getPose()) > 0))
                    || (trackable instanceof Point
                    && ((Point) trackable).getOrientationMode()
                    == OrientationMode.ESTIMATED_SURFACE_NORMAL)) {

                currentAnchor = hitResult.createAnchor();

                //getActivity().runOnUiThread(() -> resolveButton.setEnabled(false));
                appAnchorState = MainActivity.AppAnchorState.HOSTING;

                Log.d("HIT_RESULT:", hitResult.toString());

                cloudAnchorManager.hostCloudAnchor(session, currentAnchor, 300, this::onHostedAnchorAvailable);
                //anchor = arFragment.getArSceneView().getSession().hostCloudAnchor(hitResult.createAnchor());

                showToast("Hosting...");
                createCloudAnchorModel(currentAnchor);

                return;
            }
        }
    }

    /**
     * Checks if we detected at least one plane.
     */
    private boolean hasTrackingPlane() {
        for (Plane plane : session.getAllTrackables(Plane.class)) {
            if (plane.getTrackingState() == TrackingState.TRACKING) {
                return true;
            }
        }
        return false;
    }

    private synchronized void onHostedAnchorAvailable(Anchor anchor) {
        CloudAnchorState cloudState = anchor.getCloudAnchorState();
        if (cloudState == CloudAnchorState.SUCCESS) {
            String cloudAnchorId = anchor.getCloudAnchorId();
            firebaseManager.nextShortCode(shortCode -> {
                if (shortCode != null) {
                    firebaseManager.storeUsingShortCode(shortCode, cloudAnchorId);
                    messageSnackbarHelper.showMessage(getActivity(), "Cloud Anchor Hosted. Short code: " + shortCode);
                } else {
                    // Firebase could not provide a short code.
                    messageSnackbarHelper.showMessage(getActivity(), "Cloud Anchor Hosted, but could not "
                            + "get a short code from Firebase.");
                }
            });
            currentAnchor = anchor;
        } else {
            messageSnackbarHelper.showMessage(getActivity(), "Error while hosting: " + cloudState.toString());
        }
    }


    private synchronized void resolveAction() {
        ArrayList<String> stringArrayList = new ArrayList<>();
        if (from.equalsIgnoreCase(LauncherActivity.ELECTRONICS)) {
            stringArrayList = tinydb.getListString(ELECTRONICS);
        }

        for (int i = 0; i < stringArrayList.size(); i++) {
            String anchorId = stringArrayList.get(i);
            if (anchorId.equals("null")) {
                Toast.makeText(getActivity(), "No anchor Id found", Toast.LENGTH_LONG).show();
                return;
            }

            Anchor resolvedAnchor = fragment.getArSceneView().getSession().resolveCloudAnchor(anchorId);
            createCloudAnchorModel(resolvedAnchor);

        }
    }

    private void showToast(String s) {
        Toast.makeText(getActivity(), s, Toast.LENGTH_LONG).show();
    }

    private void createCloudAnchorModel(Anchor anchor) {
        ModelRenderable
                .builder()
                .setSource(getActivity(), Uri.parse("model.sfb"))
                .build()
                .thenAccept(modelRenderable -> placeCloudAnchorModel(anchor, modelRenderable));

    }

    private void placeCloudAnchorModel(Anchor anchor, ModelRenderable modelRenderable) {
        anchorNode = new AnchorNode(anchor);
        /*AnchorNode cannot be zoomed in or moved
        So we create a TransformableNode with AnchorNode as the parent*/
        TransformableNode transformableNode = new TransformableNode(fragment.getTransformationSystem());

        if (modelOptionsSpinner.getSelectedItem().toString().equals("Straight Arrow")) {
            transformableNode.setLocalRotation(Quaternion.axisAngle(new Vector3(0, 1f, 0), 225));
        }
        if (modelOptionsSpinner.getSelectedItem().toString().equals("Right Arrow")) {
            transformableNode.setLocalRotation(Quaternion.axisAngle(new Vector3(0, 1f, 0), 135));
        }
        if (modelOptionsSpinner.getSelectedItem().toString().equals("Left Arrow")) {
            transformableNode.setLocalRotation(Quaternion.axisAngle(new Vector3(0, 1f, 0), 315));
        }
        transformableNode.setParent(anchorNode);
        //adding the model to the transformable node
        transformableNode.setRenderable(modelRenderable);
        //adding this to the scene
        fragment.getArSceneView().getScene().addChild(anchorNode);
    }

}
