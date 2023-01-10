package com.ustglobal.arcloudanchors;

import androidx.appcompat.app.AppCompatActivity;

import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Toast;

import com.google.ar.core.Anchor;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Anchor.CloudAnchorState;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.ux.TransformableNode;
import com.google.common.base.Preconditions;
import com.google.firebase.database.DatabaseError;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity implements AdapterView.OnItemSelectedListener {

    private CustomArFragment arFragment;
    private FirebaseManager firebaseManager;
    private final CloudAnchorManager cloudManager = new CloudAnchorManager();
    private RoomCodeAndCloudAnchorIdListener hostListener;
    private ArrayList anchorList;
    public Spinner modelOptionsSpinner;
    private static final String[] paths = {"Straight Arrow", "Right Arrow", "Left Arrow"};
    private String FROM, MODE;

    private enum AppAnchorState {
        NONE,
        HOSTING,
        HOSTED
    }

    private Anchor anchor;
    private AnchorNode anchorNode;
    private AppAnchorState appAnchorState = AppAnchorState.NONE;
    private String CLOTHING = "clothing_DB";

    // Locks needed for synchronization
    private final Object singleTapLock = new Object();
    private final Object anchorLock = new Object();

    private AppAnchorState currentMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            Bundle extras = getIntent().getExtras();
            if (extras == null) {
                FROM = null;
            } else {
                FROM = extras.getString(LauncherActivity.FROM);
                MODE = extras.getString(LauncherActivity.MODE);
            }
        }

        //ArCoreApk.Availability availability = ArCoreApk.getInstance().checkAvailability(this);
        setContentView(R.layout.activity_main);
        anchorList = new ArrayList();
        TinyDB tinydb = new TinyDB(getApplicationContext());
        firebaseManager = new FirebaseManager(this);
        Button resolve = findViewById(R.id.resolve);
        modelOptionsSpinner = findViewById(R.id.modelOptions);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, paths);

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        modelOptionsSpinner.setAdapter(adapter);
        modelOptionsSpinner.setOnItemSelectedListener(this);

        arFragment = (CustomArFragment) getSupportFragmentManager().findFragmentById(R.id.fragment);
        arFragment.setOnTapArPlaneListener((hitResult, plane, motionEvent) -> {
            //Active only in Admin Mode
            if(MODE.equalsIgnoreCase("admin")) {
                Log.d("HIT_RESULT:", hitResult.toString());
                anchor = arFragment.getArSceneView().getSession().hostCloudAnchor(hitResult.createAnchor());
                appAnchorState = AppAnchorState.HOSTING;
                showToast("Hosting...");
                createCloudAnchorModel(anchor);
            } else {
                showToast("Anchor can be hosted only in Admin mode");
            }

        });

        arFragment.getArSceneView().getScene().addOnUpdateListener(frameTime -> {

            if (appAnchorState != AppAnchorState.HOSTING)
                return;
            Anchor.CloudAnchorState cloudAnchorState = anchor.getCloudAnchorState();

            if (cloudAnchorState.isError()) {
                showToast(cloudAnchorState.toString());
            } else if (cloudAnchorState == Anchor.CloudAnchorState.SUCCESS) {
                appAnchorState = AppAnchorState.HOSTED;

                String anchorId = anchor.getCloudAnchorId();
                anchorList.add(anchorId);

                if (FROM.equalsIgnoreCase(LauncherActivity.CLOTHING)) {
                    tinydb.putListString(CLOTHING, anchorList);
                }

                showToast("Anchor hosted successfully. Anchor Id: " + anchorId);
            }

        });


        resolve.setOnClickListener(view -> {
            ArrayList<String> stringArrayList = new ArrayList<>();
            if (FROM.equalsIgnoreCase(LauncherActivity.CLOTHING)) {
                stringArrayList = tinydb.getListString(CLOTHING);
            }

            for (int i = 0; i < stringArrayList.size(); i++) {
                String anchorId = stringArrayList.get(i);
                if (anchorId.equals("null")) {
                    Toast.makeText(this, "No anchor Id found", Toast.LENGTH_LONG).show();
                    return;
                }

                Anchor resolvedAnchor = arFragment.getArSceneView().getSession().resolveCloudAnchor(anchorId);
                createCloudAnchorModel(resolvedAnchor);

            }


        });

        if (MODE.equalsIgnoreCase("user")) {
            modelOptionsSpinner.setVisibility(View.GONE);
        } else {
            modelOptionsSpinner.setVisibility(View.VISIBLE);
            resolve.setVisibility(View.VISIBLE);
        }


    }

    private void showToast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

    /** Sets the new value of the current anchor. Detaches the old anchor, if it was non-null. */
    private void setNewAnchor(Anchor newAnchor) {
        synchronized (anchorLock) {
            if (anchor != null) {
                anchor.detach();
            }
            anchor = newAnchor;
        }
    }

    private void createCloudAnchorModel(Anchor anchor) {
        ModelRenderable
                .builder()
                .setSource(this, Uri.parse("model.sfb"))
                .build()
                .thenAccept(modelRenderable -> placeCloudAnchorModel(anchor, modelRenderable));

    }

    private void placeCloudAnchorModel(Anchor anchor, ModelRenderable modelRenderable) {
        anchorNode = new AnchorNode(anchor);
        /*AnchorNode cannot be zoomed in or moved
        So we create a TransformableNode with AnchorNode as the parent*/
        TransformableNode transformableNode = new TransformableNode(arFragment.getTransformationSystem());

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
        arFragment.getArSceneView().getScene().addChild(anchorNode);
    }

    private void onPrivacyAcceptedForHost() {
        if (hostListener != null) {
            return;
        }

        hostListener = new RoomCodeAndCloudAnchorIdListener();
        firebaseManager.getNewRoomCode(hostListener);
    }

    private void getAnchorId(Long roomCode) {
        firebaseManager.registerNewListenerForRoom(
                roomCode,
                cloudAnchorId -> {
                    // When the cloud anchor ID is available from Firebase.
                    CloudAnchorResolveStateListener resolveListener =
                            new CloudAnchorResolveStateListener(roomCode);
                    Preconditions.checkNotNull(resolveListener, "The resolve listener cannot be null.");
                    cloudManager.resolveCloudAnchor(
                            cloudAnchorId, resolveListener, SystemClock.uptimeMillis());
                });
    }


    private final class RoomCodeAndCloudAnchorIdListener
            implements CloudAnchorManager.CloudAnchorHostListener, FirebaseManager.RoomCodeListener {

        private Long roomCode;
        private String cloudAnchorId;

        @Override
        public void onNewRoomCode(Long newRoomCode) {
            Preconditions.checkState(roomCode == null, "The room code cannot have been set before.");
            roomCode = newRoomCode;

            checkAndMaybeShare();
            synchronized (singleTapLock) {
                // Change currentMode to HOSTING after receiving the room code (not when the 'Host' button
                // is tapped), to prevent an anchor being placed before we know the room code and able to
                // share the anchor ID.
                currentMode = AppAnchorState.HOSTING;
            }
        }


        @Override
        public void onError(DatabaseError error) {
            Log.w("A Firebase database error happened.", error.toException());
        }

        @Override
        public void onCloudTaskComplete(Anchor anchor) {
            CloudAnchorState cloudState = anchor.getCloudAnchorState();
            if (cloudState.isError()) {
                return;
            }
            Preconditions.checkState(
                    cloudAnchorId == null, "The cloud anchor ID cannot have been set before.");
            cloudAnchorId = anchor.getCloudAnchorId();
            setNewAnchor(anchor);
            checkAndMaybeShare();
        }

        private void checkAndMaybeShare() {
            if (roomCode == null || cloudAnchorId == null) {
                return;
            }
            firebaseManager.storeAnchorIdInRoom(roomCode, cloudAnchorId);
        }
    }



    private final class CloudAnchorResolveStateListener
            implements CloudAnchorManager.CloudAnchorResolveListener {
        private final long roomCode;

        CloudAnchorResolveStateListener(long roomCode) {
            this.roomCode = roomCode;
        }

        @Override
        public void onCloudTaskComplete(Anchor anchor) {
            CloudAnchorState cloudState = anchor.getCloudAnchorState();
            if (cloudState.isError()) {
                return;
            }
            setNewAnchor(anchor);
        }

        @Override
        public void onShowResolveMessage() {
        }
    }



    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {

    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {

    }
}
