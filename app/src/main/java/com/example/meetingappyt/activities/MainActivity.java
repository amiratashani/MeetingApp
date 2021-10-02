package com.example.meetingappyt.activities;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.meetingappyt.R;
import com.example.meetingappyt.adapters.UsersAdapter;
import com.example.meetingappyt.listeners.UsersListeners;
import com.example.meetingappyt.models.User;
import com.example.meetingappyt.utilities.Constants;
import com.example.meetingappyt.utilities.PreferenceManager;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class MainActivity extends AppCompatActivity implements UsersListeners {

    private TextView textTitle, textSignOut, textErrorMessage;
    //private ProgressBar usersProgressBar;
    private SwipeRefreshLayout swipeRefreshLayout;
    private PreferenceManager preferenceManager;
    private RecyclerView usersRecycleView;
    private ImageView imageConference;

    private List<User> users;
    private UsersAdapter usersAdapter;

    private int REQUEST_CODE_BATTERY_OPTIMIZATION = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        preferenceManager = new PreferenceManager(getApplicationContext());


        textTitle = findViewById(R.id.textTitle);
        textSignOut = findViewById(R.id.textSignOut);
        usersRecycleView = findViewById(R.id.usersRecycleView);
        textErrorMessage = findViewById(R.id.textErrorMessage);
        //usersProgressBar = findViewById(R.id.usersProgressBar);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        imageConference = findViewById(R.id.imageConference);


        textTitle.setText(String.format("%s %s",
                preferenceManager.getString(Constants.KEY_FIRST_NAME),
                preferenceManager.getString(Constants.KEY_LAST_NAME)
        ));

        textSignOut.setOnClickListener(v -> signOut());

        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        sendFCMTokenToDatabase(task.getResult());
                        Log.i("MainActivity_token1", task.getResult());
                    }
                });


//        FirebaseMessaging.getInstance().getToken()
//                .addOnCompleteListener(new OnCompleteListener<String>() {
//                    @Override
//                    public void onComplete(@NonNull Task<String> task) {
//                        if (!task.isSuccessful()) {
//                            return;
//                        } else {
//                            String token = task.getResult();
//                            Log.i("MainActivity_token2", token);
//                        }
//                    }
//                });

        users = new ArrayList<>();
        usersAdapter = new UsersAdapter(users, this);
        usersRecycleView.setAdapter(usersAdapter);

        swipeRefreshLayout.setOnRefreshListener(this::getUsers);

        getUsers();

//        checkForBatteryOptimization();
    }

    private void getUsers() {
        //usersProgressBar.setVisibility(View.VISIBLE);
        swipeRefreshLayout.setRefreshing(true);
        FirebaseFirestore database = FirebaseFirestore.getInstance();
        database.collection(Constants.KEY_COLLECTION_USERS)
                .get()
                .addOnCompleteListener(task -> {
                    //  usersProgressBar.setVisibility(View.GONE);
                    swipeRefreshLayout.setRefreshing(false);
                    String myUserId = preferenceManager.getString(Constants.KEY_USER_ID);
                    if (task.isSuccessful() && task.getResult() != null) {
                        users.clear();
                        for (QueryDocumentSnapshot documentSnapshot : task.getResult()) {
                            if (myUserId.equals(documentSnapshot.getId())) {
                                continue;
                            }
                            User user = new User();
                            user.firstName = documentSnapshot.getString(Constants.KEY_FIRST_NAME);
                            user.lastName = documentSnapshot.getString(Constants.KEY_LAST_NAME);
                            user.email = documentSnapshot.getString(Constants.KEY_EMAIL);
                            user.token = documentSnapshot.getString(Constants.KEY_FCM_TOKEN);
                            users.add(user);
                        }
                        if (users.size() > 0) {
                            usersAdapter.notifyDataSetChanged();
                        } else {
                            textErrorMessage.setText(String.format("%s", "No users available"));
                            textErrorMessage.setVisibility(View.VISIBLE);
                        }
                    } else {
                        textErrorMessage.setText(String.format("%s", "No users available"));
                        textErrorMessage.setVisibility(View.VISIBLE);
                    }
                });
    }

    private void sendFCMTokenToDatabase(String token) {
        FirebaseFirestore database = FirebaseFirestore.getInstance();
        DocumentReference documentReference = database.collection(Constants.KEY_COLLECTION_USERS)
                .document(preferenceManager.getString(Constants.KEY_USER_ID));
        documentReference.update(Constants.KEY_FCM_TOKEN, token)
//                .addOnSuccessListener(aVoid -> Toast.makeText(MainActivity.this, "Token updated successfully", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Toast.makeText(MainActivity.this, "Unable to send token", Toast.LENGTH_SHORT).show());
    }

    private void signOut() {
        Toast.makeText(this, "Signing Out...", Toast.LENGTH_SHORT).show();
        FirebaseFirestore database = FirebaseFirestore.getInstance();
        DocumentReference documentReference = database.collection(Constants.KEY_COLLECTION_USERS)
                .document(preferenceManager.getString(Constants.KEY_USER_ID));

        HashMap<String, Object> updates = new HashMap<>();
        updates.put(Constants.KEY_FCM_TOKEN, FieldValue.delete());
        documentReference.update(updates)
                .addOnSuccessListener(aVoid -> {
                    preferenceManager.clearPreference();
                    startActivity(new Intent(getApplicationContext(), SignInActivity.class));
                })
                .addOnFailureListener(e -> Toast.makeText(MainActivity.this, "Unable to sign out", Toast.LENGTH_SHORT).show());
    }

    @Override
    public void initiateVideoMeeting(User user) {
        if (user.token == null || user.token.trim().isEmpty()) {
            Toast.makeText(this, user.firstName + " " + user.lastName + " is not available for meeting ", Toast.LENGTH_SHORT).show();
        } else {
            //Toast.makeText(this, " Audio meeting with " + user.firstName + " " + user.lastName, Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(getApplicationContext(), OutgoingInvitationActivity.class);
            intent.putExtra("user", user);
            intent.putExtra("type", "video");
            startActivity(intent);
        }
    }

    @Override
    public void initiateAudioMeeting(User user) {
        if (user.token == null || user.token.trim().isEmpty()) {
            Toast.makeText(this, user.firstName + " " + user.lastName + " is not available for meeting ", Toast.LENGTH_SHORT).show();
        } else {
            // Toast.makeText(this, " Video meeting with " + user.firstName + " " + user.lastName, Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(getApplicationContext(), OutgoingInvitationActivity.class);
            intent.putExtra("user", user);
            intent.putExtra("type", "audio");
            startActivity(intent);
        }
    }

    @Override
    public void onMultipleUserAction(Boolean isMultipleUsersSelected) {
        if (isMultipleUsersSelected) {
            imageConference.setVisibility(View.VISIBLE);
            imageConference.setOnClickListener(v -> {
                Intent intent = new Intent(getApplicationContext(), OutgoingInvitationActivity.class);
                intent.putExtra("selectedUsers", new Gson().toJson(usersAdapter.getSelectedUsers()));
                intent.putExtra("type", "video");
                intent.putExtra("isMultiple", true);
                startActivity(intent);
            });
        } else {
            imageConference.setVisibility(View.GONE);
        }
    }


    private void checkForBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
            if (!powerManager.isIgnoringBatteryOptimizations(getPackageName())) {
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setTitle("Warning");
                builder.setMessage("Battery optimization is enabled. It can interrupt running background services.");
                builder.setPositiveButton("Disable", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                    startActivityForResult(intent, REQUEST_CODE_BATTERY_OPTIMIZATION);
                });
                builder.setNegativeButton("Cancel", (dialog, which) -> {
                    dialog.dismiss();
                });
                builder.show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_BATTERY_OPTIMIZATION) {
            checkForBatteryOptimization();
        }
    }
}