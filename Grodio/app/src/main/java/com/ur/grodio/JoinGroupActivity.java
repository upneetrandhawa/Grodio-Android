

package com.ur.grodio;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;

import com.ur.grodio.databinding.ActivityJoinGroupBinding;




public class JoinGroupActivity extends AppCompatActivity {

    private final String TAG = this.getClass().getSimpleName();

    private ActivityJoinGroupBinding binding;

    private FirebaseFirestore firebaseDB;
    private CollectionReference firebaseDBGroupsRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityJoinGroupBinding.inflate(getLayoutInflater());

        binding.joinButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                joinButtonClicked();
            }
        });

        binding.createGroupbutton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                createGroupButtonClicked();
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
                    joinButtonClicked();
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

    private void joinButtonClicked(){
        Log.d(TAG, "joinButtonClicked()");

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

    private void createGroupButtonClicked(){
        Log.d(TAG, "createGroupButtonClicked()");
        Intent intent = new Intent(this, CreateGroupActivity.class);
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

                    if (task.getResult().exists()) {

                        String groupPin = (String) task.getResult().getData().get("pin");

                        if (groupPin == null) {
                            Log.d(TAG, "checkIfGroupNameExists(): error getting pin");
                            binding.statusTextView.setTextColor(getResources().getColor(R.color.red));
                            binding.statusTextView.setText("error retrieving data, try later");
                            return;
                        }

                        updateResultsOnView(task.getResult().exists(), name, pin, groupPin);
                    }
                    else {
                        updateResultsOnView(task.getResult().exists(), name, pin, "");
                    }
                }
                else {
                    Log.d(TAG, "checkIfGroupNameExists(): error getting data");
                    binding.statusTextView.setTextColor(getResources().getColor(R.color.red));
                    binding.statusTextView.setText("error retrieving data, try later");
                }
            }
        });
    }

    private void updateResultsOnView(boolean groupNameExists, String groupName, String userEnteredGroupPin, String groupPin){
        Log.d(TAG, "updateResultsOnView(): groupNameExists = " + groupNameExists);
        Log.d(TAG, "updateResultsOnView(): groupPin = " + groupPin);
        Log.d(TAG, "updateResultsOnView(): userEnteredGroupPin = " + userEnteredGroupPin);


        if (groupNameExists) {

            if (!userEnteredGroupPin.equals(groupPin)) {
                Log.d(TAG, "updateResultsOnView(): incorrect pin");
                binding.groupPinEditText.setText("");
                binding.statusTextView.setTextColor(getResources().getColor(R.color.red));
                binding.statusTextView.setText("incorrect pin");
                return;
            }

            binding.groupNameEditText.setText("");
            binding.groupPinEditText.setText("");
            binding.statusTextView.setTextColor(getResources().getColor(R.color.green));
            binding.statusTextView.setText("joining group " + groupName);

            //pass info to bundle
            Bundle bundle  = new Bundle();
            bundle.putBoolean("isCreatingGroup",false);
            bundle.putString("validGroupName",groupName);
            bundle.putString("validGroupPin",groupPin);

            //start Activity
            Intent intent = new Intent(this, PlayerActivity.class);
            intent.putExtra("bundle",bundle);
            startActivity(intent);
            overridePendingTransition( R.anim.slide_in_up, R.anim.slide_out_up );
        }
        else {
            binding.groupNameEditText.setText("");
            binding.statusTextView.setTextColor(getResources().getColor(R.color.red));
            binding.statusTextView.setText("group " + groupName + " doesnt exists");
        }
    }

    private void hideKeyboard(View view){
        Log.d(TAG, "hideKeyboard()");
        InputMethodManager inputMethodManager =(InputMethodManager)getSystemService(Activity.INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

}
