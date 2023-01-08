package com.ustglobal.arcloudanchors;

import androidx.appcompat.app.AppCompatActivity;

import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Toast;

import com.google.ar.core.Anchor;
import com.google.ar.core.ArCoreApk;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.ux.TransformableNode;
import com.supplyfy.helpers.helpers.FullScreenHelper;


import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private CustomArFragment arFragment;

    private String FROM, MODE;

    public enum AppAnchorState {
        NONE,
        HOSTING,
        HOSTED
    }

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

        setContentView(R.layout.activity_main);

        TinyDB tinydb = new TinyDB(getApplicationContext());

        FragmentManager fm = getSupportFragmentManager();
        arFragment = (CustomArFragment) getSupportFragmentManager().findFragmentById(R.id.fragment);

        Bundle bundle01 = new Bundle();
        bundle01.putParcelable("customFragment", (Parcelable) arFragment);

        Bundle bundle02 = new Bundle();
        bundle02.putString("mode", MODE);

        Bundle bundle03 = new Bundle();
        bundle03.putParcelable("tinydb", (Parcelable) tinydb);

        Bundle bundle04 = new Bundle();
        bundle04.putString("from", FROM);

        if(arFragment == null) {
            Fragment frag = new CloudAnchorFragment();

            arFragment = (CustomArFragment) frag;

            arFragment.setArguments(bundle01);
            arFragment.setArguments(bundle02);
            arFragment.setArguments(bundle03);
            arFragment.setArguments(bundle04);

            fm.beginTransaction().add(R.id.fragment, arFragment).commit();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        FullScreenHelper.setFullScreenOnWindowFocusChanged(this, hasFocus);
    }
}
