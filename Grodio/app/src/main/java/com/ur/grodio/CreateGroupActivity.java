package com.ur.grodio;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import androidx.navigation.ui.AppBarConfiguration;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import com.ur.grodio.databinding.ActivityCreateGroupBinding;

import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.TextView;


public class CreateGroupActivity extends AppCompatActivity {

    private final String TAG = this.getClass().getSimpleName();

    private ActivityCreateGroupBinding binding;

    private FirebaseFirestore firebaseDB;
    private CollectionReference firebaseDBGroupsRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityCreateGroupBinding.inflate(getLayoutInflater());

        binding.createbutton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                createButtonClicked();
            }
        });

        binding.joinGroupbutton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                joinGroupButtonClicked();
            }
        });

        binding.groupNameEditText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus && !binding.groupPinEditText.isFocused()) {
                    hideKeyboard(v);
                }
            }
        });

        binding.groupPinEditText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus && !binding.groupNameEditText.isFocused()) {
                    hideKeyboard(v);
                }
            }
        });

        binding.groupPinEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if(actionId== EditorInfo.IME_ACTION_DONE){
                    Log.d(TAG, "onCreate(): onEditorAction(): IME_ACTION_DONE");
                    createButtonClicked();
                }
                return false;
            }
        });

        initFirebase();

        setContentView(binding.getRoot());
    }

    private void initFirebase(){
        Log.d(TAG, "initFirebase()");
        firebaseDB = FirebaseFirestore.getInstance();
        firebaseDBGroupsRef = firebaseDB.collection("groups");

    }

    private void createButtonClicked(){
        Log.d(TAG, "createButtonClicked()");

        String enteredGroupName = binding.groupNameEditText.getText().toString().trim();
        Log.d(TAG, "joinButtonClicked(): enteredGroupName = " + enteredGroupName);

        if (enteredGroupName == null || enteredGroupName.length() < 4) {
            Log.d(TAG, "joinButtonClicked(): not enough name chars");
            binding.groupNameEditText.setText("");
            binding.statusTextView.setTextColor(getResources().getColor(R.color.red));
            binding.statusTextView.setText("name must be atleast 4 chars");
            return;
        }

        String enteredGroupPin = binding.groupPinEditText.getText().toString().trim();
        Log.d(TAG, "joinButtonClicked(): enteredGroupPin = " + enteredGroupPin);

        if (enteredGroupPin == null || enteredGroupPin.length() == 0) {
            Log.d(TAG, "joinButtonClicked(): empty pin");
            binding.groupPinEditText.setText("");
            binding.statusTextView.setTextColor(getResources().getColor(R.color.red));
            binding.statusTextView.setText("pin required");
            return;
        }

        if (enteredGroupPin == null || enteredGroupPin.length() != 4) {
            Log.d(TAG, "joinButtonClicked(): not enough pin digits");
            binding.groupPinEditText.setText("");
            binding.statusTextView.setTextColor(getResources().getColor(R.color.red));
            binding.statusTextView.setText("pin must be 4 digits");
            return;
        }

        checkIfGroupNameExists(enteredGroupName, enteredGroupPin);
    }

    private void joinGroupButtonClicked(){
        Log.d(TAG, "joinGroupButtonClicked()");

        Intent intent = new Intent(this, JoinGroupActivity.class);
        startActivity(intent);

    }

    private void checkIfGroupNameExists(String name, String pin){
        Log.d(TAG, "checkIfGroupNameExists(): name = " + name);

        firebaseDBGroupsRef.document(name).get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
            @Override
            public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                if (task.isSuccessful()){
                    Log.d(TAG, "checkIfGroupNameExists(): success getting data");
                    Log.d(TAG, "checkIfGroupNameExists(): " + name + " exists = " + task.getResult().exists());

                    updateResultsOnView(task.getResult().exists(), name, pin);
                }
                else {
                    Log.d(TAG, "checkIfGroupNameExists(): error getting data");
                    binding.statusTextView.setTextColor(getResources().getColor(R.color.red));
                    binding.statusTextView.setText("error retrieving data, try later");
                }
            }
        });
    }

    private void updateResultsOnView(boolean groupNameExists, String groupName, String userEnteredGroupPin){
        Log.d(TAG, "updateResultsOnView(): groupNameExists = " + groupNameExists);
        Log.d(TAG, "updateResultsOnView(): userEnteredGroupPin = " + userEnteredGroupPin);


        if (groupNameExists) {
            binding.groupNameEditText.setText("");
            binding.statusTextView.setTextColor(getResources().getColor(R.color.red));
            binding.statusTextView.setText("group " + groupName + " already exists");
        }
        else {
            binding.groupNameEditText.setText("");
            binding.groupPinEditText.setText("");
            binding.statusTextView.setTextColor(getResources().getColor(R.color.green));
            binding.statusTextView.setText("creating group " + groupName);

            //pass info to bundle
            Bundle bundle  = new Bundle();
            bundle.putBoolean("isCreatingGroup",true);
            bundle.putString("validGroupName",groupName);
            bundle.putString("validGroupPin",userEnteredGroupPin);
            //start Activity
            Intent intent = new Intent(this, PlayerActivity.class);
            intent.putExtra("bundle",bundle);
            startActivity(intent);
            overridePendingTransition( R.anim.slide_in_up, R.anim.slide_out_up );


        }
    }

    private void hideKeyboard(View view){
        Log.d(TAG, "hideKeyboard()");
        InputMethodManager inputMethodManager =(InputMethodManager)getSystemService(Activity.INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

}